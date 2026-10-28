#!/usr/bin/env bash
# ============================================================
# LLM Connector Gateway - n8n (native, no Docker)
#
# Installs and runs n8n project-locally under runtime/n8n.
#
#   runtime/n8n/
#     node/            private Node.js (only if system Node is unsuitable)
#     node_modules/    n8n itself (npm install --prefix)
#     data/            n8n user folder (SQLite DB, config, credentials)
#     logs/n8n.log
#     n8n.pid
#     encryption.key   generated once, reused forever
#
# Supports: macOS Intel, macOS Apple Silicon, Ubuntu/Debian (x64, arm64)
# Needs:    curl, tar  (nothing else, no sudo, no nvm, no Docker)
#
# Usage:
#   Sourced from bootstrap.sh:   prepare_n8n
#   Standalone:                  ./n8n.sh [start|stop|restart|status|logs]
#
# Env overrides:
#   N8N_ENABLED=0         skip n8n in bootstrap
#   N8N_PORT=5678
#   N8N_VERSION=latest    pin e.g. N8N_VERSION=1.100.1 for reproducibility
#   N8N_UPDATE=1          re-install/upgrade even if already installed
#   N8N_NODE_MAJOR=24     major version of private Node if one is needed
#   N8N_NODE_MIN_MAJOR=24 minimum acceptable Node major (n8n 2.x needs >=24)
#   N8N_START_TIMEOUT=120 seconds to wait for readiness
#   N8N_HOME=...          override install location
# ============================================================

# ------------------------------------------------------------
# Standalone support: load helpers if not already loaded
# ------------------------------------------------------------

_N8N_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! declare -F log >/dev/null 2>&1; then
    if [[ -f "${_N8N_SCRIPT_DIR}/common.sh" ]]; then
        # shellcheck disable=SC1091
        source "${_N8N_SCRIPT_DIR}/common.sh"
    fi
fi

# Fallback helpers, only defined if common.sh did not provide them
declare -F log     >/dev/null 2>&1 || log()     { printf '[n8n] %s\n' "$*"; }
declare -F info    >/dev/null 2>&1 || info()    { printf '[n8n] %s\n' "$*"; }
declare -F success >/dev/null 2>&1 || success() { printf '[n8n] OK: %s\n' "$*"; }
declare -F warning >/dev/null 2>&1 || warning() { printf '[n8n] WARN: %s\n' "$*" >&2; }
declare -F fail    >/dev/null 2>&1 || fail()    { printf '[n8n] ERROR: %s\n' "$*" >&2; return 1; }


# ------------------------------------------------------------
# Configuration
# ------------------------------------------------------------

# If your common.sh already defines PROJECT_ROOT / RUNTIME_DIR, those win.
# Otherwise we assume this script sits one level below the project root.
: "${PROJECT_ROOT:=$(cd "${_N8N_SCRIPT_DIR}/.." && pwd)}"
: "${RUNTIME_DIR:=${PROJECT_ROOT}/runtime}"

N8N_HOME="${N8N_HOME:-${RUNTIME_DIR}/n8n}"
N8N_PORT="${N8N_PORT:-5678}"
N8N_VERSION="${N8N_VERSION:-latest}"
N8N_NODE_MAJOR="${N8N_NODE_MAJOR:-24}"
N8N_NODE_MIN_MAJOR="${N8N_NODE_MIN_MAJOR:-24}"
N8N_START_TIMEOUT="${N8N_START_TIMEOUT:-120}"

N8N_NODE_DIR="${N8N_HOME}/node"
N8N_DATA_DIR="${N8N_HOME}/data"
N8N_LOG_DIR="${N8N_HOME}/logs"
N8N_LOG_FILE="${N8N_LOG_DIR}/n8n.log"
N8N_PID_FILE="${N8N_HOME}/n8n.pid"
N8N_KEY_FILE="${N8N_HOME}/encryption.key"
N8N_BIN="${N8N_HOME}/node_modules/.bin/n8n"
N8N_NODE_STAMP="${N8N_HOME}/.node-major"

