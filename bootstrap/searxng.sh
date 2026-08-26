#!/usr/bin/env bash

set -Eeuo pipefail


# ============================================================
# SearXNG
# ============================================================

SEARXNG_REPOSITORY="https://github.com/searxng/searxng.git"

SEARXNG_PORT="${SEARXNG_PORT:-8888}"

SEARXNG_SRC="${SEARXNG_DIR}/searxng-src"
SEARXNG_PYENV="${SEARXNG_DIR}/searx-pyenv"
SEARXNG_SETTINGS="${SEARXNG_DIR}/settings.yml"

SEARXNG_LOG="${SEARXNG_DIR}/searxng.log"
SEARXNG_PID="${SEARXNG_DIR}/searxng.pid"


# ============================================================
# Main
# ============================================================

prepare_searxng() {

    log "Preparing SearXNG..."

    check_searxng_prerequisites

    clone_or_update_searxng

    create_searxng_virtualenv

    install_searxng

    create_searxng_settings

    start_searxng

    success "SearXNG is ready."
}


# ============================================================
# Prerequisites
# ============================================================

check_searxng_prerequisites() {

    log "Checking SearXNG prerequisites..."


    command_exists python3 || \
        fail "python3 is required for SearXNG."


    command_exists git || \
        fail "git is required for SearXNG."


    if ! python3 -m venv --help >/dev/null 2>&1; then

        fail \
            "Python virtual environment support is unavailable."

    fi


    if ! python3 -c \
        'import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)'
    then

        fail \
            "SearXNG requires Python 3.10 or newer."

    fi


    info \
        "Python: $(python3 --version 2>&1)"


    success \
        "SearXNG prerequisites are available."
}


# ============================================================
# Clone / update
# ============================================================

clone_or_update_searxng() {

    if [[ ! -d "${SEARXNG_SRC}/.git" ]]; then

        log \
            "SearXNG source not found. Cloning..."


        mkdir -p "${SEARXNG_DIR}"


        run git clone \
            "${SEARXNG_REPOSITORY}" \
            "${SEARXNG_SRC}"


        success \
            "SearXNG source cloned."


        return
    fi


    log \
        "SearXNG source already exists. Updating..."


    if ! git -C "${SEARXNG_SRC}" diff --quiet; then

        warning \
            "SearXNG source contains local modifications."

        warning \
            "Skipping git pull."

        return
    fi


    if ! git -C "${SEARXNG_SRC}" diff --cached --quiet; then

        warning \
            "SearXNG source contains staged changes."

        warning \
            "Skipping git pull."

        return
    fi


    run git \
        -C "${SEARXNG_SRC}" \
        pull \
        --ff-only


    success \
        "SearXNG source updated."
}


# ============================================================
# Virtual environment
# ============================================================

create_searxng_virtualenv() {

    log "Preparing SearXNG Python environment..."


    # --------------------------------------------------------
    # If the venv does not exist, create it.
    # --------------------------------------------------------

    if [[ ! -x "${SEARXNG_PYENV}/bin/python" ]]; then

        info "Creating Python virtual environment..."

        run python3 \
            -m venv \
            "${SEARXNG_PYENV}"

    fi


    # --------------------------------------------------------
    # Some environments can create a venv without pip.
    #
    # Check for pip explicitly rather than assuming that the
    # existence of bin/python means the venv is complete.
    # --------------------------------------------------------

    if ! "${SEARXNG_PYENV}/bin/python" \
        -m pip --version \
        >/dev/null 2>&1
    then

        warning \
            "SearXNG virtual environment does not contain pip."


        # ----------------------------------------------------
        # Try ensurepip first.
        # ----------------------------------------------------

        if "${SEARXNG_PYENV}/bin/python" \
            -m ensurepip \
            --upgrade \
            >/dev/null 2>&1
        then

            success \
                "pip installed using ensurepip."

        else

            # ------------------------------------------------
            # If ensurepip is unavailable, use the system
            # pip bootstrap script.
            # ------------------------------------------------

            warning \
                "ensurepip unavailable. Bootstrapping pip manually."


            local get_pip

            get_pip="${SEARXNG_DIR}/get-pip.py"


            if [[ ! -f "${get_pip}" ]]; then

                run curl \
                    -L \
                    --fail \
                    --retry 5 \
                    -o "${get_pip}" \
                    "https://bootstrap.pypa.io/get-pip.py"

            fi


            run "${SEARXNG_PYENV}/bin/python" \
                "${get_pip}"


            rm -f "${get_pip}"

        fi

    fi


    # --------------------------------------------------------
    # Verify pip now exists.
    # --------------------------------------------------------

    if ! "${SEARXNG_PYENV}/bin/python" \
        -m pip --version \
        >/dev/null 2>&1
    then

        fail \
            "Unable to install pip into the SearXNG Python environment."

    fi


    success \
        "SearXNG virtual environment is ready."
}


