#!/usr/bin/env bash

set -Eeuo pipefail

# ------------------------------------------------------------
# llama.cpp
# ------------------------------------------------------------

LLAMA_REPOSITORY="https://github.com/ggml-org/llama.cpp"


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


clone_llama() {

    log "llama.cpp is not present. Cloning..."

    run git clone \
        "${LLAMA_REPOSITORY}" \
        "${LLAMA_DIR}"

    success "llama.cpp cloned."
}


update_llama() {

    log "llama.cpp already exists. Pulling latest changes..."

    if ! git -C "${LLAMA_DIR}" diff \
        --quiet \
        --exit-code; then

        fail \
            "llama.cpp contains local modifications. " \
            "Refusing to overwrite them with git pull."
    fi


    if ! git -C "${LLAMA_DIR}" diff \
        --cached \
        --quiet; then

        fail \
            "llama.cpp contains staged local changes. " \
            "Refusing to overwrite them."
    fi


    run git \
        -C "${LLAMA_DIR}" \
        pull \
        --ff-only

    success "llama.cpp updated."
}


configure_and_build_llama() {

    log "Configuring llama.cpp for ${LLAMA_BACKEND}..."


    local previous_backend=""

    if [[ -f "${PLATFORM_STATE_FILE}" ]]; then

        previous_backend="$(
            read_state backend || true
        )"
    fi


    # If the backend changed, the previous CMake configuration
    # cannot safely be reused.
    #
    # Example:
    #
    # CPU -> CUDA
    #
    # Therefore remove the build directory.
    if [[ -n "${previous_backend}" ]] \
        && [[ "${previous_backend}" != "${LLAMA_BACKEND}" ]]; then

        warning \
            "llama.cpp backend changed from ${previous_backend} to ${LLAMA_BACKEND}."

        warning "Removing previous CMake build directory."

        rm -rf "${LLAMA_DIR}/build"
    fi


    mkdir -p "${LLAMA_DIR}/models"


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

            # Explicitly disable GPU backends so that a stale
            # CMake cache cannot accidentally retain one.

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


    run cmake \
        "${LLAMA_DIR}" \
        "${cmake_args[@]}"


    log "Building llama.cpp..."

    local jobs

    jobs="$(
        get_cpu_count
    )"


    run cmake \
        --build "${LLAMA_DIR}/build" \
        --config Release \
        --parallel "${jobs}"


    verify_llama_build


    # IMPORTANT:
    # Store the backend AFTER a successful build.
    write_state "backend" "${LLAMA_BACKEND}"

    success "llama.cpp build completed."
}


verify_llama_build() {

    local binary=""

    if [[ "${PLATFORM_OS}" == "macos" ]] \
        || [[ "${PLATFORM_OS}" == "linux" ]]; then

        binary="${LLAMA_DIR}/build/bin/llama-server"

    fi


    if [[ ! -x "${binary}" ]]; then

        fail \
            "llama-server was not produced by the llama.cpp build."
    fi


    success "llama-server found."
}


get_cpu_count() {

    if command_exists nproc; then

        nproc

    elif command_exists sysctl; then

        sysctl -n hw.ncpu

    else

        echo "2"

    fi
}