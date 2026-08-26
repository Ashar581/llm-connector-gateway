#!/usr/bin/env bash

set -Eeuo pipefail


SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"


source "${SCRIPT_DIR}/common.sh"
source "${SCRIPT_DIR}/platform.sh"
source "${SCRIPT_DIR}/dependencies.sh"
source "${SCRIPT_DIR}/llama.sh"
source "${SCRIPT_DIR}/models.sh"
source "${SCRIPT_DIR}/searxng.sh"


main() {

    printf '\n'
    printf '%s\n' \
        "============================================================"
    printf '%s\n' \
        "        LLM Connector Gateway - Bootstrap"
    printf '%s\n' \
        "============================================================"


    log "Starting environment preparation."


    # --------------------------------------------------------
    # 1. Detect hardware/platform
    # --------------------------------------------------------

    detect_platform

    print_platform_summary


    # --------------------------------------------------------
    # 2. Install required system dependencies
    # --------------------------------------------------------

    install_dependencies


    # --------------------------------------------------------
    # 3. Pull + build llama.cpp
    # --------------------------------------------------------

    prepare_llama


    # --------------------------------------------------------
    # 4. Download required GGUF models
    # --------------------------------------------------------

    download_models


    # --------------------------------------------------------
    # 5. Install/configure/start SearXNG
    # --------------------------------------------------------

    prepare_searxng


    printf '\n'
    printf '%s\n' \
        "============================================================"
    printf '%s\n' \
        "             Bootstrap completed successfully"
    printf '%s\n' \
        "============================================================"
    printf '\n'

    info "llama.cpp : ${LLAMA_DIR}"
    info "Models    : ${LLAMA_MODELS_DIR}"
    info "SearXNG   : http://127.0.0.1:${SEARXNG_PORT}"
    info "Backend   : ${LLAMA_BACKEND}"
}


main "$@"