# ============================================================
# Install
# ============================================================

install_searxng() {

    log \
        "Installing/updating SearXNG..."


    local python="${SEARXNG_PYENV}/bin/python"


    [[ -x "${python}" ]] || \
        fail \
            "SearXNG Python executable not found."


    run "${python}" \
        -m pip install \
        --upgrade \
        pip \
        setuptools \
        wheel


    run "${python}" \
        -m pip install \
        --upgrade \
        pyyaml \
        msgspec \
        typing-extensions \
        pybind11


    run "${python}" \
        -m pip install \
        --use-pep517 \
        --no-build-isolation \
        -e "${SEARXNG_SRC}"


    success \
        "SearXNG installation completed."
}


# ============================================================
# Settings
# ============================================================

create_searxng_settings() {

    log \
        "Preparing SearXNG settings..."


    if [[ -f "${SEARXNG_SETTINGS}" ]]; then

        success \
            "Existing SearXNG settings preserved."

        return
    fi


    local template="${SEARXNG_SRC}/utils/templates/etc/searxng/settings.yml"


    if [[ ! -f "${template}" ]]; then

        fail \
            "SearXNG settings template not found: ${template}"

    fi


    mkdir -p "${SEARXNG_DIR}"


    run cp \
        "${template}" \
        "${SEARXNG_SETTINGS}"


    success \
        "SearXNG settings created."
}


# ============================================================
# Secret
# ============================================================

generate_searxng_secret() {

    if command_exists openssl; then

        openssl rand -hex 32

    else

        "${SEARXNG_PYENV}/bin/python" \
            -c \
            'import secrets; print(secrets.token_hex(32))'

    fi
}


# ============================================================
# Start
# ============================================================

start_searxng() {

    log \
        "Starting SearXNG..."


    local python="${SEARXNG_PYENV}/bin/python"


    [[ -x "${python}" ]] || \
        fail \
            "SearXNG Python environment is not available."


    # --------------------------------------------------------
    # Already running?
    # --------------------------------------------------------

    if curl \
        --silent \
        --fail \
        --max-time 2 \
        "http://127.0.0.1:${SEARXNG_PORT}/" \
        >/dev/null 2>&1
    then

        success \
            "SearXNG is already running on port ${SEARXNG_PORT}."

        return
    fi


    mkdir -p "${SEARXNG_DIR}"


    local secret

    secret="$(generate_searxng_secret)"


    (
        cd "${SEARXNG_SRC}"


        export SEARXNG_SETTINGS_PATH="${SEARXNG_SETTINGS}"

        export SEARXNG_PORT="${SEARXNG_PORT}"

        export SEARXNG_BIND_ADDRESS="127.0.0.1"

        export SEARXNG_SECRET="${secret}"


        nohup "${python}" \
            -m searx.webapp \
            > "${SEARXNG_LOG}" \
            2>&1 &


        echo $! > "${SEARXNG_PID}"
    )


    wait_for_searxng
}


# ============================================================
# Wait
# ============================================================

wait_for_searxng() {

    log \
        "Waiting for SearXNG..."


    local attempts=0
    local max_attempts=60


    while (( attempts < max_attempts )); do


        if curl \
            --silent \
            --fail \
            --max-time 2 \
            "http://127.0.0.1:${SEARXNG_PORT}/" \
            >/dev/null 2>&1
        then

            success \
                "SearXNG is running on port ${SEARXNG_PORT}."

            return
        fi


        if [[ -f "${SEARXNG_PID}" ]]; then

            local pid

            pid="$(cat "${SEARXNG_PID}")"


            if ! kill -0 "${pid}" 2>/dev/null; then

                warning \
                    "SearXNG process exited unexpectedly."


                if [[ -f "${SEARXNG_LOG}" ]]; then

                    warning \
                        "Last SearXNG log output:"

                    tail -n 40 \
                        "${SEARXNG_LOG}"

                fi


                fail \
                    "SearXNG failed to start."
            fi
        fi


        attempts=$((attempts + 1))


        sleep 2

    done


    warning \
        "SearXNG did not become ready within the expected time."


    if [[ -f "${SEARXNG_LOG}" ]]; then

        warning \
            "Last SearXNG log output:"

        tail -n 40 \
            "${SEARXNG_LOG}"

    fi


    fail \
        "SearXNG did not become ready."
}