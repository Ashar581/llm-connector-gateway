#!/usr/bin/env bash
# ============================================================
# Ashar n8n Edition - Custom Monorepo Runner
#
# Builds and runs the custom n8n monorepo (with Agent & Scraper Agent nodes).
#
#   <runtime>/
#     node/              private Node.js 24 + pnpm
#     n8n-repo/          the cloned monorepo (when not run from inside it)
#     n8n/
#       data/            n8n user folder (SQLite DB, config, credentials)
#       logs/            n8n.log, install.log, build.log
#       n8n.pid
#       encryption.key   generated once, reused forever
#
# Runtime dir resolution:
#   $RUNTIME_DIR (set by bootstrap)  ->  else <script-dir>/../runtime
#
# Usage:
#   ./n8n-custom.sh [start|stop|restart|status|logs|build|install]
#
# Env overrides:
#   N8N_PORT=5678
#   N8N_REPO_URL=https://github.com/Ashar581/llm-connector-n8n.git
#   N8N_REPO_ROOT=...            override where the repo is located
#   N8N_START_TIMEOUT=120        seconds to wait for readiness
#   N8N_HOME=...                 override runtime location (default: <runtime>/n8n)
#   N8N_SKIP_BUILD=1             skip pnpm build (use existing dist)
#   N8N_SKIP_INSTALL=1           skip pnpm install
#   N8N_DATA_DIR=...             override n8n user data folder
#   N8N_LOG_LEVEL=info           n8n log level (debug, info, warn, error)
#   N8N_PNPM_VERSION=...         force a pnpm version (default: repo's packageManager)
#   GENERIC_TIMEZONE=UTC         default timezone
#   N8N_FRAME_ANCESTORS="*"      who may embed n8n in an <iframe> (space separated CSP
#                                sources, e.g. "'self' https://app.example.com http://localhost:*")
#   N8N_BASE_PATH=/n8n           serve n8n under a sub-path (for a reverse proxy at /n8n)
#   N8N_PUBLIC_URL=https://h/n8n public URL as the browser sees it (sets editor/webhook URL)
#   N8N_LISTEN_ADDRESS=0.0.0.0   bind address (default 127.0.0.1)
#   N8N_ALLOW_IFRAME=0           don't strip X-Frame-Options from the n8n server
#
# Needs: curl, git, tar (everything else is installed privately).
# Platforms: Ubuntu/Debian (vast.ai containers, x64/arm64), macOS (Intel + Apple
#            Silicon; works with the stock bash 3.2), Windows Git Bash/MSYS2.
# NOTE: no `set -e` at file level, so this file is safe to `source` from bootstrap.
# ============================================================

# ------------------------------------------------------------
# Helpers
# ------------------------------------------------------------

_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"

_n8n_log()     { printf '[n8n] %s\n' "$*"; }
_n8n_info()    { printf '[n8n] %s\n' "$*"; }
_n8n_success() { printf '[n8n] OK: %s\n' "$*"; }
_n8n_warning() { printf '[n8n] WARN: %s\n' "$*" >&2; }
_n8n_fail()    { printf '[n8n] ERROR: %s\n' "$*" >&2; return 1; }

_is_windows() {
    case "$(uname -s 2>/dev/null || echo "")" in
        MSYS*|MINGW*|CYGWIN*|msys*|mingw*|cygwin*) return 0 ;;
        *) return 1 ;;
    esac
}

# Git Bash / MSYS2 / Cygwin all use ':' inside bash, so ':' everywhere.
if _is_windows; then _NPM_CMD="npm.cmd"; else _NPM_CMD="npm"; fi

# ------------------------------------------------------------
# Locations
# ------------------------------------------------------------

_looks_like_n8n_repo() {
    [[ -f "$1/package.json" && -d "$1/packages/cli" ]]
}

# Runtime base (holds node/, n8n/ and, by default, n8n-repo/).
# It must NEVER be the repo checkout itself or the script directory, because a
# stale-checkout cleanup would otherwise take the runtime (or the script) with it.
if [[ -n "${RUNTIME_DIR:-}" ]]; then
    _RUNTIME_BASE="${RUNTIME_DIR}"
elif _looks_like_n8n_repo "${_SCRIPT_DIR}"; then
    _RUNTIME_BASE="${_SCRIPT_DIR}/runtime"
else
    _RUNTIME_BASE="$(cd "${_SCRIPT_DIR}/.." && pwd)/runtime"
fi

if [[ -n "${N8N_REPO_ROOT:-}" ]]; then
    REPO_ROOT="${N8N_REPO_ROOT}"
