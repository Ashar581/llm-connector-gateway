#!/usr/bin/env bash

set -Eeuo pipefail


# ============================================================
# LLM Connector Gateway - Bootstrap
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"


# ------------------------------------------------------------
# Load bootstrap modules
# ------------------------------------------------------------

source "${SCRIPT_DIR}/common.sh"
source "${SCRIPT_DIR}/platform.sh"
source "${SCRIPT_DIR}/dependencies.sh"
source "${SCRIPT_DIR}/llama.sh"
source "${SCRIPT_DIR}/models.sh"
source "${SCRIPT_DIR}/searxng.sh"


# ============================================================
# Main
# ============================================================

main() {

    printf '\n'
    printf '%s\n' \
        "============================================================"
    printf '%s\n' \
        "        LLM Connector Gateway - Bootstrap"
    printf '%s\n' \
        "============================================================"
    printf '\n'


    log "Starting environment preparation."


    # --------------------------------------------------------
    # 1. Detect operating system, architecture and GPU
    # --------------------------------------------------------

    detect_platform

    print_platform_summary


    # --------------------------------------------------------
    # 2. Install/check system dependencies
    #
    # This handles things such as:
    #
    # git
    # cmake
    # python
    # pip
    # python venv
    # build tools
    # etc.
    # --------------------------------------------------------

    install_dependencies


    # --------------------------------------------------------
    # 3. Clone/update and build llama.cpp
    #
    # First run:
    #
    #     git clone
    #     cmake configure
    #     build
    #
    # Subsequent runs:
    #
    #     git pull
    #     cmake configure
    #     incremental build
    #
    # The backend is selected automatically by platform.sh.
    # --------------------------------------------------------

    prepare_llama


    # --------------------------------------------------------
    # 4. Download required GGUF models
    #
    # Existing models are skipped.
    # Interrupted downloads can resume.
    # --------------------------------------------------------

    download_models


    # --------------------------------------------------------
    # 5. Install/configure/start SearXNG
    #
    # This is a native Python installation.
    #
    # No Docker is required.
    #
    # prepare_searxng() handles:
    #
    #     clone/update source
    #     create virtualenv
    #     install dependencies
    #     create settings.yml
    #     start SearXNG
    #     wait until it is reachable
    # --------------------------------------------------------

    prepare_searxng


    # --------------------------------------------------------
    # Bootstrap completed
    # --------------------------------------------------------

    printf '\n'
    printf '%s\n' \
        "============================================================"
    printf '%s\n' \
        "             Bootstrap completed successfully"
    printf '%s\n' \
        "============================================================"
    printf '\n'


    info "Project   : ${PROJECT_ROOT}"
    info "llama.cpp : ${LLAMA_DIR}"
    info "Models    : ${LLAMA_MODELS_DIR}"
    info "SearXNG   : http://127.0.0.1:${SEARXNG_PORT}"
    info "Backend   : ${LLAMA_BACKEND}"

    printf '\n'
}


# ============================================================
# Execute
# ============================================================

main "$@"