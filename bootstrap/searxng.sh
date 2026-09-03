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


    command_exists curl || \
        fail "curl is required for SearXNG."


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

    log \
        "Preparing SearXNG Python environment..."


    mkdir -p "${SEARXNG_DIR}"


    # --------------------------------------------------------
    # If an existing venv is present, make sure it actually
    # contains a working Python + pip installation.
    # --------------------------------------------------------

    if [[ -x "${SEARXNG_PYENV}/bin/python" ]] \
        && "${SEARXNG_PYENV}/bin/python" \
            -m pip --version \
            >/dev/null 2>&1
    then

        success \
            "SearXNG virtual environment already exists."

        return

    fi


    # --------------------------------------------------------
    # Existing venv is incomplete/broken.
    # --------------------------------------------------------

    if [[ -d "${SEARXNG_PYENV}" ]]; then

        warning \
            "Existing SearXNG virtual environment is incomplete."

        info \
            "Recreating virtual environment..."

        rm -rf "${SEARXNG_PYENV}"

    fi


    # --------------------------------------------------------
    # First attempt: create the venv normally.
    # --------------------------------------------------------

    if python3 \
        -m venv \
        "${SEARXNG_PYENV}"
    then

        success \
            "SearXNG virtual environment created."

    else

        # ----------------------------------------------------
        # Normal venv creation failed.
        #
        # Try installing the OS venv package on Debian/Ubuntu.
        # ----------------------------------------------------

        warning \
            "Python virtual environment creation failed."


        if command_exists apt-get; then

            info \
                "Installing Python venv support..."


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

                fail \
                    "Python venv is unavailable and sudo is not available."

            fi


            rm -rf "${SEARXNG_PYENV}"


            # ------------------------------------------------
            # Try again.
            # ------------------------------------------------

            if ! python3 \
                -m venv \
                "${SEARXNG_PYENV}"
            then

                fail \
                    "Unable to create the SearXNG Python virtual environment."

            fi


            success \
                "SearXNG virtual environment created."

        else

            fail \
                "Unable to create Python virtual environment. " \
                "Install Python venv support for this operating system."

        fi

    fi


    # --------------------------------------------------------
    # Verify Python exists.
    # --------------------------------------------------------

    if [[ ! -x "${SEARXNG_PYENV}/bin/python" ]]; then

        fail \
            "SearXNG Python executable was not created."

    fi


    # --------------------------------------------------------
    # Verify pip.
    # --------------------------------------------------------

    if ! "${SEARXNG_PYENV}/bin/python" \
        -m pip --version \
        >/dev/null 2>&1
    then

        warning \
            "SearXNG virtual environment was created without pip."


        # ----------------------------------------------------
        # Try ensurepip.
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
            # Fallback: get-pip.py
            # ------------------------------------------------

            warning \
                "ensurepip is unavailable."

            info \
                "Bootstrapping pip..."


            local get_pip

            get_pip="${SEARXNG_DIR}/get-pip.py"


            run curl \
                -L \
                --fail \
                --retry 5 \
                -o "${get_pip}" \
                "https://bootstrap.pypa.io/get-pip.py"


            run "${SEARXNG_PYENV}/bin/python" \
                "${get_pip}"


            rm -f "${get_pip}"

        fi

    fi


    # --------------------------------------------------------
    # Final pip verification.
    # --------------------------------------------------------

    if ! "${SEARXNG_PYENV}/bin/python" \
        -m pip --version \
        >/dev/null 2>&1
    then

        fail \
            "pip could not be installed into the SearXNG virtual environment."

    fi


    success \
        "SearXNG Python environment is ready."
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

    local template="${SEARXNG_SRC}/utils/templates/etc/searxng/settings.yml"

    # --------------------------------------------------------
    # Create settings file if it does not exist.
    # --------------------------------------------------------

    if [[ ! -f "${SEARXNG_SETTINGS}" ]]; then

        if [[ ! -f "${template}" ]]; then

            fail \
                "SearXNG settings template not found: ${template}"

        fi

        mkdir -p "${SEARXNG_DIR}"

        run cp \
            "${template}" \
            "${SEARXNG_SETTINGS}"

        info \
            "SearXNG settings file created."

    else

        info \
            "Existing SearXNG settings found."

    fi

    # --------------------------------------------------------
    # Ensure JSON output is enabled.
    #
    # This only changes search.formats.
    # All other user settings are preserved.
    # --------------------------------------------------------

    local python="${SEARXNG_PYENV}/bin/python"

    if [[ ! -x "${python}" ]]; then

        fail \
            "SearXNG Python executable not found."

    fi

    run "${python}" -c '
import yaml

settings_path = "'"${SEARXNG_SETTINGS}"'"

with open(settings_path, "r") as file:
    settings = yaml.safe_load(file) or {}

search = settings.setdefault("search", {})

formats = search.get("formats")

if formats is None:
    formats = []

elif not isinstance(formats, list):
    formats = [formats]

if "html" not in formats:
    formats.insert(0, "html")

if "json" not in formats:
    formats.append("json")

search["formats"] = formats

with open(settings_path, "w") as file:
    yaml.safe_dump(
        settings,
        file,
        sort_keys=False,
        default_flow_style=False
    )
'

    success \
        "SearXNG settings ready. JSON API enabled."
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