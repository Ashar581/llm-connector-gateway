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
#   N8N_MANAGE_NGROK=0|auto|1    default 0 = never touch ngrok (recommended); auto/1 = script may restart it
#   N8N_PROXY=1                  start the optional standalone front proxy (default 0: Spring Boot proxies
#                                /n8n via N8nProxyController, ngrok stays on the Spring port)
#   N8N_PROXY_PORT=6970         port of the front proxy (point ngrok here). N8N_PROXY=0 disables it
#   GATEWAY_PORT=8080            Spring Boot port (auto-detected from application.properties)
#   N8N_BASE_PATH=/n8n           sub-path n8n is served under (default /n8n, for the Spring Boot
#                                proxy; set N8N_BASE_PATH="" to serve at / like stock n8n)
#   N8N_PUBLIC_URL=...           public URL as the browser sees it (default: your ngrok domain + /n8n;
#                                set N8N_PUBLIC_URL="" to disable)
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

# Public URL of n8n as the browser sees it (your ngrok domain + /n8n). Used for n8n's editor and
# webhook URLs. Override with N8N_PUBLIC_URL=..., or disable with N8N_PUBLIC_URL="".
N8N_PUBLIC_URL="${N8N_PUBLIC_URL-https://transparent-suzann-limy.ngrok-free.dev/n8n}"

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
    local bp="${N8N_BASE_PATH-/n8n}"   # unset -> /n8n ; N8N_BASE_PATH="" -> serve at /
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

# ------------------------------------------------------------
# Front proxy (so ONE public URL serves both the gateway UI and n8n)
#
#   ngrok --> proxy :N8N_PROXY_PORT --+--> /n8n/*  -> n8n     127.0.0.1:N8N_PORT
#                                     +--> others  -> Spring  127.0.0.1:GATEWAY_PORT
#
# The iframe then loads /n8n/ from the same origin as the UI, so no backend change
# is needed (frame-src 'self' already allows it) and Spring Security headers never
# touch n8n responses. Disable with N8N_PROXY=0.
# ------------------------------------------------------------

N8N_PROXY_PID_FILE="${N8N_HOME}/proxy.pid"
N8N_PROXY_JS="${N8N_HOME}/n8n-proxy.js"
N8N_PROXY_LOG="${N8N_LOG_DIR}/proxy.log"

# Spring Boot port: $GATEWAY_PORT, else server.port from application.properties/yml, else 6969
_n8n_gateway_port() {
    if [[ -n "${GATEWAY_PORT:-}" ]]; then echo "$GATEWAY_PORT"; return; fi
    local root p f
    root="$(dirname "${_RUNTIME_BASE}")"
    for f in "$root"/src/main/resources/application*.properties "$root"/src/main/resources/application*.yml \
             "$root"/src/main/resources/application*.yaml "$root"/application*.properties; do
        [[ -f "$f" ]] || continue
        p="$(sed -n 's/^[[:space:]]*server\.port[[:space:]]*[=:][[:space:]]*\([0-9]\{2,5\}\).*/\1/p' "$f" | head -n1 || true)"
        [[ -z "$p" ]] && p="$(sed -n 's/^[[:space:]]*port:[[:space:]]*\([0-9]\{2,5\}\)[[:space:]]*$/\1/p' "$f" | head -n1 || true)"
        [[ -n "$p" ]] && { echo "$p"; return; }
    done
    echo 6969
}

N8N_PROXY_PORT_FILE="${N8N_HOME}/proxy.port"

# Proxy port: $N8N_PROXY_PORT, else the one a running proxy uses, else the first FREE port from
# 6970 up that is not Spring's own port (pointing ngrok at Spring's port would bypass the proxy).
_n8n_proxy_port() {
    if [[ -n "${N8N_PROXY_PORT:-}" ]]; then echo "$N8N_PROXY_PORT"; return; fi
    if _n8n_proxy_alive && [[ -s "$N8N_PROXY_PORT_FILE" ]]; then cat "$N8N_PROXY_PORT_FILE"; return; fi
    local gp port
    gp="$(_n8n_gateway_port)"
    for port in 6970 6971 6972 6973 6974 6975 7070; do
        [[ "$port" == "$gp" ]] && continue
        _port_in_use "$port" || { echo "$port"; return; }
    done
    echo 6970
}