elif _looks_like_n8n_repo "${_SCRIPT_DIR}"; then
    REPO_ROOT="${_SCRIPT_DIR}"
else
    REPO_ROOT="${_RUNTIME_BASE}/n8n-repo"
fi

_NODE_DIR="${_RUNTIME_BASE}/node"
_NODE_VERSION="24.11.0"
_PNPM_FALLBACK="10"   # used only if the repo has no packageManager field

N8N_HOME="${N8N_HOME:-${_RUNTIME_BASE}/n8n}"
N8N_LOG_DIR="${N8N_HOME}/logs"
N8N_LOG_FILE="${N8N_LOG_DIR}/n8n.log"
N8N_PORT="${N8N_PORT:-5678}"
N8N_START_TIMEOUT="${N8N_START_TIMEOUT:-120}"
N8N_DATA_DIR="${N8N_DATA_DIR:-${N8N_HOME}/data}"
N8N_PID_FILE="${N8N_HOME}/n8n.pid"
N8N_KEY_FILE="${N8N_HOME}/encryption.key"
N8N_BIN="${REPO_ROOT}/packages/cli/bin/n8n"

# ------------------------------------------------------------
# Prereqs (auto-install via apt when running as root on Debian/Ubuntu)
# ------------------------------------------------------------

_n8n_need_cmd() {
    local cmd="$1" pkg="${2:-$1}"
    command -v "$cmd" >/dev/null 2>&1 && return 0
    if [[ "$(id -u)" == "0" ]] && command -v apt-get >/dev/null 2>&1; then
        _n8n_warning "'${cmd}' not found. Installing '${pkg}' via apt-get..."
        DEBIAN_FRONTEND=noninteractive apt-get install -y "$pkg" >/dev/null 2>&1 \
            || { apt-get update >/dev/null 2>&1 \
                 && DEBIAN_FRONTEND=noninteractive apt-get install -y "$pkg" >/dev/null 2>&1; } \
            || true
        command -v "$cmd" >/dev/null 2>&1 && return 0
    fi
    _n8n_fail "'${cmd}' is required but not installed."
}

# Native-module toolchain (python3 + make + g++). Best-effort, never fatal.
_n8n_ensure_toolchain() {
    _is_windows && return 0
    case "$(uname -s)" in
        Darwin)
            xcode-select -p >/dev/null 2>&1 \
                || _n8n_warning "Xcode Command Line Tools missing. Run: xcode-select --install"
            ;;
        Linux)
            _n8n_need_cmd python3 python3     >/dev/null 2>&1 || _n8n_warning "python3 missing; native modules may fail to build."
            _n8n_need_cmd make build-essential >/dev/null 2>&1 || _n8n_warning "make missing; native modules may fail to build."
            _n8n_need_cmd g++ build-essential  >/dev/null 2>&1 || _n8n_warning "g++ missing; native modules may fail to build."
            ;;
    esac
    return 0
}

_check_prereqs() {
    if [[ "$(uname -s)" == "Darwin" ]] && ! command -v git >/dev/null 2>&1; then
        _n8n_fail "git not found. Run: xcode-select --install   (or: brew install git)"
        return 1
    fi
    _n8n_need_cmd ca-certificates >/dev/null 2>&1 || true
    _n8n_need_cmd curl || return 1
    _n8n_need_cmd git  || return 1
    _n8n_need_cmd tar  || return 1
    _n8n_ensure_toolchain
    return 0
}

# ------------------------------------------------------------
# Clone
# ------------------------------------------------------------

_n8n_ensure_repo() {
    if [[ -f "${REPO_ROOT}/package.json" && -d "${REPO_ROOT}/.git" ]]; then
        return 0
    fi

    # Never rm -rf: move a broken/non-empty directory aside instead.
    if [[ -e "${REPO_ROOT}" ]] && [[ -n "$(ls -A "${REPO_ROOT}" 2>/dev/null || true)" ]]; then
        local aside="${REPO_ROOT}.stale-$(date +%s)"
        _n8n_warning "${REPO_ROOT} is not a valid n8n checkout. Moving it to ${aside}"
        mv "${REPO_ROOT}" "${aside}" || { _n8n_fail "Could not move ${REPO_ROOT} aside."; return 1; }
    fi

    _n8n_log "Cloning n8n repo into ${REPO_ROOT}..."
    mkdir -p "$(dirname "${REPO_ROOT}")"
    git clone --depth 1 "${N8N_REPO_URL:-https://github.com/Ashar581/llm-connector-n8n.git}" "${REPO_ROOT}" \
        || { _n8n_fail "Failed to clone n8n repo"; return 1; }
    _n8n_success "Cloned n8n repo to ${REPO_ROOT}"
}

