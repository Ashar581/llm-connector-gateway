#!/usr/bin/env bash

set -Eeuo pipefail


# ============================================================
# LLM Connector Gateway - SearXNG
# ============================================================

# This file is sourced by bootstrap.sh.
#
# Expected variables/functions from common.sh:
#
#   PROJECT_ROOT
#   RUNTIME_DIR
#   SEARXNG_DIR
#   log
#   info
#   success
#   warning
#   error
#   fail
#   run
#   command_exists
#   is_root
#   sudo_available
#
# ============================================================


# ============================================================
# Configuration
# ============================================================

SEARXNG_SOURCE_DIR="${SEARXNG_DIR}/searxng-src"

SEARXNG_PYENV="${SEARXNG_DIR}/searx-pyenv"

SEARXNG_SETTINGS_DIR="${SEARXNG_DIR}/config"

SEARXNG_SETTINGS_FILE="${SEARXNG_SETTINGS_DIR}/settings.yml"

SEARXNG_LOG_FILE="${SEARXNG_DIR}/searxng.log"

SEARXNG_PID_FILE="${SEARXNG_DIR}/searxng.pid"

SEARXNG_HOST="${SEARXNG_HOST:-127.0.0.1}"

SEARXNG_PORT="${SEARXNG_PORT:-8080}"

SEARXNG_BASE_URL="http://${SEARXNG_HOST}:${SEARXNG_PORT}"

SEARXNG_REPOSITORY="https://github.com/searxng/searxng.git"


export SEARXNG_SOURCE_DIR
export SEARXNG_PYENV
export SEARXNG_SETTINGS_DIR
export SEARXNG_SETTINGS_FILE
export SEARXNG_LOG_FILE
export SEARXNG_PID_FILE
export SEARXNG_HOST
export SEARXNG_PORT
export SEARXNG_BASE_URL


# ============================================================
# Check SearXNG prerequisites
# ============================================================

check_searxng_prerequisites() {

    log "Checking SearXNG prerequisites..."


    # --------------------------------------------------------
    # Python
    # --------------------------------------------------------

    require_command python3

    info "Python: $(python3 --version)"


    # --------------------------------------------------------
    # Git
    # --------------------------------------------------------

    require_command git


    # --------------------------------------------------------
    # Curl
    # --------------------------------------------------------

    require_command curl


    # --------------------------------------------------------
    # Check whether Python can actually create a venv.
    # --------------------------------------------------------

    local test_venv

    test_venv="${SEARXNG_DIR}/.venv-test"


    mkdir -p "${SEARXNG_DIR}"


    rm -rf "${test_venv}"


    if python3 -m venv "${test_venv}" >/dev/null 2>&1; then

        success "Python virtual environment support is available."

        rm -rf "${test_venv}"

        return 0
    fi


    warning "python3 -m venv is unavailable."


    # --------------------------------------------------------
    # Debian / Ubuntu / Google Colab
    # --------------------------------------------------------

    if command_exists apt-get; then

        info "Attempting to install Python virtual environment support..."


        if is_root; then

            apt-get update -qq

            apt-get install \
                -y \
                python3-venv \
                python3-pip


        elif sudo_available; then

            sudo apt-get update -qq

            sudo apt-get install \
                -y \
                python3-venv \
                python3-pip


        else

            warning \
                "apt-get is available but root/sudo access is unavailable."

        fi

    fi


    # --------------------------------------------------------
    # Try again.
    # --------------------------------------------------------

    rm -rf "${test_venv}"


    if python3 -m venv "${test_venv}" >/dev/null 2>&1; then

        success "Python virtual environment support is available."

        rm -rf "${test_venv}"

        return 0
    fi


    # --------------------------------------------------------
    # Nothing worked.
    # --------------------------------------------------------

    rm -rf "${test_venv}"


    fail \
        "Python virtual environment support is unavailable. " \
        "Install the venv package for your Python installation."


}


# ============================================================
# Clone or update SearXNG
# ============================================================

prepare_searxng_source() {

    log "Preparing SearXNG source..."


    mkdir -p "${SEARXNG_DIR}"


    # --------------------------------------------------------
    # First installation
    # --------------------------------------------------------

    if [[ ! -d "${SEARXNG_SOURCE_DIR}/.git" ]]; then

        if [[ -e "${SEARXNG_SOURCE_DIR}" ]]; then

            warning \
                "SearXNG source directory exists but is not a Git repository."

            info "Removing incomplete SearXNG source..."

            rm -rf "${SEARXNG_SOURCE_DIR}"

        fi


        run git clone \
            "${SEARXNG_REPOSITORY}" \
            "${SEARXNG_SOURCE_DIR}"


        success "SearXNG source downloaded."

        return 0

    fi


    # --------------------------------------------------------
    # Existing installation
    # --------------------------------------------------------

    info "SearXNG source already exists. Updating..."


    run git \
        -C "${SEARXNG_SOURCE_DIR}" \
        pull \
        --ff-only


    success "SearXNG source updated."

}


