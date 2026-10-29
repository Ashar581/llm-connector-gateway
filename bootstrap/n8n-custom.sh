#!/usr/bin/env bash
# ============================================================
# Ashar n8n Edition - Custom Monorepo Runner
#
# Builds and runs the custom n8n monorepo (with Agent & Scraper Agent nodes)
# locally under runtime/n8n.
#
#   runtime/n8n/
#     data/            n8n user folder (SQLite DB, config, credentials)
#     logs/n8n.log
#     n8n.pid
#     encryption.key   generated once, reused forever
#
# The n8n binary itself runs from the built monorepo at:
#   packages/cli/bin/n8n   (via scripts/os-normalize.mjs)
#
# Supports: macOS Intel/Apple Silicon, Ubuntu/Debian (x64, arm64, armv7l),
#           Windows 10/11 (Git Bash, MSYS2, WSL)
# Needs:    curl, and either pnpm or corepack (Node is installed by the script)
#
# Usage:
#   ./n8n-custom.sh [start|stop|restart|status|logs|build|install]
#
# Env overrides:
#   N8N_PORT=5678
#   N8N_REPO_URL=https://github.com/Ashar581/llm-connector-n8n.git
#   N8N_REPO_ROOT=...            override where the repo is located
#   N8N_START_TIMEOUT=120        seconds to wait for readiness
#   N8N_HOME=...                 override runtime location (default: <repo>/runtime/n8n)
#   N8N_SKIP_BUILD=1             skip pnpm build (use existing dist)
#   N8N_SKIP_INSTALL=1           skip pnpm install
#   N8N_DATA_DIR=...             override n8n user data folder
#   N8N_LOG_LEVEL=info           n8n log level (debug, info, warn, error)
#   GENERIC_TIMEZONE=UTC         default timezone
# ============================================================

set -Eeuo pipefail

# ------------------------------------------------------------
# Helpers
# ------------------------------------------------------------

# BASH_SOURCE[0] is unset when sourced from certain shells; fall back to $0
_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"

# Use our own prefixed helpers to avoid collision with bootstrap's common.sh
_n8n_log()     { printf '[n8n] %s\n' "$*"; }
_n8n_info()    { printf '[n8n] %s\n' "$*"; }
_n8n_success() { printf '[n8n] OK: %s\n' "$*"; }
_n8n_warning() { printf '[n8n] WARN: %s\n' "$*" >&2; }
_n8n_fail()    { printf '[n8n] ERROR: %s\n' "$*" >&2; return 1; }

# Determine the n8n monorepo root:
# 1. Explicit override via N8N_REPO_ROOT
# 2. If RUNTIME_DIR is set (from bootstrap), use ${RUNTIME_DIR}/n8n-repo
# 3. Fallback: script directory (for standalone use inside the repo)
if [[ -n "${N8N_REPO_ROOT:-}" ]]; then
    REPO_ROOT="${N8N_REPO_ROOT}"
elif [[ -n "${RUNTIME_DIR:-}" ]]; then
    REPO_ROOT="${RUNTIME_DIR}/n8n-repo"
else
    REPO_ROOT="${_SCRIPT_DIR}"
fi

# ------------------------------------------------------------
# Platform detection
# ------------------------------------------------------------
# uname reports "Linux" inside WSL, so WSL is handled by the linux branch.
# Native Windows shells (Git Bash, MSYS2, Cygwin) report MSYS_NT-*,
# MINGW*, or CYGWIN* and need the windows branch.
_is_windows() {
    local u
    u="$(uname -s 2>/dev/null || echo "")"
    case "$u" in
        msys*|mingw*|cygwin*|MSYS_*|MINGW*|CYGWIN*) return 0 ;;
        *) return 1 ;;
    esac
}

# PATH separator for the current shell (: on Unix, ; on Windows shells)
if _is_windows; then
    _PATH_SEP=";"
    _NPM_CMD="npm.cmd"
else
    _PATH_SEP=":"
    _NPM_CMD="npm"
fi