# ------------------------------------------------------------
# Private Node.js + pnpm
# ------------------------------------------------------------

_n8n_node_bin() {
    if   [[ -x "${_NODE_DIR}/bin/node"  ]]; then echo "${_NODE_DIR}/bin/node"
    elif [[ -x "${_NODE_DIR}/node.exe"  ]]; then echo "${_NODE_DIR}/node.exe"
    else echo ""
    fi
}

_n8n_node_archive_url() {
    local os arch ext
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    arch="$(uname -m)"
    case "$arch" in
        x86_64|amd64|AMD64) arch="x64" ;;
        aarch64|arm64)      arch="arm64" ;;
        armv7l|armv7)       arch="armv7l" ;;
        i386|i686)          arch="x86" ;;
    esac

    if _is_windows; then
        os="win"; ext="zip"
    else
        case "$os" in
            darwin|linux) ext="tar.gz" ;;
            *) _n8n_fail "Unsupported OS: $os"; return 1 ;;
        esac
    fi

    _NODE_URL="https://nodejs.org/dist/v${_NODE_VERSION}/node-v${_NODE_VERSION}-${os}-${arch}.${ext}"
    _NODE_ARCHIVE="${TMPDIR:-/tmp}/node-v${_NODE_VERSION}-${os}-${arch}.${ext}"
}

_n8n_extract_node() {
    _n8n_log "Extracting Node.js to ${_NODE_DIR}..."
    rm -rf "${_NODE_DIR}"
    mkdir -p "${_NODE_DIR}"
    if _is_windows; then
        if command -v unzip >/dev/null 2>&1; then
            unzip -q -o "${_NODE_ARCHIVE}" -d "${_NODE_DIR}" \
                || { _n8n_fail "Failed to extract Node.js"; return 1; }
        else
            powershell -Command "Expand-Archive -Path '${_NODE_ARCHIVE}' -DestinationPath '${_NODE_DIR}' -Force" \
                || { _n8n_fail "Failed to extract Node.js"; return 1; }
        fi
        # zip has a top-level node-vX-win-x64/ dir; move its contents up
        local top
        top="$(ls -A "${_NODE_DIR}" 2>/dev/null | head -n1 || true)"
        if [[ -n "$top" && -d "${_NODE_DIR}/${top}" ]]; then
            mv "${_NODE_DIR}/${top}"/* "${_NODE_DIR}/" 2>/dev/null || true
            rm -rf "${_NODE_DIR:?}/${top}"
        fi
    else
        tar -xzf "${_NODE_ARCHIVE}" -C "${_NODE_DIR}" --strip-components=1 \
            || { _n8n_fail "Failed to extract Node.js"; return 1; }
    fi
    rm -f "${_NODE_ARCHIVE}"
}

_n8n_install_private_node() {
    _n8n_node_archive_url || return 1
    _n8n_log "Downloading Node.js ${_NODE_VERSION} (${_NODE_URL##*/})..."
    curl -fSL --retry 3 --retry-delay 2 "$_NODE_URL" -o "${_NODE_ARCHIVE}" \
        || { rm -f "${_NODE_ARCHIVE}"; _n8n_fail "Failed to download ${_NODE_URL}"; return 1; }
    _n8n_extract_node || return 1
    _n8n_success "Private Node.js ${_NODE_VERSION} installed at ${_NODE_DIR}"
}