# ============================================================
# Create / repair Python virtual environment
# ============================================================

prepare_searxng_virtualenv() {

    log "Preparing SearXNG Python environment..."


    mkdir -p "${SEARXNG_DIR}"


    # --------------------------------------------------------
    # Create the environment if Python executable is missing.
    # --------------------------------------------------------

    if [[ ! -x "${SEARXNG_PYENV}/bin/python" ]]; then

        info "Creating SearXNG virtual environment..."


        rm -rf "${SEARXNG_PYENV}"


        run python3 \
            -m venv \
            "${SEARXNG_PYENV}"

    else

        success \
            "SearXNG virtual environment already exists."

    fi


    # --------------------------------------------------------
    # Check whether pip exists.
    # --------------------------------------------------------

    if "${SEARXNG_PYENV}/bin/python" \
        -m pip --version \
        >/dev/null 2>&1
    then

        success "pip is available in the SearXNG environment."

        return 0

    fi


    warning \
        "SearXNG virtual environment does not contain pip."


    # ========================================================
    # Attempt 1: ensurepip
    # ========================================================

    if "${SEARXNG_PYENV}/bin/python" \
        -m ensurepip \
        --upgrade \
        >/dev/null 2>&1
    then

        success "pip installed using ensurepip."

    else

        warning \
            "ensurepip is unavailable."


        # ====================================================
        # Attempt 2: get-pip.py
        # ====================================================

        local get_pip

        get_pip="${SEARXNG_DIR}/get-pip.py"


        info "Bootstrapping pip using get-pip.py..."


        run curl \
            --fail \
            --location \
            --retry 5 \
            --retry-delay 2 \
            --output "${get_pip}" \
            "https://bootstrap.pypa.io/get-pip.py"


        run "${SEARXNG_PYENV}/bin/python" \
            "${get_pip}"


        rm -f "${get_pip}"


        success "pip installed using get-pip.py."

    fi


    # ========================================================
    # Final verification
    # ========================================================

    if ! "${SEARXNG_PYENV}/bin/python" \
        -m pip --version \
        >/dev/null 2>&1
    then

        fail \
            "Unable to install pip into the SearXNG Python environment."

    fi


    success "SearXNG Python environment is ready."

}


# ============================================================
# Install SearXNG
# ============================================================

install_searxng() {

    log "Installing/updating SearXNG..."


    local python_bin

    python_bin="${SEARXNG_PYENV}/bin/python"


    # --------------------------------------------------------
    # Upgrade packaging tools
    # --------------------------------------------------------

    run "${python_bin}" \
        -m pip install \
        --upgrade \
        pip \
        setuptools \
        wheel


    # --------------------------------------------------------
    # Install SearXNG itself.
    #
    # Installing from the cloned source means that the exact
    # source we downloaded is what gets installed.
    # --------------------------------------------------------

    run "${python_bin}" \
        -m pip install \
        --upgrade \
        "${SEARXNG_SOURCE_DIR}"


    success "SearXNG installed."

}


# ============================================================
# Create SearXNG configuration
# ============================================================

prepare_searxng_configuration() {

    log "Preparing SearXNG configuration..."


    mkdir -p "${SEARXNG_SETTINGS_DIR}"


    # --------------------------------------------------------
    # Do not overwrite an existing configuration.
    # --------------------------------------------------------

    if [[ -f "${SEARXNG_SETTINGS_FILE}" ]]; then

        success \
            "SearXNG settings already exist."

        return 0

    fi


    # --------------------------------------------------------
    # Generate a random secret key.
    # --------------------------------------------------------

    local secret_key


    if command_exists openssl; then

        secret_key="$(
            openssl rand -hex 32
        )"

    else

        secret_key="$(
            python3 -c \
                'import secrets; print(secrets.token_hex(32))'
        )"

    fi


    # --------------------------------------------------------
    # Write settings.yml
    # --------------------------------------------------------

    cat > "${SEARXNG_SETTINGS_FILE}" <<EOF
use_default_settings: true

general:
  debug: false
  instance_name: "LLM Connector Gateway SearXNG"

search:
  safe_search: 0
  autocomplete: ""
  default_lang: "en"
  formats:
    - html
    - json

server:
  bind_address: "${SEARXNG_HOST}"
  port: ${SEARXNG_PORT}
  secret_key: "${secret_key}"
  base_url: "${SEARXNG_BASE_URL}/"
  image_proxy: false

ui:
  static_use_hash: true

redis:
  url: false

engines:
  - name: google
    engine: google
    shortcut: go
    disabled: false

  - name: bing
    engine: bing
    shortcut: bi
    disabled: false

  - name: duckduckgo
    engine: duckduckgo
    shortcut: ddg
    disabled: false

  - name: brave
    engine: brave
    shortcut: br
    disabled: false
EOF


    success \
        "SearXNG settings created."

}


# ============================================================
# Check whether SearXNG is already running
# ============================================================