_n8n_proxy_alive() {
    [[ -f "$N8N_PROXY_PID_FILE" ]] || return 1
    local pid; pid="$(cat "$N8N_PROXY_PID_FILE" 2>/dev/null || true)"
    [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

_n8n_write_proxy_js() {
    mkdir -p "$N8N_HOME"
    cat > "$N8N_PROXY_JS" <<'JS'
// Tiny dependency-free reverse proxy: /n8n -> n8n, everything else -> Spring Boot.
const http = require('http');
const net = require('net');

const LISTEN = Number(process.env.PROXY_PORT);
const SPRING = Number(process.env.GATEWAY_PORT);
const N8N = Number(process.env.N8N_PORT);
const BASE = process.env.N8N_BASE_PATH || '/n8n';

const isN8n = (url) => url === BASE || url.startsWith(BASE + '/') || url.startsWith(BASE + '?');
const target = (url) => (isN8n(url) ? N8N : SPRING);

function fwdHeaders(req) {
  const h = Object.assign({}, req.headers);              // Host is preserved on purpose
  const ip = req.socket.remoteAddress;
  h['x-forwarded-for'] = req.headers['x-forwarded-for'] ? req.headers['x-forwarded-for'] + ', ' + ip : ip;
  if (!h['x-forwarded-proto']) h['x-forwarded-proto'] = 'http';
  if (!h['x-forwarded-host'] && req.headers.host) h['x-forwarded-host'] = req.headers.host;
  return h;
}

// The built UI hardcodes http://localhost:5678 as the iframe URL. Rewrite it to the same-origin
// /n8n path on the fly in JS bundles coming from Spring, so no rebuild / file patching is needed.
const REWRITE = new RegExp('https?://(?:localhost|127\\.0\\.0\\.1):' + N8N + '(?![0-9])', 'g');
const isJsPath = (url) => /\.m?js(\?|$)/.test(url);

const server = http.createServer((req, res) => {
  if (req.url === BASE) {                                // /n8n -> /n8n/
    res.writeHead(302, { Location: BASE + '/' });
    return res.end();
  }
  const headers = fwdHeaders(req);
  const wantRewrite = !isN8n(req.url) && req.method === 'GET' && isJsPath(req.url);
  if (wantRewrite) {                                     // get the full, uncompressed body
    ['accept-encoding', 'if-none-match', 'if-modified-since', 'range', 'if-range'].forEach((k) => delete headers[k]);
  }
  const up = http.request(
    { host: '127.0.0.1', port: target(req.url), method: req.method, path: req.url, headers },
    (ur) => {
      const ct = String(ur.headers['content-type'] || '');
      if (wantRewrite && ur.statusCode === 200 && /javascript/i.test(ct) && !ur.headers['content-encoding']) {
        const chunks = [];
        ur.on('data', (c) => chunks.push(c));
        ur.on('end', () => {
          const body = Buffer.concat(chunks).toString('utf8');
          const out = body.replace(REWRITE, BASE);
          const buf = Buffer.from(out, 'utf8');
          const h = Object.assign({}, ur.headers);
          ['etag', 'last-modified', 'content-encoding', 'transfer-encoding'].forEach((k) => delete h[k]);
          h['content-length'] = String(buf.length);
          h['cache-control'] = 'no-cache';
          res.writeHead(200, ur.statusMessage, h);
          res.end(buf);
          if (out !== body) console.log('rewrote iframe URL in ' + req.url);
        });
        return;
      }
      res.writeHead(ur.statusCode, ur.statusMessage, ur.headers);
      ur.pipe(res);                                      // streams: SSE stays live
    }
  );
  up.on('error', (e) => {
    if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'text/plain' });
    res.end('Bad gateway: ' + e.message + '\n');
  });
  req.on('aborted', () => up.destroy());
  req.pipe(up);
});

server.on('upgrade', (req, socket, head) => {            // websockets (n8n or Spring)
  const up = net.connect(target(req.url), '127.0.0.1', () => {
    const lines = [req.method + ' ' + req.url + ' HTTP/' + req.httpVersion];
    for (let i = 0; i < req.rawHeaders.length; i += 2) lines.push(req.rawHeaders[i] + ': ' + req.rawHeaders[i + 1]);
    up.write(lines.join('\r\n') + '\r\n\r\n');
    if (head && head.length) up.write(head);
    socket.pipe(up).pipe(socket);
  });
  up.on('error', () => socket.destroy());
  socket.on('error', () => up.destroy());
});

server.listen(LISTEN, '127.0.0.1', () =>
  console.log('proxy :' + LISTEN + '  ' + BASE + '/* -> n8n:' + N8N + '   other -> spring:' + SPRING));
JS
}