# ------------------------------------------------------------
# Private Node.js Runtime (Node 24)
# ------------------------------------------------------------

_NODE_DIR="${REPO_ROOT}/runtime/node"
_NODE_VERSION="24.11.0"

# Download the Node.js archive for the current platform. Returns 0 and
# exports _NODE_ARCHIVE (path), _NODE_EXTRACT_CMD on success.
_n8n_node_archive_url() {
    local os arch url ext
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    arch="$(uname -m)"

    # Normalise architectures to Node's naming
    case "$arch" in
        x86_64|amd64|AMD64) arch="x64" ;;
        aarch64|arm64)       arch="arm64" ;;
        armv7l|armv7)        arch="armv7l" ;;
        i386|i686)           arch="x86" ;;
    esac

    if _is_windows; then
        # On Windows uname -s is msys/mingw/cygwin; Node names the platform win
        os="win"
        ext="zip"
        url="https://nodejs.org/dist/v${_NODE_VERSION}/node-v${_NODE_VERSION}-win-${arch}.zip"
    else
        case "$os" in
            darwin) ext="tar.gz" ;;
            linux)  ext="tar.xz" ;;
            *) _n8n_fail "Unsupported OS: $os" ;;
        esac
        url="https://nodejs.org/dist/v${_NODE_VERSION}/node-v${_NODE_VERSION}-${os}-${arch}.${ext}"
    fi

    _NODE_ARCHIVE="/tmp/node-v${_NODE_VERSION}-${os}-${arch}.${ext}"
    _NODE_URL="$url"
}

_n8n_extract_node() {
    _n8n_log "Extracting Node.js to ${_NODE_DIR}..."
    mkdir -p "${_NODE_DIR}"
    if _is_windows; then
        # unzip is available in Git Bash/MSYS2; fall back to powershell
        if command -v unzip >/dev/null 2>&1; then
            unzip -q -o "${_NODE_ARCHIVE}" -C "${_NODE_DIR}" \
                || _n8n_fail "Failed to extract Node.js"
            # zip strips the top-level dir; move contents up if present
            local top
            top="$(ls -A "${_NODE_DIR}" 2>/dev/null | head -n1 || true)"
            if [[ -n "$top" && -d "${_NODE_DIR}/${top}" ]]; then
                mv "${_NODE_DIR}/${top}"/* "${_NODE_DIR}/" 2>/dev/null || true
                rm -rf "${_NODE_DIR}/${top}"
            fi
        else
            powershell -Command "Expand-Archive -Path '${_NODE_ARCHIVE}' -DestinationPath '${_NODE_DIR}' -Force" \
                || _n8n_fail "Failed to extract Node.js"
        fi
    else
        if [[ "${_NODE_URL}" == *.tar.xz ]]; then
            tar -xJf "${_NODE_ARCHIVE}" -C "${_NODE_DIR}" --strip-components=1 \
                || _n8n_fail "Failed to extract Node.js"
        else
            tar -xzf "${_NODE_ARCHIVE}" -C "${_NODE_DIR}" --strip-components=1 \
                || _n8n_fail "Failed to extract Node.js"
        fi
    fi
    rm -f "${_NODE_ARCHIVE}"
}

_n8n_install_private_node() {
    _n8n_node_archive_url
    _n8n_log "Downloading private Node.js ${_NODE_VERSION} for ${_NODE_URL##*/}..."
    curl -fsSL "$_NODE_URL" -o "${_NODE_ARCHIVE}" || _n8n_fail "Failed to download Node.js"
    _n8n_extract_node

    # Enable corepack and install pnpm
    export PATH="${_NODE_DIR}/bin${_PATH_SEP}${PATH}"
    _n8n_log "Installing pnpm via corepack..."
    corepack enable
    corepack prepare pnpm@12.4.2 --activate || "${_NPM_CMD}" install -g pnpm@12.4.2

    _n8n_success "Private Node.js runtime installed at ${_NODE_DIR}"
}