searxng_is_running() {

    # --------------------------------------------------------
    # PID file
    # --------------------------------------------------------

    if [[ -f "${SEARXNG_PID_FILE}" ]]; then

        local pid

        pid="$(
            cat "${SEARXNG_PID_FILE}" 2>/dev/null || true
        )


        if [[ -n "${pid}" ]] \
            && kill -0 "${pid}" 2>/dev/null
        then

            return 0

        fi


        rm -f "${SEARXNG_PID_FILE}"

    fi


    # --------------------------------------------------------
    # Port check
    # --------------------------------------------------------

    if command_exists curl; then

        if curl \
            --silent \
            --show-error \
            --max-time 2 \
            "${SEARXNG_BASE_URL}/" \
            >/dev/null 2>&1
        then

            return 0

        fi

    fi


    return 1

}


# ============================================================
# Start SearXNG
# ============================================================

start_searxng() {

    log "Starting SearXNG..."


    if searxng_is_running; then

        success \
            "SearXNG is already running at ${SEARXNG_BASE_URL}"

        return 0

    fi


    local python_bin

    python_bin="${SEARXNG_PYENV}/bin/python"


    # --------------------------------------------------------
    # Remove stale PID file.
    # --------------------------------------------------------

    rm -f "${SEARXNG_PID_FILE}"


    # --------------------------------------------------------
    # Start SearXNG in the background.
    #
    # stdout/stderr go into a runtime log file so that the
    # Spring Boot process does not get blocked by SearXNG.
    # --------------------------------------------------------

    info \
        "Starting SearXNG in background."


    (
        cd "${SEARXNG_SOURCE_DIR}"


        exec "${python_bin}" \
            -m searx.webapp \
            >> "${SEARXNG_LOG_FILE}" \
            2>&1

    ) &


    local pid=$!


    printf '%s\n' "${pid}" \
        > "${SEARXNG_PID_FILE}"


    success \
        "SearXNG process started with PID ${pid}."

}


# ============================================================
# Wait for SearXNG
# ============================================================

wait_for_searxng() {

    log "Waiting for SearXNG..."


    local max_attempts=60

    local attempt=1


    while [[ "${attempt}" -le "${max_attempts}" ]]; do


        # ----------------------------------------------------
        # Check whether the process died.
        # ----------------------------------------------------

        if [[ -f "${SEARXNG_PID_FILE}" ]]; then

            local pid

            pid="$(
                cat "${SEARXNG_PID_FILE}" \
                    2>/dev/null \
                    || true
            )


            if [[ -n "${pid}" ]] \
                && ! kill -0 "${pid}" 2>/dev/null
            then

                error \
                    "SearXNG process stopped unexpectedly."


                if [[ -f "${SEARXNG_LOG_FILE}" ]]; then

                    error "Last SearXNG log output:"

                    tail -n 50 \
                        "${SEARXNG_LOG_FILE}" \
                        >&2

                fi


                fail \
                    "SearXNG failed to start."

            fi

        fi


        # ----------------------------------------------------
        # HTTP readiness check.
        # ----------------------------------------------------

        if curl \
            --silent \
            --show-error \
            --max-time 2 \
            "${SEARXNG_BASE_URL}/" \
            >/dev/null 2>&1
        then

            success \
                "SearXNG is ready at ${SEARXNG_BASE_URL}"

            return 0

        fi


        sleep 1


        attempt=$((attempt + 1))

    done


    # --------------------------------------------------------
    # Timeout
    # --------------------------------------------------------

    if [[ -f "${SEARXNG_LOG_FILE}" ]]; then

        error "Last SearXNG log output:"

        tail -n 50 \
            "${SEARXNG_LOG_FILE}" \
            >&2

    fi


    fail \
        "SearXNG did not become ready."

}


# ============================================================
# Main SearXNG preparation function
# ============================================================

prepare_searxng() {

    log "Preparing SearXNG..."


    # --------------------------------------------------------
    # 1. Prerequisites
    # --------------------------------------------------------

    check_searxng_prerequisites


    # --------------------------------------------------------
    # 2. Source
    # --------------------------------------------------------

    prepare_searxng_source


    # --------------------------------------------------------
    # 3. Python environment
    # --------------------------------------------------------

    prepare_searxng_virtualenv


    # --------------------------------------------------------
    # 4. Install/update
    # --------------------------------------------------------

    install_searxng


    # --------------------------------------------------------
    # 5. Configuration
    # --------------------------------------------------------

    prepare_searxng_configuration


    # --------------------------------------------------------
    # 6. Start
    # --------------------------------------------------------

    start_searxng


    # --------------------------------------------------------
    # 7. Wait
    # --------------------------------------------------------

    wait_for_searxng


    # --------------------------------------------------------
    # Done
    # --------------------------------------------------------

    success \
        "SearXNG preparation completed."


}


# ============================================================
# Export
# ============================================================

export -f check_searxng_prerequisites
export -f prepare_searxng_source
export -f prepare_searxng_virtualenv
export -f install_searxng
export -f prepare_searxng_configuration
export -f searxng_is_running
export -f start_searxng
export -f wait_for_searxng
export -f prepare_searxng