# pnpm version: explicit override > repo's packageManager field > fallback
_n8n_pnpm_version() {
    if [[ -n "${N8N_PNPM_VERSION:-}" ]]; then echo "${N8N_PNPM_VERSION}"; return; fi
    local v=""
    if [[ -f "${REPO_ROOT}/package.json" ]]; then
        v="$(sed -n 's/.*"packageManager"[[:space:]]*:[[:space:]]*"pnpm@\([^"+]*\).*/\1/p' \
             "${REPO_ROOT}/package.json" | head -n1 || true)"
    fi
    echo "${v:-${_PNPM_FALLBACK}}"
}

_n8n_ensure_pnpm() {
    local npm_bin="${_NODE_DIR}/bin/npm"
    [[ -x "$npm_bin" ]] || npm_bin="${_NODE_DIR}/${_NPM_CMD}"

    if [[ -e "${_NODE_DIR}/bin/pnpm" || -e "${_NODE_DIR}/pnpm.cmd" ]] \
       && pnpm --version >/dev/null 2>&1; then
        _n8n_info "Using pnpm $(pnpm --version)"
        return 0
    fi

    local want; want="$(_n8n_pnpm_version)"
    _n8n_log "Installing pnpm@${want} with the private Node's npm..."
    "$npm_bin" install -g "pnpm@${want}" >/dev/null \
        || { _n8n_fail "Failed to install pnpm@${want}."; return 1; }
    hash -r 2>/dev/null || true
    pnpm --version >/dev/null 2>&1 \
        || { _n8n_fail "pnpm still not working after install. PATH=$PATH"; return 1; }
    _n8n_info "Using pnpm $(pnpm --version)"
}

_n8n_ensure_node() {
    local nb major=""
    nb="$(_n8n_node_bin)"
    [[ -n "$nb" ]] && major="$("$nb" -v 2>/dev/null | sed 's/^v//; s/\..*//' || true)"

    if [[ -z "$major" || ! "$major" =~ ^[0-9]+$ || "$major" -lt 24 ]]; then
        _n8n_install_private_node || return 1
    fi

    export PATH="${_NODE_DIR}/bin:${REPO_ROOT}/node_modules/.bin:${PATH}"
    _is_windows && export PATH="${_NODE_DIR}:${PATH}"
    _n8n_ensure_pnpm
}

# One-time setup: prereqs -> repo -> node -> pnpm. Idempotent.
_N8N_INIT_DONE=0
_n8n_init() {
    [[ "$_N8N_INIT_DONE" == "1" ]] && return 0
    _check_prereqs    || return 1
    _n8n_ensure_repo  || return 1
    _n8n_ensure_node  || return 1
    _N8N_INIT_DONE=1
}

# ------------------------------------------------------------
# Small helpers
# ------------------------------------------------------------

_port_in_use() {
    if _is_windows; then
        if command -v netstat >/dev/null 2>&1; then
            netstat -ano 2>/dev/null | grep -q ":$1 " && return 0
            return 1
        fi
        curl -fsS -o /dev/null "http://127.0.0.1:$1/healthz" 2>/dev/null
    else
        (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1
    fi
}

_pid_alive() {
    [[ -f "$N8N_PID_FILE" ]] || return 1
    local pid
    pid="$(cat "$N8N_PID_FILE" 2>/dev/null || true)"
    [[ -n "$pid" ]] || return 1
    if _is_windows; then
        tasklist 2>/dev/null | awk '{print $2}' | grep -qx "$pid"
    else
        kill -0 "$pid" 2>/dev/null
    fi
}

# ------------------------------------------------------------
# Build steps
# ------------------------------------------------------------

do_install() {
    if [[ "${N8N_SKIP_INSTALL:-0}" == "1" ]]; then
        _n8n_log "Skipping pnpm install (N8N_SKIP_INSTALL=1)."
        return 0
    fi
    _n8n_log "Installing dependencies with pnpm (log: ${N8N_LOG_DIR}/install.log)..."
    cd "$REPO_ROOT" || return 1
    mkdir -p "$N8N_LOG_DIR"
    # CI=true stops pnpm from prompting (it aborts without a TTY otherwise).
    if CI=true pnpm install --frozen-lockfile > "${N8N_LOG_DIR}/install.log" 2>&1; then
        _n8n_success "Dependencies installed."
    elif CI=true pnpm install > "${N8N_LOG_DIR}/install.log" 2>&1; then
        _n8n_success "Dependencies installed (lockfile was out of sync)."
    else
        _n8n_fail "pnpm install failed. See ${N8N_LOG_DIR}/install.log"
        tail -n 20 "${N8N_LOG_DIR}/install.log" >&2 || true
        return 1
    fi
}

# Print the real reason a turbo build failed (the summary alone hides it).
_n8n_show_build_error() {
    local log="${N8N_LOG_DIR}/build.log" failed pkg
    failed="$(sed -n 's/^[[:space:]]*Failed:[[:space:]]*//p' "$log" 2>/dev/null | head -n1 || true)"
    _n8n_warning "---- build error (full log: ${log}) ----"
    if [[ -n "$failed" ]]; then
        pkg="${failed%%#*}"
        _n8n_warning "Failed task: ${failed}"
        grep -F "${pkg}:" "$log" 2>/dev/null | tail -n 40 >&2 || true
    else
        tail -n 40 "$log" >&2 || true
    fi
    _n8n_warning "-----------------------------------------"
}

_n8n_is_built() {
    [[ -f "$N8N_BIN" && -d "${REPO_ROOT}/node_modules" \
       && -d "${REPO_ROOT}/packages/cli/dist/config" ]]
}

# Some packages ship a lezer grammar and expect the parser to be generated before
# `cm-buildhelper` runs (root cause of: "Could not resolve './parser'"). That step
# is skipped in some installs, so generate any missing parser here. Idempotent.
_n8n_generate_lezer_parsers() {
    local pkg="${REPO_ROOT}/packages/@n8n/codemirror-lang-html"
    [[ -d "$pkg" ]] || return 0
    [[ -f "$pkg/src/grammar/parser.js" || -f "$pkg/src/grammar/parser.ts" ]] && return 0

    _n8n_log "Generating missing lezer parser for @n8n/codemirror-lang-html..."
    ( cd "$pkg" || exit 1

      # 1) the package's own generate/prepare script, if it has one
      local s
      for s in generate prepare; do
          if grep -Eq "\"${s}\"[[:space:]]*:" package.json 2>/dev/null; then
              pnpm run "$s" >>"${N8N_LOG_DIR}/build.log" 2>&1 || true
              [[ -f src/grammar/parser.js ]] && exit 0
          fi
      done

      # 2) run lezer-generator directly on the grammar file
      local grammar gen
      grammar="$(find src -name '*.grammar' 2>/dev/null | head -n1 || true)"
      [[ -n "$grammar" ]] || { echo "no .grammar file found under $pkg/src" >&2; exit 1; }
      gen="$(find "${REPO_ROOT}/node_modules/.pnpm" -path '*@lezer/generator/dist/lezer-generator.cjs' 2>/dev/null | head -n1 || true)"
      if [[ -n "$gen" ]]; then
          node "$gen" "$grammar" -o "$(dirname "$grammar")/parser"
      else
          pnpm exec lezer-generator "$grammar" -o "$(dirname "$grammar")/parser"
      fi
    ) || { _n8n_warning "Could not generate the lezer parser automatically."; return 1; }
    _n8n_success "Generated lezer parser."
}

do_build() {
    if [[ "${N8N_SKIP_BUILD:-0}" == "1" ]]; then
        _n8n_log "Skipping build (N8N_SKIP_BUILD=1)."
        return 0
    fi
    _n8n_log "Building monorepo - this can take a long time (log: ${N8N_LOG_DIR}/build.log)..."
    cd "$REPO_ROOT" || return 1
    mkdir -p "$N8N_LOG_DIR"
    export NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=8192}"
    export CI=true

    _n8n_generate_lezer_parsers || true

    if pnpm build > "${N8N_LOG_DIR}/build.log" 2>&1; then
        _n8n_success "Build complete."
        return 0
    fi
    _n8n_show_build_error

    # Retry serially: parallel builds hit races / OOM in small containers.
    _n8n_warning "Retrying build with --concurrency=1 ..."
    if pnpm build --concurrency=1 > "${N8N_LOG_DIR}/build.log" 2>&1; then
        _n8n_success "Build complete (serial)."
        return 0
    fi
    _n8n_show_build_error
    return 1
}

ensure_built() {
    if _n8n_is_built; then
        _n8n_log "Using existing build at ${N8N_BIN}"
        return 0
    fi
    _n8n_log "Built artifacts not found. Running install + build..."
    do_install || return 1
    do_build   || true
    if ! _n8n_is_built; then
        _n8n_fail "Build did not produce packages/cli/dist. Fix the error above (see ${N8N_LOG_DIR}/build.log) and re-run."
        return 1
    fi
}

# ------------------------------------------------------------
# Encryption key (protects stored credentials; must never change)
# ------------------------------------------------------------

_ensure_key() {
    if [[ ! -s "$N8N_KEY_FILE" ]]; then
        mkdir -p "$N8N_HOME"
        ( umask 077
          head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' > "$N8N_KEY_FILE" )
        _n8n_log "Generated n8n encryption key."
    fi
}

# ------------------------------------------------------------
# Start / stop / status / logs
# ------------------------------------------------------------

# Sub-path support, e.g. N8N_BASE_PATH=/n8n when a reverse proxy serves n8n at /n8n
_n8n_base_path() {
    local bp="${N8N_BASE_PATH:-}"
    bp="${bp#"${bp%%[!/]*}"}"      # strip leading slashes
    bp="${bp%"${bp##*[!/]}"}"      # strip trailing slashes
    [[ -n "$bp" ]] && echo "/${bp}" || echo ""
}

_n8n_http_ok() {
    local bp; bp="$(_n8n_base_path)"
    curl -fsS -o /dev/null "http://127.0.0.1:${N8N_PORT}${bp}/healthz" 2>/dev/null \
        || { [[ -n "$bp" ]] && curl -fsS -o /dev/null "http://127.0.0.1:${N8N_PORT}/healthz" 2>/dev/null; }
}

# n8n sends "X-Frame-Options: sameorigin" via helmet in production mode, which makes
# browsers show "localhost refused to connect" inside an <iframe>. Turn that header
# off in the compiled server (idempotent; re-applied after every rebuild).
# Skip with N8N_ALLOW_IFRAME=0.
_n8n_allow_iframe() {
    [[ "${N8N_ALLOW_IFRAME:-1}" == "0" ]] && return 0
    local f="${REPO_ROOT}/packages/cli/dist/server.js"
    [[ -f "$f" ]] || return 0
    if grep -Eq "action:[[:space:]]*['\"]sameorigin['\"]" "$f" 2>/dev/null; then
        if command -v perl >/dev/null 2>&1; then
            perl -pi -e 's/\{\s*action:\s*([\x27"])sameorigin\1\s*\}/false/g' "$f" \
                && _n8n_log "Disabled X-Frame-Options in n8n server (iframe embedding enabled)."
        else
            _n8n_warning "perl not found; cannot patch X-Frame-Options (CSP frame-ancestors still applies)."
        fi
    fi
}

_n8n_check_embedding() {
    [[ "${N8N_ALLOW_IFRAME:-1}" == "0" ]] && return 0
    local h
    h="$(curl -s -D - -o /dev/null "http://127.0.0.1:${N8N_PORT}$(_n8n_base_path)/" 2>/dev/null | tr -d '\r' || true)"
    if grep -qi '^x-frame-options:' <<< "$h"; then
        _n8n_warning "n8n still sends X-Frame-Options; iframe embedding may be blocked: $(grep -i '^x-frame-options:' <<< "$h" | head -n1)"
    fi
    if grep -qi 'frame-ancestors' <<< "$h"; then
        _n8n_success "iframe embedding allowed (CSP frame-ancestors set)."
    else
        _n8n_info "No CSP frame-ancestors header (fine: X-Frame-Options is off, so n8n allows framing)."
    fi
    _n8n_info "iframe reminder: the BROWSER must reach n8n. 'localhost' in an iframe means the viewer's own machine."
    _n8n_info "On a remote host use a same-origin proxy: N8N_BASE_PATH=/n8n and iframe src=/n8n/"
}

start_n8n() {
    if _pid_alive; then
        _n8n_log "n8n already running (pid $(cat "$N8N_PID_FILE"))."
        return 0
    fi

    if _port_in_use "$N8N_PORT"; then
        _n8n_fail "Port ${N8N_PORT} is already in use. Set N8N_PORT to change it."
        return 1
    fi

    _n8n_init || return 1
    _ensure_key
    mkdir -p "$N8N_DATA_DIR" "$N8N_LOG_DIR"

    ensure_built || return 1

    local node_bin node_major
    node_bin="$(_n8n_node_bin)"
    node_major="$("$node_bin" -v 2>/dev/null | sed 's/^v//; s/\..*//' || true)"
    if [[ -z "$node_bin" || ! "$node_major" =~ ^[0-9]+$ || "$node_major" -lt 24 ]]; then
        _n8n_fail "Private Node ${_NODE_VERSION} missing or too old (found: ${node_major:-none})."
        return 1
    fi

    if [[ ! -f "$N8N_BIN" ]]; then
        _n8n_fail "n8n entry point not found: ${N8N_BIN}"
        return 1
    fi

    _n8n_allow_iframe

    _n8n_log "Starting n8n on http://127.0.0.1:${N8N_PORT}"

    (
        export N8N_USER_FOLDER="$N8N_DATA_DIR"
        N8N_ENCRYPTION_KEY="$(cat "$N8N_KEY_FILE")"
        export N8N_ENCRYPTION_KEY

        # 127.0.0.1 = reachable only from this machine. On a remote host (vast.ai etc.) the
        # browser is NOT on this machine: put a same-origin reverse proxy in front
        # (N8N_BASE_PATH=/n8n) or set N8N_LISTEN_ADDRESS=0.0.0.0 and expose the port.
        export N8N_LISTEN_ADDRESS="${N8N_LISTEN_ADDRESS:-127.0.0.1}"
        export N8N_PORT="$N8N_PORT"
        export N8N_PROTOCOL="http"
        export N8N_HOST="127.0.0.1"
        local _bp; _bp="$(_n8n_base_path)"
        if [[ -n "$_bp" ]]; then
            export N8N_PATH="${_bp}/"
            # Behind a plain HTTP proxy (e.g. Spring Boot) use SSE for live updates instead of
            # websockets, so the proxy needs no websocket support. Override with N8N_PUSH_BACKEND.
            export N8N_PUSH_BACKEND="${N8N_PUSH_BACKEND:-sse}"
        fi
        if [[ -n "${N8N_PUBLIC_URL:-}" ]]; then
            # public URL as the browser sees it, e.g. https://my-host/n8n
            export N8N_EDITOR_BASE_URL="${N8N_EDITOR_BASE_URL:-${N8N_PUBLIC_URL%/}}"
            export WEBHOOK_URL="${WEBHOOK_URL:-${N8N_PUBLIC_URL%/}/}"
            export N8N_PROXY_HOPS="${N8N_PROXY_HOPS:-1}"
        else
            export N8N_EDITOR_BASE_URL="${N8N_EDITOR_BASE_URL:-http://127.0.0.1:${N8N_PORT}${_bp}}"
            export WEBHOOK_URL="${WEBHOOK_URL:-http://127.0.0.1:${N8N_PORT}${_bp}/}"
        fi
        export N8N_SECURE_COOKIE="${N8N_SECURE_COOKIE:-false}"

        export N8N_DIAGNOSTICS_ENABLED="false"
        export N8N_VERSION_NOTIFICATIONS_ENABLED="false"
        export N8N_PERSONALIZATION_ENABLED="false"
        export N8N_HIRING_BANNER_ENABLED="false"
        export N8N_TEMPLATES_ENABLED="${N8N_TEMPLATES_ENABLED:-true}"

        export N8N_PUBLIC_API_DISABLED="false"
        export N8N_RUNNERS_ENABLED="true"
        # iframe embedding: n8n reads this as JSON (helmet directives). The old plain
        # string was not valid JSON, so n8n ignored it and X-Frame-Options won.
        if [[ -z "${N8N_CONTENT_SECURITY_POLICY:-}" ]]; then
            local _fa_list _fa_json="" _fa_src
            local -a _fa_arr
            _fa_list="${N8N_FRAME_ANCESTORS:-*}"
            read -r -a _fa_arr <<< "$_fa_list"      # read -a: no glob expansion of '*'
            for _fa_src in "${_fa_arr[@]}"; do
                _fa_json="${_fa_json:+${_fa_json},}\"${_fa_src}\""
            done
            export N8N_CONTENT_SECURITY_POLICY="{\"frame-ancestors\":[${_fa_json}]}"
        fi

        export N8N_RELEASE_TYPE="stable"
        export GENERIC_TIMEZONE="${GENERIC_TIMEZONE:-UTC}"

        export EXECUTIONS_DATA_PRUNE="true"
        export EXECUTIONS_DATA_MAX_AGE="${EXECUTIONS_DATA_MAX_AGE:-168}"
        export N8N_LOG_LEVEL="${N8N_LOG_LEVEL:-info}"

        cd "$REPO_ROOT"
        # Always launch with the private Node 24 (system Node 22 breaks this n8n).
        if command -v nohup >/dev/null 2>&1; then
            nohup "$node_bin" "$N8N_BIN" start >>"$N8N_LOG_FILE" 2>&1 < /dev/null &
        else
            "$node_bin" "$N8N_BIN" start >>"$N8N_LOG_FILE" 2>&1 < /dev/null &
        fi
        echo $! > "$N8N_PID_FILE"
    )

    local i
    for (( i = 0; i < N8N_START_TIMEOUT; i++ )); do
        if ! _pid_alive; then
            _n8n_fail "n8n exited during startup. Last log lines:"
            tail -n 20 "$N8N_LOG_FILE" >&2 || true
            return 1
        fi
        if _n8n_http_ok; then
            _n8n_success "n8n is up: http://127.0.0.1:${N8N_PORT}"
            _n8n_check_embedding
            return 0
        fi
        sleep 1
    done

    _n8n_fail "n8n did not become ready within ${N8N_START_TIMEOUT}s. See ${N8N_LOG_FILE}"
    return 1
}

stop_n8n() {
    if ! _pid_alive; then
        _n8n_log "n8n is not running."
        rm -f "$N8N_PID_FILE"
        return 0
    fi

    local pid i
    pid="$(cat "$N8N_PID_FILE")"
    _n8n_log "Stopping n8n (pid ${pid})."
    if _is_windows; then
        taskkill //PID "$pid" //T //F >/dev/null 2>&1 || true
    else
        kill "$pid" 2>/dev/null || true
    fi

    for (( i = 0; i < 20; i++ )); do
        _pid_alive || break
        sleep 1
    done

    if _pid_alive; then
        _n8n_warning "n8n did not stop gracefully. Force-killing."
        if _is_windows; then
            taskkill //PID "$pid" //F >/dev/null 2>&1 || true
        else
            kill -9 "$pid" 2>/dev/null || true
        fi
    fi

    rm -f "$N8N_PID_FILE"
    _n8n_success "n8n stopped."
}

status_n8n() {
    if _pid_alive; then
        _n8n_info "n8n running (pid $(cat "$N8N_PID_FILE")) on http://127.0.0.1:${N8N_PORT}"
    else
        _n8n_info "n8n not running."
        return 1
    fi
}

logs_n8n() {
    if [[ -f "$N8N_LOG_FILE" ]]; then
        tail -n 100 -f "$N8N_LOG_FILE"
    else
        _n8n_warning "Log file not found: $N8N_LOG_FILE"
    fi
}

# Print everything needed to debug "localhost refused to connect" in an iframe.
doctor_n8n() {
    local bp host base ver
    bp="$(_n8n_base_path)"
    ver="$(sed -n 's/^[[:space:]]*"version":[[:space:]]*"\([^"]*\)".*/\1/p' \
           "${REPO_ROOT}/packages/cli/package.json" 2>/dev/null | head -n1 || true)"
    _n8n_info "n8n version      : ${ver:-unknown}"
    _n8n_info "process running  : $(_pid_alive && echo "yes (pid $(cat "$N8N_PID_FILE"))" || echo no)"
    _n8n_info "base path        : ${bp:-/}"
    _n8n_info "listening on port ${N8N_PORT}:"
    { ss -ltn 2>/dev/null || netstat -an 2>/dev/null | grep LISTEN || true; } | grep -E "[:.]${N8N_PORT}[[:space:]]" \
        | sed 's/^/    /' || true
    for host in 127.0.0.1 localhost "[::1]"; do
        base="http://${host}:${N8N_PORT}${bp}/"
        _n8n_info "GET ${base}"
        curl -g -s -m 5 -D - -o /dev/null "$base" 2>&1 | tr -d '\r' \
            | grep -iE '^(HTTP/|location:|x-frame-options:|content-security-policy|cross-origin-|set-cookie:)' \
            | sed 's/^/    /' || true
        curl -g -s -m 5 -o /dev/null "$base" || echo "    (no response from ${host})"
    done
    _n8n_info "Next: open http://localhost:${N8N_PORT}${bp}/ directly in the SAME browser that shows your gateway."
    _n8n_info "      - loads  -> framing/headers problem (send me the browser console message)"
    _n8n_info "      - refused -> the browser cannot reach this host's port ${N8N_PORT} (remote box / tunnel): use the /n8n same-origin proxy"
}

# ------------------------------------------------------------
# Commands
# ------------------------------------------------------------

cmd_install() { _n8n_init || return 1; do_install; }
cmd_build()   { _n8n_init || return 1; do_build; }
cmd_start()   { start_n8n; }
cmd_stop()    { stop_n8n; }
cmd_restart() { stop_n8n; start_n8n; }
cmd_status()  { status_n8n; }
cmd_logs()    { logs_n8n; }
cmd_doctor()  { doctor_n8n; }

# ------------------------------------------------------------
# Standalone CLI (only when executed, not when sourced)
# ------------------------------------------------------------

_is_sourced() { [[ "${BASH_SOURCE[0]:-$0}" != "$0" ]]; }
if ! _is_sourced; then
    set -Eeuo pipefail

    case "${1:-start}" in
    install)   cmd_install ;;
    build)     cmd_build ;;
    start)     cmd_start ;;
    stop)      cmd_stop ;;
    restart)   cmd_restart ;;
    status)    cmd_status ;;
    logs)      cmd_logs ;;
    doctor)    cmd_doctor ;;
    *)
        cat <<EOF
Usage: $0 [install|build|start|stop|restart|status|logs|doctor]

  install   Run pnpm install
  build     Run pnpm build
  start     Clone/install/build if needed, then start n8n (default)
  stop      Stop n8n
  restart   Stop and start n8n
  status    Show process status
  logs      Tail the n8n log
  doctor    Print version, listeners and response headers (iframe debugging)

Examples:
  $0 start
  N8N_PORT=5679 $0 start
  N8N_SKIP_BUILD=1 $0 start
EOF
        exit 2
        ;;
    esac
fi