_n8n_ensure_node() {
    # The node binary lives at bin/node (Unix) or node.exe (Windows)
    if [[ ! -x "${_NODE_DIR}/bin/node" && ! -x "${_NODE_DIR}/node.exe" ]]; then
        _n8n_install_private_node
    fi
    export PATH="${_NODE_DIR}/bin${_PATH_SEP}${REPO_ROOT}/node_modules/.bin${_PATH_SEP}${PATH}"
}

# Ensure node is available before we do anything else
_n8n_ensure_node

# Auto-clone the repo if it doesn't exist, or if the existing path is not a
# valid checkout (a stale/broken directory left by an interrupted previous
# run). git refuses to clone into a non-empty directory, so clear it first.
if [[ ! -f "${REPO_ROOT}/package.json" || ! -d "${REPO_ROOT}/.git" ]]; then
    if [[ -e "${REPO_ROOT}" ]]; then
        _n8n_warning "Existing path ${REPO_ROOT} is not a valid n8n repo. Removing it before clone..."
        rm -rf "${REPO_ROOT}"
    fi
    _n8n_log "n8n repo not found at ${REPO_ROOT}. Cloning..."
    mkdir -p "$(dirname "${REPO_ROOT}")"
    git clone "${N8N_REPO_URL:-https://github.com/Ashar581/llm-connector-n8n.git}" "${REPO_ROOT}" \
        || _n8n_fail "Failed to clone n8n repo"
    _n8n_success "Cloned n8n repo to ${REPO_ROOT}"
fi

# ------------------------------------------------------------
# Configuration
# ------------------------------------------------------------

N8N_HOME="${N8N_HOME:-${REPO_ROOT}/runtime/n8n}"
N8N_PORT="${N8N_PORT:-5678}"
N8N_START_TIMEOUT="${N8N_START_TIMEOUT:-120}"
N8N_DATA_DIR="${N8N_DATA_DIR:-${N8N_HOME}/data}"
N8N_LOG_DIR="${N8N_HOME}/logs"
N8N_LOG_FILE="${N8N_LOG_DIR}/n8n.log"
N8N_PID_FILE="${N8N_HOME}/n8n.pid"
N8N_KEY_FILE="${N8N_HOME}/encryption.key"

# Built n8n entry point (after pnpm build)
N8N_BIN="${REPO_ROOT}/packages/cli/bin/n8n"
N8N_OS_NORMALIZE="${REPO_ROOT}/scripts/os-normalize.mjs"

# ------------------------------------------------------------
# Small helpers
# ------------------------------------------------------------

_port_in_use() {
    if _is_windows; then
        # netstat is available on Windows; fall back to a curl health probe
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
        # tasklist shows running processes; the PID column is the 2nd field
        tasklist 2>/dev/null | awk '{print $2}' | grep -qx "$pid"
    else
        kill -0 "$pid" 2>/dev/null
    fi
}

_check_prereqs() {
    # pnpm and curl are genuinely required; Node is not, because the script
    # installs its own private Node 24 runtime. A system Node >=24 is fine to
    # use instead, but its absence must not block the script.
    command -v curl >/dev/null 2>&1 || _n8n_fail "curl is required."
    if command -v pnpm >/dev/null 2>&1; then
        _n8n_info "Using system pnpm: $(pnpm -v)"
    elif command -v corepack >/dev/null 2>&1; then
        _n8n_info "pnpm not found; will install via corepack during Node setup."
    else
        _n8n_fail "pnpm not found and corepack unavailable. Install Node.js >=24 first."
    fi
    if command -v node >/dev/null 2>&1; then
        local node_major
        node_major="$(node -v | sed 's/^v//; s/\..*//')"
        if (( node_major >= 24 )); then
            _n8n_info "Using system Node.js $(node -v)"
        else
            _n8n_info "System Node.js $(node -v) is too old; will install private Node ${_NODE_VERSION}."
        fi
    else
        _n8n_info "No system Node.js found; will install private Node ${_NODE_VERSION}."
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
    _n8n_log "Installing dependencies with pnpm..."
    cd "$REPO_ROOT"
    # --frozen-lockfile fails if the lockfile is out of sync with the
    # package.json (e.g. after a branch switch). Fall back to a normal
    # install so a stale lockfile never blocks the script.
    if ! pnpm install --frozen-lockfile; then
        _n8n_warning "Frozen install failed; retrying with a normal pnpm install..."
        pnpm install || _n8n_fail "pnpm install failed."
    fi
    _n8n_success "Dependencies installed."
}

