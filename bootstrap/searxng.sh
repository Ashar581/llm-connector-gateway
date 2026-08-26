#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# SearXNG - Native Python Installation
# ============================================================

SEARXNG_REPOSITORY="https://github.com/searxng/searxng.git"

SEARXNG_PORT="${SEARXNG_PORT:-8888}"

SEARXNG_SRC="${SEARXNG_DIR}/searxng-src"
SEARXNG_PYENV="${SEARXNG_DIR}/searx-pyenv"
SEARXNG_SETTINGS="${SEARXNG_DIR}/settings.yml"


# ============================================================
# Main SearXNG preparation
# ============================================================

prepare_searxng() {

    log "Preparing SearXNG..."

    check_searxng_prerequisites

    clone_or_update_searxng

    create_searxng_virtualenv

    install_searxng_dependencies

    create_searxng_settings

    start_searxng

    success "SearXNG is ready."
}


# ============================================================
# Prerequisites
# ============================================================

check_searxng_prerequisites() {

    log "Checking SearXNG prerequisites..."


    if ! command_exists python3; then

        fail "python3 is required for SearXNG."

    fi


    if ! command_exists git; then

        fail "git is required for SearXNG."

    fi


    if ! python3 -m venv --help >/dev/null 2>&1; then

        fail \
            "Python virtual environment support is missing. " \
            "Install python3-venv."

    fi


    local python_version

    python_version="$(
        python3 -c \
            'import sys; print(".".join(map(str, sys.version_info[:3])))'
    )"


    info "Python: ${python_version}"


    if ! python3 -c \
        'import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)'
    then

        fail \
            "SearXNG requires Python 3.10 or newer."

    fi


    success "SearXNG prerequisites are available."
}


# ============================================================
# Clone / update SearXNG
# ============================================================

clone_or_update_searxng() {

    if [[ ! -d "${SEARXNG_SRC}/.git" ]]; then

        log "SearXNG source not found. Cloning..."

        mkdir -p "${SEARXNG_DIR}"

        run git clone \
            "${SEARXNG_REPOSITORY}" \
            "${SEARXNG_SRC}"

        success "SearXNG source cloned."

        return
    fi


    log "SearXNG source already exists. Updating..."


    if ! git -C "${SEARXNG_SRC}" diff \
        --quiet \
        --exit-code
    then

        warning \
            "SearXNG source contains local modifications."

        warning \
            "Skipping git pull to avoid overwriting changes."

        return
    fi


    if ! git -C "${SEARXNG_SRC}" diff \
        --cached \
        --quiet
    then

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


    success "SearXNG source updated."
}


# ============================================================
# Python virtual environment
# ============================================================

create_searxng_virtualenv() {

    log "Preparing SearXNG Python environment..."


    if [[ ! -x "${SEARXNG_PYENV}/bin/python" ]]; then

        info "Creating virtual environment..."


        run python3 \
            -m venv \
            "${SEARXNG_PYENV}"


    else

        success \
            "SearXNG virtual environment already exists."

    fi
}


# ============================================================
# Install SearXNG
# ============================================================

install_searxng_dependencies() {

    log "Installing/updating SearXNG Python dependencies..."


    local python="${SEARXNG_PYENV}/bin/python"
    local pip="${SEARXNG_PYENV}/bin/pip"


    if [[ ! -x "${python}" ]]; then

        fail \
            "SearXNG Python executable not found: ${python}"

    fi


    # --------------------------------------------------------
    # Upgrade Python packaging tools
    # --------------------------------------------------------

    run "${python}" \
        -m pip install \
        --upgrade \
        pip \
        setuptools \
        wheel


    # --------------------------------------------------------
    # Install SearXNG
    # --------------------------------------------------------

    run "${pip}" \
        install \
        --use-pep517 \
        --no-build-isolation \
        -e "${SEARXNG_SRC}"


    success "SearXNG Python environment is ready."
}


# ============================================================
# Create settings.yml
# ============================================================

