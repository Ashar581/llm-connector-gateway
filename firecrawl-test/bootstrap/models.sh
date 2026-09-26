#!/usr/bin/env bash

set -Eeuo pipefail

# ------------------------------------------------------------
# Model management
# ------------------------------------------------------------


download_model() {

    local filename="$1"
    local url="$2"

    local destination="${LLAMA_MODELS_DIR}/${filename}"


    if [[ -f "${destination}" ]] \
        && [[ -s "${destination}" ]]; then

        success "Model already exists: ${filename}"

        return
    fi


    log "Downloading model: ${filename}"

    mkdir -p "${LLAMA_MODELS_DIR}"


    if command_exists wget; then

        run wget \
            -c \
            -O "${destination}" \
            "${url}"

    elif command_exists curl; then

        run curl \
            -L \
            --fail \
            --retry 5 \
            --continue-at - \
            -o "${destination}" \
            "${url}"

    else

        fail \
            "Neither wget nor curl is available."
    fi


    if [[ ! -s "${destination}" ]]; then

        rm -f "${destination}"

        fail \
            "Model download failed: ${filename}"
    fi


    success "Downloaded: ${filename}"
}


download_models() {

    log "Checking models..."

    mkdir -p "${LLAMA_MODELS_DIR}"


    download_model \
        "qwen2.5-7b-instruct-q4_0-00001-of-00002.gguf" \
        "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_0-00001-of-00002.gguf"


    download_model \
        "qwen2.5-7b-instruct-q4_0-00002-of-00002.gguf" \
        "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_0-00002-of-00002.gguf"


    download_model \
        "Bonsai-8B.gguf" \
        "https://huggingface.co/prism-ml/Bonsai-8B-gguf/resolve/main/Bonsai-8B.gguf"


    download_model \
        "bge-large-en-v1.5-q4_k_m.gguf" \
        "https://huggingface.co/CompendiumLabs/bge-large-en-v1.5-gguf/resolve/main/bge-large-en-v1.5-q4_k_m.gguf"


    download_model \
        "Qwen3VL-8B-Instruct-Q4_K_M.gguf" \
        "https://huggingface.co/Qwen/Qwen3-VL-8B-Instruct-GGUF/resolve/main/Qwen3VL-8B-Instruct-Q4_K_M.gguf"


    download_model \
        "mmproj-Qwen3VL-8B-Instruct-F16.gguf" \
        "https://huggingface.co/Qwen/Qwen3-VL-8B-Instruct-GGUF/resolve/main/mmproj-Qwen3VL-8B-Instruct-F16.gguf"


    download_model \
        "bge-m3-Q4_K_M.gguf" \
        "https://huggingface.co/gpustack/bge-m3-GGUF/resolve/main/bge-m3-Q4_K_M.gguf"


    success "Model check completed."
}