do_build() {
    if [[ "${N8N_SKIP_BUILD:-0}" == "1" ]]; then
        _n8n_log "Skipping build (N8N_SKIP_BUILD=1)."
        return 0
    fi
    _n8n_log "Building monorepo (this may take a few minutes)..."
    cd "$REPO_ROOT"
    mkdir -p "$N8N_LOG_DIR"
    # A build failure is not fatal: an existing dist/ from a prior run may
    # still launch, and the start path surfaces the real problem via the log.
    if pnpm build > "${N8N_LOG_DIR}/build.log" 2>&1; then
        _n8n_success "Build complete."
    else
        _n8n_warning "Build failed (see ${N8N_LOG_DIR}/build.log). Continuing..."
        return 1
    fi
}

ensure_built() {
    # bin/n8n is checked into the repo, so it always exists. We must also
    # verify that node_modules was actually installed (otherwise require()
    # fails with MODULE_NOT_FOUND) and that dist/ was built.
    if [[ ! -x "$N8N_BIN" || ! -f "$N8N_OS_NORMALIZE" \
        || ! -d "${REPO_ROOT}/node_modules" || ! -d "${REPO_ROOT}/packages/cli/dist" ]]; then
        _n8n_log "Built artifacts not found. Running install + build..."
        do_install
        # A build failure must not abort the script: an existing dist/ from a
        # previous run may still work, and the start path reports the real
        # failure via the log instead of a misleading clone error.
        if ! do_build; then
            _n8n_warning "Build failed. Continuing with any existing build artifacts..."
        fi
    else
        _n8n_log "Using existing build at ${N8N_BIN}"
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

start_n8n() {
    if _pid_alive; then
        _n8n_log "n8n already running (pid $(cat "$N8N_PID_FILE"))."
        return 0
    fi

    if _port_in_use "$N8N_PORT"; then
        _n8n_fail "Port ${N8N_PORT} is already in use. Set N8N_PORT to change it."
        return 1
    fi

    _ensure_key
    mkdir -p "$N8N_DATA_DIR" "$N8N_LOG_DIR"

    ensure_built

    _n8n_log "Starting n8n on http://127.0.0.1:${N8N_PORT}"

    (
        export N8N_USER_FOLDER="$N8N_DATA_DIR"
        export N8N_ENCRYPTION_KEY
        N8N_ENCRYPTION_KEY="$(cat "$N8N_KEY_FILE")"

        # Network: bind to localhost only
        export N8N_LISTEN_ADDRESS="127.0.0.1"
        export N8N_PORT="$N8N_PORT"
        export N8N_PROTOCOL="http"
        export N8N_HOST="127.0.0.1"
        export N8N_EDITOR_BASE_URL="${N8N_EDITOR_BASE_URL:-http://127.0.0.1:${N8N_PORT}}"
        export WEBHOOK_URL="${WEBHOOK_URL:-http://127.0.0.1:${N8N_PORT}/}"
        export N8N_SECURE_COOKIE="${N8N_SECURE_COOKIE:-false}"

        # Quiet, offline-friendly defaults
        export N8N_DIAGNOSTICS_ENABLED="false"
        export N8N_VERSION_NOTIFICATIONS_ENABLED="false"
        export N8N_PERSONALIZATION_ENABLED="false"
        export N8N_HIRING_BANNER_ENABLED="false"
        export N8N_TEMPLATES_ENABLED="${N8N_TEMPLATES_ENABLED:-true}"

        # Public REST API
        export N8N_PUBLIC_API_DISABLED="false"

        # Task runners for Code nodes
        export N8N_RUNNERS_ENABLED="true"

        # Allow embedding in iframe
        export N8N_CONTENT_SECURITY_POLICY="frame-ancestors 'self' http://localhost:* http://127.0.0.1:*"

        export N8N_RELEASE_TYPE="stable"
        export GENERIC_TIMEZONE="${GENERIC_TIMEZONE:-UTC}"

        # Keep the DB small: prune old executions
        export EXECUTIONS_DATA_PRUNE="true"
        export EXECUTIONS_DATA_MAX_AGE="${EXECUTIONS_DATA_MAX_AGE:-168}"

        # Log level
        export N8N_LOG_LEVEL="${N8N_LOG_LEVEL:-info}"

        cd "$REPO_ROOT"
        # Run the n8n CLI directly. The bin/n8n is a Node script with a shebang,
        # so we don't need os-normalize.mjs (which runs in-process via zx and
        # would block the PID file from being written).
        if command -v nohup >/dev/null 2>&1; then
            nohup "$N8N_BIN" start >>"$N8N_LOG_FILE" 2>&1 &
        else
            "$N8N_BIN" start >>"$N8N_LOG_FILE" 2>&1 &
        fi
        echo $! > "$N8N_PID_FILE"
    )

    # Wait for readiness
    local i
    for (( i = 0; i < N8N_START_TIMEOUT; i++ )); do
        if ! _pid_alive; then
            _n8n_fail "n8n exited during startup. See ${N8N_LOG_FILE}"
            return 1
        fi
        if curl -fsS -o /dev/null "http://127.0.0.1:${N8N_PORT}/healthz" 2>/dev/null; then
            _n8n_success "n8n is up: http://127.0.0.1:${N8N_PORT}"
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

# ------------------------------------------------------------
# Commands
# ------------------------------------------------------------

cmd_install() {
    _check_prereqs
    do_install
}

cmd_build() {
    _check_prereqs
    do_build
}

cmd_start() {
    _check_prereqs
    start_n8n
}

cmd_stop() {
    stop_n8n
}

cmd_restart() {
    stop_n8n
    start_n8n
}

cmd_status() {
    status_n8n
}

cmd_logs() {
    logs_n8n
}

# ------------------------------------------------------------
# Standalone CLI (only when executed, not when sourced)
# ------------------------------------------------------------

# When sourced, BASH_SOURCE[0] is the script path; when executed, it's $0.
# Use a guard that works in both cases.
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
    *)
        cat <<EOF
Usage: $0 [install|build|start|stop|restart|status|logs]

Commands:
  install   Run pnpm install (fetch dependencies)
  build     Run pnpm build (compile TypeScript)
  start     Install (if needed), build (if needed), and start n8n
  stop      Stop running n8n
  restart   Stop and start n8n
  status    Show n8n process status
  logs      Tail n8n log file

Environment:
  N8N_PORT=5678              Port to bind (default: 5678)
  N8N_REPO_URL=...           Git repo to clone (default: https://github.com/Ashar581/llm-connector-n8n.git)
  N8N_REPO_ROOT=...          Override repo location (default: <script-dir> or \$RUNTIME_DIR/n8n-repo)
  N8N_HOME=...               Runtime directory (default: ./runtime/n8n)
  N8N_DATA_DIR=...           n8n user data folder (default: \$N8N_HOME/data)
  N8N_START_TIMEOUT=120      Startup wait seconds (default: 120)
  N8N_SKIP_INSTALL=1         Skip pnpm install
  N8N_SKIP_BUILD=1           Skip pnpm build
  N8N_LOG_LEVEL=info         n8n log level (debug|info|warn|error)
  GENERIC_TIMEZONE=UTC       Default timezone

Notes:
  The script installs its own private Node.js 24 runtime under runtime/node,
  so a system Node.js is optional. On Windows it uses unzip/powershell for the
  archive and taskkill/netstat for process management.

Examples:
  $0 start                    # Full startup (install+build+run)
  N8N_PORT=5679 $0 start      # Custom port
  N8N_SKIP_BUILD=1 $0 start   # Quick restart using existing build
  $0 logs                     # Follow logs
EOF
        exit 2
        ;;
    esac
fi