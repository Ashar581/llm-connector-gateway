#!/usr/bin/env bash

set -Eeuo pipefail

# ------------------------------------------------------------
# Platform / hardware detection
# ------------------------------------------------------------

detect_platform() {

    OS="$(uname -s)"
    ARCH="$(uname -m)"

    case "${OS}" in

        Linux)
            PLATFORM_OS="linux"
            ;;

        Darwin)
            PLATFORM_OS="macos"
            ;;

        *)
            fail "Unsupported operating system: ${OS}"
            ;;
    esac


    case "${ARCH}" in

        x86_64|amd64)
            PLATFORM_ARCH="x86_64"
            ;;

        arm64|aarch64)
            PLATFORM_ARCH="arm64"
            ;;

        *)
            PLATFORM_ARCH="unknown"
            ;;
    esac


    GPU_VENDOR="none"
    GPU_NAME="none"
    LLAMA_BACKEND="CPU"


    if [[ "${PLATFORM_OS}" == "macos" ]]; then

        detect_macos_backend

    elif [[ "${PLATFORM_OS}" == "linux" ]]; then

        detect_linux_backend

    fi


    write_state "os" "${PLATFORM_OS}"
    write_state "architecture" "${PLATFORM_ARCH}"
    write_state "gpu_vendor" "${GPU_VENDOR}"
    write_state "gpu_name" "${GPU_NAME}"
    write_state "backend" "${LLAMA_BACKEND}"


    log "Detected platform"

    info "OS: ${PLATFORM_OS}"
    info "Architecture: ${PLATFORM_ARCH}"
    info "GPU: ${GPU_NAME}"
    info "Backend: ${LLAMA_BACKEND}"
}


detect_macos_backend() {

    if [[ "${PLATFORM_ARCH}" == "arm64" ]]; then

        GPU_VENDOR="apple"
        GPU_NAME="Apple Silicon"
        LLAMA_BACKEND="METAL"

        return
    fi


    # Intel Mac.
    GPU_VENDOR="none"
    GPU_NAME="Intel Mac GPU"
    LLAMA_BACKEND="CPU"
}


detect_linux_backend() {

    # --------------------------------------------------------
    # NVIDIA / CUDA
    # --------------------------------------------------------

    if command_exists nvidia-smi; then

        GPU_VENDOR="nvidia"

        GPU_NAME="$(
            nvidia-smi \
                --query-gpu=name \
                --format=csv,noheader \
                2>/dev/null \
                | head -n1 \
                || true
        )"

        if [[ -z "${GPU_NAME}" ]]; then
            GPU_NAME="NVIDIA GPU"
        fi


        if command_exists nvcc; then

            LLAMA_BACKEND="CUDA"

            return
        fi

        warning "NVIDIA GPU detected but nvcc/CUDA toolkit was not found."
        warning "Falling back to CPU."

        LLAMA_BACKEND="CPU"

        return
    fi


    # --------------------------------------------------------
    # AMD / ROCm / HIP
    # --------------------------------------------------------

    if command_exists rocminfo \
        && command_exists hipcc; then

        GPU_VENDOR="amd"

        GPU_NAME="$(
            rocminfo 2>/dev/null \
                | grep -m1 \
                -E "Name:.*gfx|Marketing Name:" \
                | sed 's/^[[:space:]]*//' \
                || true
        )"

        if [[ -z "${GPU_NAME}" ]]; then
            GPU_NAME="AMD GPU"
        fi

        LLAMA_BACKEND="HIP"

        return
    fi


    # --------------------------------------------------------
    # Vulkan
    #
    # Do NOT select Vulkan merely because vulkaninfo exists.
    # llama.cpp's Vulkan build needs the development tooling
    # as well.
    # --------------------------------------------------------

    if command_exists vulkaninfo \
        && command_exists glslc; then

        GPU_VENDOR="vulkan"
        GPU_NAME="Vulkan GPU"
        LLAMA_BACKEND="VULKAN"

        return
    fi


    # --------------------------------------------------------
    # CPU fallback
    # --------------------------------------------------------

    GPU_VENDOR="none"
    GPU_NAME="none"
    LLAMA_BACKEND="CPU"
}


print_platform_summary() {

    printf '\n'
    printf '%s\n' \
        "------------------------------------------------------------"

    printf 'OS          : %s\n' "${PLATFORM_OS}"
    printf 'Architecture: %s\n' "${PLATFORM_ARCH}"
    printf 'GPU Vendor  : %s\n' "${GPU_VENDOR}"
    printf 'GPU         : %s\n' "${GPU_NAME}"
    printf 'Backend     : %s\n' "${LLAMA_BACKEND}"

    printf '%s\n' \
        "------------------------------------------------------------"
}