# ngrok control. N8N_MANAGE_NGROK: 0 (DEFAULT, never touches ngrok) | auto | 1
#   0   : leave ngrok alone; only print how to point it at the proxy port (and how to go back).
#   auto: if ngrok already runs and forwards to Spring's port, restart it for the same domain.
#   1   : always (re)start ngrok for the N8N_PUBLIC_URL domain -> proxy port.
# Restarting is risky on the free plan (one session; the old one lingers ~1 min server side), so a
# failed switch now RESTORES the original forwarding instead of leaving the endpoint offline.
_NG_ADDR_PORT=""; _NG_HOST=""
_n8n_ngrok_info() {
    local json a u
    json="$(curl -s -m 2 http://127.0.0.1:4040/api/tunnels 2>/dev/null || true)"
    a="$(printf '%s' "$json" | grep -o '"addr":"[^"]*"' | head -n1 || true)"
    a="${a#\"addr\":\"}"; a="${a%\"}"
    _NG_ADDR_PORT="${a##*:}"; [[ -n "$a" ]] || _NG_ADDR_PORT=""
    u="$(printf '%s' "$json" | grep -o '"public_url":"https://[^"]*"' | head -n1 || true)"
    u="${u#\"public_url\":\"https://}"; u="${u%\"}"
    _NG_HOST="${u%%/*}"
}

# Start ngrok for HOST -> PORT and wait up to WAIT seconds for the local API to confirm it.
_n8n_ngrok_up() {
    local port="$1" host="$2" wait="${3:-90}" flag i
    for flag in --domain --url; do
        nohup ngrok http "$port" "${flag}=${host}" >>"${N8N_LOG_DIR}/ngrok.log" 2>&1 < /dev/null &
        echo $! > "${N8N_HOME}/ngrok.pid"
        for (( i = 0; i < wait; i++ )); do
            sleep 1
            _n8n_ngrok_info
            [[ "$_NG_ADDR_PORT" == "$port" ]] && return 0
            kill -0 "$(cat "${N8N_HOME}/ngrok.pid")" 2>/dev/null || break   # ngrok exited early
        done
        kill "$(cat "${N8N_HOME}/ngrok.pid")" 2>/dev/null || true
        sleep 3
    done
    return 1
}

_n8n_manage_ngrok() {
    local pp="$1" host="${2:-}" orig="${3:-}"
    command -v ngrok >/dev/null 2>&1 || { _n8n_warning "ngrok binary not found; cannot retarget it."; return 1; }
    if [[ -z "$host" ]]; then host="${N8N_PUBLIC_URL#*://}"; host="${host%%/*}"; fi
    [[ -n "$host" ]] || { _n8n_warning "Unknown ngrok domain (set N8N_PUBLIC_URL)."; return 1; }

    _n8n_log "Switching ngrok: https://${host}  :${orig:-?} -> :${pp}"
    pkill -x ngrok 2>/dev/null || true
    sleep 3
    if _n8n_ngrok_up "$pp" "$host" 90; then
        _n8n_success "ngrok now forwards https://${host} -> :${pp}"
        return 0
    fi
    _n8n_warning "Switch failed. Restoring ngrok -> :${orig:-?} so your site stays online."
    if [[ -n "$orig" ]] && _n8n_ngrok_up "$orig" "$host" 120; then
        _n8n_success "ngrok restored: https://${host} -> :${orig}"
    else
        _n8n_warning "ngrok is OFFLINE. Run by hand:  ngrok http ${orig:-6969} --domain=${host}"
    fi
    return 1
}

_n8n_ngrok_hint() {
    local pp="$1" mode="${N8N_MANAGE_NGROK:-0}" gp host
    gp="$(_n8n_gateway_port)"
    host="${N8N_PUBLIC_URL#*://}"; host="${host%%/*}"
    _n8n_ngrok_info

    if [[ "$_NG_ADDR_PORT" == "$pp" ]]; then
        _n8n_success "ngrok forwards to the proxy (port ${pp}): n8n is available inside the UI."
        return 0
    fi
    if [[ "$mode" == "1" ]] || [[ "$mode" == "auto" && -n "$_NG_ADDR_PORT" && "$_NG_ADDR_PORT" == "$gp" ]]; then
        _n8n_manage_ngrok "$pp" "$_NG_HOST" "$_NG_ADDR_PORT" && return 0
        return 0
    fi
    _n8n_info "ngrok is untouched (forwarding to :${_NG_ADDR_PORT:-?}). Your main UI keeps working as before."
    _n8n_info "To show n8n INSIDE the UI, restart ngrok against the proxy:  ngrok http ${pp} --domain=${host:-<your-domain>}"
    _n8n_info "To go back anytime:                                        ngrok http ${gp} --domain=${host:-<your-domain>}"
    return 0
}

# The built UI hardcodes http://localhost:5678 as the iframe URL, which from a remote
# browser points at the viewer's own machine ("localhost refused to connect").
# Rewrite it to the same-origin /n8n path in any built UI bundle under the project, so no
# frontend rebuild is needed. Idempotent; skip with N8N_PATCH_UI=0. Hard-refresh the browser after.
_n8n_patch_ui_url() {
    [[ "${N8N_PATCH_UI:-1}" == "0" ]] && return 0
    local bp root f n=0
    bp="$(_n8n_base_path)"
    [[ -n "$bp" ]] || return 0
    root="$(dirname "${_RUNTIME_BASE}")"
    while IFS= read -r f; do
        [[ -n "$f" ]] || continue
        if command -v perl >/dev/null 2>&1; then
            perl -pi -e "s#https?://(localhost|127\.0\.0\.1):${N8N_PORT}#${bp}#g" "$f" || continue
        else
            sed "s#https\{0,1\}://\(localhost\|127\.0\.0\.1\):${N8N_PORT}#${bp}#g" "$f" > "$f.tmp" && mv "$f.tmp" "$f" || continue
        fi
        _n8n_log "Patched UI bundle: ${f#"$root"/}  (iframe URL -> ${bp})"
        n=$((n + 1))
    done < <(find "$root" \( -name node_modules -o -name .git -o -name runtime \) -prune -o \
                  -type f -name '*.js' -path '*assets*' -print 2>/dev/null \
             | xargs grep -lE "(localhost|127\.0\.0\.1):${N8N_PORT}" 2>/dev/null || true)
    if (( n == 0 )); then
        _n8n_info "UI patch: no bundle with localhost:${N8N_PORT} found (already patched, or built with VITE_N8N_BASE_URL=${bp})."
    else
        _n8n_warning "Patched ${n} UI file(s). Restart Spring Boot / rebuild the jar if the UI is packaged, then hard-refresh the browser."
    fi
}

_n8n_start_proxy() {
    [[ "${N8N_PROXY:-0}" == "1" ]] || return 0        # opt-in: Spring Boot proxies /n8n by default
    [[ -n "$(_n8n_base_path)" ]] || return 0          # proxy only makes sense with a base path
    local pp gp node_bin
    pp="$(_n8n_proxy_port)"; gp="$(_n8n_gateway_port)"
    node_bin="$(_n8n_node_bin)"
    [[ -n "$node_bin" ]] || { _n8n_warning "No private Node; cannot start the front proxy."; return 0; }

    if _n8n_proxy_alive; then _n8n_ngrok_hint "$pp"; return 0; fi
    if _port_in_use "$pp"; then
        _n8n_warning "Proxy port ${pp} is busy. Set N8N_PROXY_PORT to another port."
        return 0
    fi

    _n8n_write_proxy_js
    mkdir -p "$N8N_LOG_DIR"
    (
        export PROXY_PORT="$pp" GATEWAY_PORT="$gp" N8N_PORT="$N8N_PORT" N8N_BASE_PATH="$(_n8n_base_path)"
        nohup "$node_bin" "$N8N_PROXY_JS" >>"$N8N_PROXY_LOG" 2>&1 < /dev/null &
        echo $! > "$N8N_PROXY_PID_FILE"
    )
    local w
    for (( w = 0; w < 30; w++ )); do               # up to ~15s: slow disks / cold Node start
        _n8n_proxy_alive || break
        _port_in_use "$pp" && break
        sleep 0.5
    done
    if _n8n_proxy_alive && _port_in_use "$pp"; then
        echo "$pp" > "$N8N_PROXY_PORT_FILE"
        _n8n_success "Front proxy up on :${pp}  (/n8n -> n8n:${N8N_PORT}, rest -> Spring:${gp})"
        _n8n_ngrok_hint "$pp"
    else
        _n8n_warning "Front proxy failed to start. See ${N8N_PROXY_LOG}"
    fi
}

_n8n_stop_proxy() {
    _n8n_proxy_alive || { rm -f "$N8N_PROXY_PID_FILE"; return 0; }
    kill "$(cat "$N8N_PROXY_PID_FILE")" 2>/dev/null || true
    rm -f "$N8N_PROXY_PID_FILE" "$N8N_PROXY_PORT_FILE"
    _n8n_log "Front proxy stopped."
}

start_n8n() {
    if _pid_alive; then
        _n8n_log "n8n already running (pid $(cat "$N8N_PID_FILE"))."
        _n8n_patch_ui_url
        _n8n_start_proxy
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
            # Requests arrive via a proxy (Cloudflare tunnel -> Spring Boot): trust 1 hop
            export N8N_PROXY_HOPS="${N8N_PROXY_HOPS:-1}"
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
            _n8n_patch_ui_url
            _n8n_start_proxy
            return 0
        fi
        sleep 1
    done

    _n8n_fail "n8n did not become ready within ${N8N_START_TIMEOUT}s. See ${N8N_LOG_FILE}"
    return 1
}

stop_n8n() {
    _n8n_stop_proxy
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
        if _n8n_proxy_alive; then
            _n8n_info "front proxy running on :$(_n8n_proxy_port) (/n8n -> n8n, rest -> Spring :$(_n8n_gateway_port))"
        fi
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

# Probe the real public path (ngrok -> proxy -> n8n/Spring) the way the browser does, and check
# whether the UI bundle's iframe URL got rewritten. Run: bash n8n-custom.sh doctor
_n8n_doctor_public() {
    local host pp tmp base path url res ui bundle n_old n_new
    host="${N8N_PUBLIC_URL#*://}"; host="${host%%/*}"
    pp="$(_n8n_proxy_port)"
    tmp="$(mktemp 2>/dev/null || echo "/tmp/n8n-probe.$$")"
    _n8n_info "---- end-to-end probes ----"
    for base in "http://127.0.0.1:${pp}" ${host:+"https://${host}"}; do
        for path in /n8n/ /n8n/rest/settings; do
            url="${base}${path}"
            res="$(curl -s -m 10 -H 'ngrok-skip-browser-warning: 1' -o "$tmp" -w '%{http_code}  %{content_type}' "$url" 2>/dev/null || true)"
            _n8n_info "GET ${url}  ->  ${res:-no response}"
            printf '        body: %s\n' "$(head -c 140 "$tmp" 2>/dev/null | tr -d '\r\n')"
        done
    done
    ui="http://127.0.0.1:${pp}"; [[ -n "$host" ]] && ui="https://${host}"
    bundle="$(curl -s -m 10 -H 'ngrok-skip-browser-warning: 1' "${ui}/ui/" 2>/dev/null | grep -o 'assets/index-[^"]*\.js' | head -n1 || true)"
    if [[ -n "$bundle" ]]; then
        curl -s -m 30 -H 'ngrok-skip-browser-warning: 1' "${ui}/ui/${bundle}" -o "$tmp" 2>/dev/null || true
        n_old="$(grep -o "localhost:${N8N_PORT}" "$tmp" 2>/dev/null | wc -l | tr -d ' ')"
        n_new="$(grep -o '"/n8n"' "$tmp" 2>/dev/null | wc -l | tr -d ' ')"
        _n8n_info "UI bundle ${bundle}: still has localhost:${N8N_PORT} x${n_old} (want 0), has \"/n8n\" x${n_new} (want >=1)"
    else
        _n8n_info "UI bundle: could not locate it from ${ui}/ui/ (UI not reachable on that URL?)"
    fi
    rm -f "$tmp"
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
    _n8n_info "---- public path: ngrok -> proxy -> n8n/Spring ----"
    local gp pp naddr
    gp="$(_n8n_gateway_port)"; pp="$(_n8n_proxy_port)"
    _n8n_info "Spring port (detected): ${gp}      listening: $(_port_in_use "$gp" && echo yes || echo NO)"
    _n8n_info "front proxy port       : ${pp}      running  : $(_n8n_proxy_alive && echo yes || echo NO)   (log: ${N8N_PROXY_LOG})"
    naddr="$(curl -s -m 2 http://127.0.0.1:4040/api/tunnels 2>/dev/null | grep -o '"addr":"[^"]*"' | head -n1 || true)"
    _n8n_info "ngrok forwards to      : ${naddr:-<ngrok API not reachable on :4040>}"
    if [[ "${N8N_PROXY:-0}" == "1" && -n "$naddr" && "$naddr" != *":${pp}\"" ]]; then
        _n8n_warning ">>> ngrok is NOT pointed at the proxy port ${pp}: /n8n goes straight to Spring (that is your NoResourceFoundException)."
        _n8n_warning ">>> Fix: pkill -x ngrok; ngrok http --url=<your-domain> ${pp}    (or run with N8N_MANAGE_NGROK=1)"
    fi
    _n8n_info "GET proxy :${pp}/n8n  ->  $(curl -s -m 3 -o /dev/null -w '%{http_code} %{redirect_url}' "http://127.0.0.1:${pp}${bp:-/n8n}" 2>/dev/null || echo 'no response')   (expect: 302 .../n8n/)"
    _n8n_doctor_public
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