create_searxng_settings() {

    log "Preparing SearXNG settings..."


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


    cp \
        "${template}" \
        "${SEARXNG_SETTINGS}"


    # --------------------------------------------------------
    # Generate random secret key
    # --------------------------------------------------------

    local secret


    if command_exists openssl; then

        secret="$(
            openssl rand -hex 32
        )"

    else

        secret="$(
            "${SEARXNG_PYENV}/bin/python" -c \
            'import secrets; print(secrets.token_hex(32))'
        )

    fi


    # Replace the default secret.
    "${SEARXNG_PYENV}/bin/python" - \
        "${SEARXNG_SETTINGS}" \
        "${secret}" <<'PY'

import sys
from pathlib import Path

settings_file = Path(sys.argv[1])
secret = sys.argv[2]

content = settings_file.read_text()

content = content.replace(
    "ultrasecretkey",
    secret
)

settings_file.write_text(content)

PY


    # --------------------------------------------------------
    # Configure localhost + port
    # --------------------------------------------------------

    "${SEARXNG_PYENV}/bin/python" - \
        "${SEARXNG_SETTINGS}" \
        "${SEARXNG_PORT}" <<'PY'

import sys
from pathlib import Path

settings_file = Path(sys.argv[1])
port = int(sys.argv[2])

content = settings_file.read_text()

lines = content.splitlines()

result = []

inside_server = False
bind_written = False
port_written = False


for line in lines:

    stripped = line.strip()


    if stripped == "server:":

        inside_server = True

        result.append(line)

        continue


    if inside_server and (
        line
        and not line.startswith(" ")
        and not line.startswith("\t")
    ):

        inside_server = False


    if inside_server and stripped.startswith("bind_address:"):

        result.append(
            '  bind_address: "127.0.0.1"'
        )

        bind_written = True

        continue


    if inside_server and stripped.startswith("port:"):

        result.append(
            f"  port: {port}"
        )

        port_written = True

        continue


    result.append(line)


# If the template didn't contain a server section,
# create one.

if not bind_written or not port_written:

    result.append("")

    result.append("server:")

    if not bind_written:

        result.append(
            '  bind_address: "127.0.0.1"'
        )

    if not port_written:

        result.append(
            f"  port: {port}"
        )


settings_file.write_text(
    "\n".join(result) + "\n"
)

PY


    success \
        "SearXNG settings created: ${SEARXNG_SETTINGS}"
}


# ============================================================
# Start SearXNG
# ============================================================

start_searxng() {

    log "Starting SearXNG..."


    local python="${SEARXNG_PYENV}/bin/python"


    if [[ ! -x "${python}" ]]; then

        fail \
            "SearXNG Python environment is not available."

    fi


    # --------------------------------------------------------
    # Check if SearXNG is already running
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


    local log_file="${SEARXNG_DIR}/searxng.log"


    # --------------------------------------------------------
    # Start SearXNG in background
    # --------------------------------------------------------

    (
        cd "${SEARXNG_SRC}"


        export SEARXNG_SETTINGS_PATH="${SEARXNG_SETTINGS}"


        nohup "${python}" \
            -m searx.webapp \
            > "${log_file}" \
            2>&1 &


        echo $! > \
            "${SEARXNG_DIR}/searxng.pid"
    )


    wait_for_searxng
}


# ============================================================
# Wait for SearXNG
# ============================================================

wait_for_searxng() {

    log "Waiting for SearXNG..."


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


        # ----------------------------------------------------
        # Check whether the process died
        # ----------------------------------------------------

        if [[ -f "${SEARXNG_DIR}/searxng.pid" ]]; then

            local pid

            pid="$(
                cat "${SEARXNG_DIR}/searxng.pid"
            )"


            if ! kill -0 "${pid}" 2>/dev/null; then

                warning \
                    "SearXNG process exited unexpectedly."


                if [[ -f "${SEARXNG_DIR}/searxng.log" ]]; then

                    warning "Last SearXNG log output:"

                    tail -n 40 \
                        "${SEARXNG_DIR}/searxng.log"

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


    if [[ -f "${SEARXNG_DIR}/searxng.log" ]]; then

        warning "Last SearXNG log output:"

        tail -n 40 \
            "${SEARXNG_DIR}/searxng.log"

    fi


    fail \
        "SearXNG did not become ready."
}


# ============================================================
# End
# ============================================================