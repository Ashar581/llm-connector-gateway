#!/usr/bin/env bash

set -Eeuo pipefail


# ============================================================
# Common bootstrap utilities
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

RUNTIME_DIR="${PROJECT_ROOT}/runtime"

LLAMA_DIR="${RUNTIME_DIR}/llama.cpp"
LLAMA_MODELS_DIR="${LLAMA_DIR}/models"

SEARXNG_DIR="${RUNTIME_DIR}/searxng"

BOOTSTRAP_STATE_DIR="${RUNTIME_DIR}/.bootstrap"
PLATFORM_STATE_FILE="${BOOTSTRAP_STATE_DIR}/platform.properties"


mkdir -p "${RUNTIME_DIR}"
mkdir -p "${BOOTSTRAP_STATE_DIR}"


export PROJECT_ROOT
export RUNTIME_DIR

export LLAMA_DIR
export LLAMA_MODELS_DIR

export SEARXNG_DIR

export BOOTSTRAP_STATE_DIR
export PLATFORM_STATE_FILE


# ============================================================
# Logging
# ============================================================

log() {
    printf '\n\033[1;36m[BOOTSTRAP]\033[0m %s\n' "$1"
}


info() {
    printf '  %s\n' "$1"
}


success() {
    printf '  \033[1;32m✓\033[0m %s\n' "$1"
}


warning() {
    printf '  \033[1;33m!\033[0m %s\n' "$1"
}


error() {
    printf '  \033[1;31m✗\033[0m %s\n' "$1" >&2
}


fail() {
    error "$1"
    exit 1
}


# ============================================================
# Command helpers
# ============================================================

command_exists() {
    command -v "$1" >/dev/null 2>&1
}


require_command() {

    if ! command_exists "$1"; then
        fail "Required command not found: $1"
    fi
}


run() {

    printf '\n  \$'

    for arg in "$@"; do
        printf ' %q' "$arg"
    done

    printf '\n'

    "$@"
}


run_quiet() {
    "$@" >/dev/null 2>&1
}


# ============================================================
# System helpers
# ============================================================

is_root() {
    [[ "$(id -u)" -eq 0 ]]
}


sudo_available() {
    command_exists sudo
}


# ============================================================
# State
# ============================================================

write_state() {

    local key="$1"
    local value="$2"

    mkdir -p "${BOOTSTRAP_STATE_DIR}"

    touch "${PLATFORM_STATE_FILE}"


    if grep -q "^${key}=" "${PLATFORM_STATE_FILE}" 2>/dev/null; then

        sed -i.bak \
            "s|^${key}=.*|${key}=${value}|" \
            "${PLATFORM_STATE_FILE}"

        rm -f "${PLATFORM_STATE_FILE}.bak"

    else

        printf '%s=%s\n' \
            "${key}" \
            "${value}" \
            >> "${PLATFORM_STATE_FILE}"

    fi
}


read_state() {

    local key="$1"


    if [[ ! -f "${PLATFORM_STATE_FILE}" ]]; then
        return 0
    fi


    grep "^${key}=" "${PLATFORM_STATE_FILE}" \
        | head -n1 \
        | cut -d '=' -f2-
}


# ============================================================
# Error handling
# ============================================================

cleanup_on_error() {

    local exit_code=$?


    if [[ "${exit_code}" -ne 0 ]]; then

        printf '\n'

        error "Bootstrap failed."
        error "Exit code: ${exit_code}"

        printf '\n'

    fi
}


trap cleanup_on_error EXIT