# Resolved at runtime by _n8n_ensure_node
N8N_NODE_BIN_DIR=""

# Python used by node-gyp when system Python lacks distutils (3.12+)
N8N_GYP_PYTHON=""


# ------------------------------------------------------------
# Small helpers
# ------------------------------------------------------------

_n8n_os() {
    case "$(uname -s)" in
        Darwin) printf 'darwin' ;;
        Linux)  printf 'linux'  ;;
        *)      return 1 ;;
    esac
}

_n8n_arch() {
    local os="$1" m
    m="$(uname -m)"

    # A bash running under Rosetta reports x86_64 on Apple Silicon.
    # Prefer the real hardware so we fetch the native arm64 Node.
    if [[ "$os" == "darwin" ]] \
        && [[ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" == "1" ]]; then
        printf 'arm64'
        return
    fi

    case "$m" in
        x86_64|amd64)  printf 'x64'   ;;
        arm64|aarch64) printf 'arm64' ;;
        *)             return 1 ;;
    esac
}

_n8n_sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

_n8n_port_in_use() {
    (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1
}

_n8n_pid_alive() {
    [[ -f "$N8N_PID_FILE" ]] || return 1
    local pid
    pid="$(cat "$N8N_PID_FILE" 2>/dev/null || true)"
    [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

# Accept Node >= N8N_NODE_MIN_MAJOR (n8n 2.x refuses anything older)
_n8n_node_acceptable() {
    local node="$1" v major
    [[ -x "$node" ]] || return 1
    v="$("$node" -v 2>/dev/null | sed 's/^v//')" || return 1
    major="${v%%.*}"
    [[ "$major" =~ ^[0-9]+$ ]] || return 1
    (( major >= N8N_NODE_MIN_MAJOR ))
}


# ------------------------------------------------------------
# Node.js: system if suitable, otherwise private copy
# ------------------------------------------------------------

_n8n_install_private_node() {

    local os arch base shasums file expected tmp actual

    os="$(_n8n_os)"   || { fail "Unsupported OS: $(uname -s)"; return 1; }
    arch="$(_n8n_arch "$os")" || { fail "Unsupported CPU: $(uname -m)"; return 1; }

    command -v curl >/dev/null 2>&1 || { fail "curl is required."; return 1; }
    command -v tar  >/dev/null 2>&1 || { fail "tar is required.";  return 1; }

    base="https://nodejs.org/dist/latest-v${N8N_NODE_MAJOR}.x"

    log "Resolving latest Node.js v${N8N_NODE_MAJOR} for ${os}-${arch}."

    shasums="$(curl -fsSL "${base}/SHASUMS256.txt")" \
        || { fail "Could not fetch ${base}/SHASUMS256.txt"; return 1; }

    file="$(printf '%s\n' "$shasums" \
        | awk -v suffix="-${os}-${arch}.tar.gz" '$2 ~ suffix"$" {print $2; exit}')"
    expected="$(printf '%s\n' "$shasums" \
        | awk -v f="$file" '$2 == f {print $1; exit}')"

    [[ -n "$file" && -n "$expected" ]] \
        || { fail "No Node.js build found for ${os}-${arch}."; return 1; }

    mkdir -p "$N8N_HOME"
    tmp="$(mktemp -d "${N8N_HOME}/node-dl.XXXXXX")"

    log "Downloading ${file}."
    curl -fSL --retry 3 -o "${tmp}/${file}" "${base}/${file}" \
        || { rm -rf "$tmp"; fail "Node.js download failed."; return 1; }

    actual="$(_n8n_sha256 "${tmp}/${file}")"
    if [[ "$actual" != "$expected" ]]; then
        rm -rf "$tmp"
        fail "Node.js checksum mismatch (expected ${expected}, got ${actual})."
        return 1
    fi

    rm -rf "$N8N_NODE_DIR"
    mkdir -p "$N8N_NODE_DIR"
    tar -xzf "${tmp}/${file}" -C "$N8N_NODE_DIR" --strip-components=1
    rm -rf "$tmp"

    success "Private Node.js installed: $("${N8N_NODE_DIR}/bin/node" -v)"
}

_n8n_ensure_node() {

    # 1. Previously installed private Node
    if _n8n_node_acceptable "${N8N_NODE_DIR}/bin/node"; then
        N8N_NODE_BIN_DIR="${N8N_NODE_DIR}/bin"
        return 0
    fi

    # 2. System Node, if in the supported range
    local sys_node
    sys_node="$(command -v node 2>/dev/null || true)"
    if [[ -n "$sys_node" ]] && _n8n_node_acceptable "$sys_node" \
        && command -v npm >/dev/null 2>&1; then
        N8N_NODE_BIN_DIR="$(dirname "$sys_node")"
        log "Using system Node.js $("$sys_node" -v)."
        return 0
    fi

    # 3. Download a private one
    log "No suitable Node.js found. Installing a private copy (no sudo)."
    _n8n_install_private_node || return 1
    N8N_NODE_BIN_DIR="${N8N_NODE_DIR}/bin"
}


# ------------------------------------------------------------
# Python for node-gyp
#
# n8n pulls in native modules (e.g. isolated-vm) that may need to be
# compiled. Its bundled node-gyp imports Python's `distutils`, which was
# removed in Python 3.12. If the system Python lacks it, build a tiny
# private venv with setuptools (which provides a distutils shim).
# ------------------------------------------------------------

_n8n_ensure_gyp_python() {

    N8N_GYP_PYTHON=""

    local py venv
    py="$(command -v python3 2>/dev/null || true)"

    if [[ -z "$py" ]]; then
        warning "python3 not found; native module builds may fail."
        return 0
    fi

    if "$py" -c 'import distutils' >/dev/null 2>&1; then
        return 0
    fi

    venv="${N8N_HOME}/pybuild"
    mkdir -p "$N8N_LOG_DIR"

    log "$("$py" -V 2>&1) has no distutils. Preparing a build helper environment."

    if [[ ! -x "${venv}/bin/python" ]]; then
        "$py" -m venv "$venv" >>"${N8N_LOG_DIR}/install.log" 2>&1 \
            || { fail "Could not create Python venv. On Ubuntu: sudo apt install python3-venv"; return 1; }
    fi

    "${venv}/bin/python" -m pip install --quiet setuptools \
        >>"${N8N_LOG_DIR}/install.log" 2>&1 \
        || { fail "pip install setuptools failed. See ${N8N_LOG_DIR}/install.log"; return 1; }

    "${venv}/bin/python" -c 'import distutils' >/dev/null 2>&1 \
        || { fail "distutils still unavailable after installing setuptools."; return 1; }

    N8N_GYP_PYTHON="${venv}/bin/python"
    success "Build helper Python ready."
}


# ------------------------------------------------------------
# Install n8n
# ------------------------------------------------------------

_n8n_install() {

    local want="${N8N_VERSION}"

    # Native modules are compiled for one Node major. If Node changed since
    # the last install, wipe node_modules so they get rebuilt correctly.
    local cur_major last_major
    cur_major="$(PATH="${N8N_NODE_BIN_DIR}:${PATH}" node -v | sed 's/^v//; s/\..*//')"
    last_major="$(cat "$N8N_NODE_STAMP" 2>/dev/null || true)"
    if [[ -d "${N8N_HOME}/node_modules" && "$last_major" != "$cur_major" ]]; then
        log "Node major changed (${last_major:-unknown} -> ${cur_major}). Reinstalling n8n for the new Node."
        rm -rf "${N8N_HOME}/node_modules" "${N8N_HOME}/package-lock.json"
    fi

    if [[ -x "$N8N_BIN" && "${N8N_UPDATE:-0}" != "1" ]]; then
        local installed
        installed="$(PATH="${N8N_NODE_BIN_DIR}:${PATH}" "$N8N_BIN" --version 2>/dev/null || true)"
        if [[ "$want" == "latest" || "$installed" == "$want" ]]; then
            log "n8n ${installed:-?} already installed (set N8N_UPDATE=1 to upgrade)."
            return 0
        fi
        log "Installed n8n ${installed:-?} differs from requested ${want}. Reinstalling."
    fi

    log "Installing n8n@${want} (first run can take a few minutes)."

    mkdir -p "$N8N_HOME" "$N8N_DATA_DIR" "$N8N_LOG_DIR"

    _n8n_ensure_gyp_python || return 1

    # Minimal package.json so npm treats this folder as a project
    if [[ ! -f "${N8N_HOME}/package.json" ]]; then
        printf '{ "name": "n8n-runtime", "private": true, "version": "1.0.0" }\n' \
            > "${N8N_HOME}/package.json"
    fi

    (
        export PATH="${N8N_NODE_BIN_DIR}:${PATH}"
        if [[ -n "$N8N_GYP_PYTHON" ]]; then
            export npm_config_python="$N8N_GYP_PYTHON"
        fi
        cd "$N8N_HOME"
        npm install "n8n@${want}" \
            --no-audit --no-fund --loglevel=error \
            >>"${N8N_LOG_DIR}/install.log" 2>&1
    ) || {
        fail "npm install n8n failed. See ${N8N_LOG_DIR}/install.log"
        return 1
    }

    [[ -x "$N8N_BIN" ]] || { fail "n8n binary missing after install."; return 1; }
    printf '%s\n' "$cur_major" > "$N8N_NODE_STAMP"

    success "n8n installed: $(PATH="${N8N_NODE_BIN_DIR}:${PATH}" "$N8N_BIN" --version 2>/dev/null || echo '?')"
}


# ------------------------------------------------------------
# Encryption key (protects stored credentials; must never change)
# ------------------------------------------------------------

_n8n_ensure_key() {
    if [[ ! -s "$N8N_KEY_FILE" ]]; then
        mkdir -p "$N8N_HOME"
        ( umask 077
          head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' > "$N8N_KEY_FILE" )
        log "Generated n8n encryption key."
    fi
}


# ------------------------------------------------------------
# Start / stop / status
# ------------------------------------------------------------

start_n8n() {

    if _n8n_pid_alive; then
        log "n8n already running (pid $(cat "$N8N_PID_FILE"))."
        return 0
    fi

    if _n8n_port_in_use "$N8N_PORT"; then
        fail "Port ${N8N_PORT} is already in use by another process. Set N8N_PORT to change it."
        return 1
    fi

    _n8n_ensure_key
    mkdir -p "$N8N_DATA_DIR" "$N8N_LOG_DIR"

    log "Starting n8n on http://127.0.0.1:${N8N_PORT}"

    (
        export PATH="${N8N_NODE_BIN_DIR}:${PATH}"
        export N8N_USER_FOLDER="$N8N_DATA_DIR"
        export N8N_ENCRYPTION_KEY
        N8N_ENCRYPTION_KEY="$(cat "$N8N_KEY_FILE")"

        # Network: bind to localhost only. Your Spring Boot app (or a
        # reverse proxy) is the only thing that should reach n8n.
        export N8N_LISTEN_ADDRESS="127.0.0.1"
        export N8N_PORT="$N8N_PORT"
        export N8N_PROTOCOL="http"
        export N8N_HOST="127.0.0.1"
        export N8N_EDITOR_BASE_URL="${N8N_EDITOR_BASE_URL:-http://127.0.0.1:${N8N_PORT}}"
        export WEBHOOK_URL="${WEBHOOK_URL:-http://127.0.0.1:${N8N_PORT}/}"
        export N8N_SECURE_COOKIE="${N8N_SECURE_COOKIE:-false}"   # plain http locally

        # Quiet, offline-friendly defaults
        export N8N_DIAGNOSTICS_ENABLED="false"
        export N8N_VERSION_NOTIFICATIONS_ENABLED="false"
        export N8N_PERSONALIZATION_ENABLED="false"
        export N8N_HIRING_BANNER_ENABLED="false"
        export N8N_TEMPLATES_ENABLED="${N8N_TEMPLATES_ENABLED:-true}"

        # Public REST API (create the API key from the n8n UI: Settings > n8n API)
        export N8N_PUBLIC_API_DISABLED="false"

        # Task runners for Code nodes (recommended in recent n8n versions)
        export N8N_RUNNERS_ENABLED="true"

        # Allow embedding in iframe (needed for the LLM Gateway UI integration)
        # We allow self and any local origin for flexibility in local development.
        export N8N_CONTENT_SECURITY_POLICY="frame-ancestors 'self' http://localhost:* http://127.0.0.1:*"

        export N8N_RELEASE_TYPE="stable"
        export GENERIC_TIMEZONE="${GENERIC_TIMEZONE:-UTC}"

        # Keep the DB small on a laptop: prune old executions
        export EXECUTIONS_DATA_PRUNE="true"
        export EXECUTIONS_DATA_MAX_AGE="${EXECUTIONS_DATA_MAX_AGE:-168}"   # hours

        cd "$N8N_HOME"
        nohup "$N8N_BIN" start >>"$N8N_LOG_FILE" 2>&1 &
        echo $! > "$N8N_PID_FILE"
    )

    # Wait for readiness
    local i
    for (( i = 0; i < N8N_START_TIMEOUT; i++ )); do
        if ! _n8n_pid_alive; then
            fail "n8n exited during startup. See ${N8N_LOG_FILE}"
            return 1
        fi
        if curl -fsS -o /dev/null "http://127.0.0.1:${N8N_PORT}/healthz" 2>/dev/null; then
            success "n8n is up: http://127.0.0.1:${N8N_PORT}"
            return 0
        fi
        sleep 1
    done

    fail "n8n did not become ready within ${N8N_START_TIMEOUT}s. See ${N8N_LOG_FILE}"
    return 1
}

stop_n8n() {

    if ! _n8n_pid_alive; then
        log "n8n is not running."
        rm -f "$N8N_PID_FILE"
        return 0
    fi

    local pid i
    pid="$(cat "$N8N_PID_FILE")"
    log "Stopping n8n (pid ${pid})."
    kill "$pid" 2>/dev/null || true

    for (( i = 0; i < 20; i++ )); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
    done

    if kill -0 "$pid" 2>/dev/null; then
        warning "n8n did not stop gracefully. Sending SIGKILL."
        kill -9 "$pid" 2>/dev/null || true
    fi

    rm -f "$N8N_PID_FILE"
    success "n8n stopped."
}

status_n8n() {
    if _n8n_pid_alive; then
        info "n8n running (pid $(cat "$N8N_PID_FILE")) on http://127.0.0.1:${N8N_PORT}"
    else
        info "n8n not running."
        return 1
    fi
}


# ------------------------------------------------------------
# Entry point used by bootstrap.sh
# ------------------------------------------------------------

prepare_n8n() {

    log "Preparing n8n (native)."

    _n8n_ensure_node || return 1
    _n8n_install     || return 1
    start_n8n        || return 1

    info "n8n editor : http://127.0.0.1:${N8N_PORT}"
    info "n8n data   : ${N8N_DATA_DIR}"
    info "n8n logs   : ${N8N_LOG_FILE}"
}


# ------------------------------------------------------------
# Standalone CLI (only when executed, not when sourced)
# ------------------------------------------------------------

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then

    set -Eeuo pipefail

    case "${1:-start}" in
        start)   prepare_n8n ;;
        stop)    stop_n8n ;;
        restart) stop_n8n; prepare_n8n ;;
        status)  status_n8n ;;
        logs)    tail -n 100 -f "$N8N_LOG_FILE" ;;
        *)
            printf 'Usage: %s [start|stop|restart|status|logs]\n' "$0" >&2
            exit 2
            ;;
    esac
fi