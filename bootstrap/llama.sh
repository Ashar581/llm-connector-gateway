#!/usr/bin/env bash

set -Eeuo pipefail


# ============================================================
# llama.cpp
# ============================================================

LLAMA_REPOSITORY="https://github.com/ggml-org/llama.cpp"


# ============================================================
# Main
# ============================================================

prepare_llama() {

    log "Preparing llama.cpp..."


    mkdir -p "${RUNTIME_DIR}"


    if [[ ! -d "${LLAMA_DIR}/.git" ]]; then

        clone_llama

    else

        update_llama

    fi


    configure_and_build_llama
}


# ============================================================
# Clone
# ============================================================

clone_llama() {

    log "llama.cpp is not present. Cloning..."


    run git clone \
        "${LLAMA_REPOSITORY}" \
        "${LLAMA_DIR}"


    success "llama.cpp cloned."
}


# ============================================================
# Update
# ============================================================

update_llama() {

    log "llama.cpp already exists. Pulling latest changes..."


    if ! git -C "${LLAMA_DIR}" diff --quiet; then

        fail \
            "llama.cpp contains local modifications."

    fi


    if ! git -C "${LLAMA_DIR}" diff --cached --quiet; then

        fail \
            "llama.cpp contains staged local changes."

    fi


    run git \
        -C "${LLAMA_DIR}" \
        pull \
        --ff-only


    success "llama.cpp updated."
}


# ============================================================
# Configure + build
# ============================================================

configure_and_build_llama() {

    log \
        "Configuring llama.cpp for ${LLAMA_BACKEND}..."


    local previous_backend=""

    if [[ -f "${PLATFORM_STATE_FILE}" ]]; then

        previous_backend="$(
            read_state backend || true
        )"

    fi


    # If backend changed, discard the old CMake cache.

    if [[ -n "${previous_backend}" ]] \
        && [[ "${previous_backend}" != "${LLAMA_BACKEND}" ]]; then

        warning \
            "llama.cpp backend changed from ${previous_backend} to ${LLAMA_BACKEND}."

        warning \
            "Removing previous CMake build directory."

        rm -rf "${LLAMA_DIR}/build"

    fi


    mkdir -p "${LLAMA_MODELS_DIR}"


    local cmake_args=(
        "-B"
        "${LLAMA_DIR}/build"
        "-DCMAKE_BUILD_TYPE=Release"
    )


    case "${LLAMA_BACKEND}" in

        CUDA)

            cmake_args+=(
                "-DGGML_CUDA=ON"
                "-DGGML_NATIVE=OFF"
            )

            ;;


        METAL)

            cmake_args+=(
                "-DGGML_METAL=ON"
            )

            ;;


        HIP)

            cmake_args+=(
                "-DGGML_HIPBLAS=ON"
            )

            ;;


        VULKAN)

            cmake_args+=(
                "-DGGML_VULKAN=ON"
            )

            ;;


        CPU)

            cmake_args+=(
                "-DGGML_CUDA=OFF"
                "-DGGML_HIPBLAS=OFF"
                "-DGGML_VULKAN=OFF"
                "-DGGML_METAL=OFF"
            )

            ;;


        *)

            fail \
                "Unknown llama.cpp backend: ${LLAMA_BACKEND}"

            ;;

    esac


    # --------------------------------------------------------
    # Configure
    # --------------------------------------------------------

    if [[ "${PLATFORM_OS}" == "macos" ]] \
        && [[ "${PLATFORM_ARCH}" == "arm64" ]]; then

        run arch \
            -arm64 \
            cmake \
            "${LLAMA_DIR}" \
            "${cmake_args[@]}"

    else

        run cmake \
            "${LLAMA_DIR}" \
            "${cmake_args[@]}"

    fi


    # --------------------------------------------------------
    # Build
    # --------------------------------------------------------

    log "Building llama.cpp..."


    local jobs


    if command_exists nproc; then

        jobs="$(nproc)"

    elif command_exists sysctl; then

        jobs="$(sysctl -n hw.ncpu)"

    else

        jobs="2"

    fi


    if [[ "${PLATFORM_OS}" == "macos" ]] \
        && [[ "${PLATFORM_ARCH}" == "arm64" ]]; then

        run arch \
            -arm64 \
            cmake \
            --build \
            "${LLAMA_DIR}/build" \
            --config Release \
            --parallel "${jobs}"

    else

        run cmake \
            --build \
            "${LLAMA_DIR}/build" \
            --config Release \
            --parallel "${jobs}"

    fi


    verify_llama_build


    write_state \
        "backend" \
        "${LLAMA_BACKEND}"


    success \
        "llama.cpp build completed."
}


# ============================================================
# Verify
# ============================================================

verify_llama_build() {

    local binary="${LLAMA_DIR}/build/bin/llama-server"


    if [[ ! -x "${binary}" ]]; then

        fail \
            "llama-server was not produced by the llama.cpp build."

    fi


    success \
        "llama-server found."
}