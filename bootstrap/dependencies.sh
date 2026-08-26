#!/usr/bin/env bash

set -Eeuo pipefail


# ============================================================
# System dependencies
# ============================================================

install_dependencies() {

    log "Checking system dependencies..."


    if [[ "${PLATFORM_OS}" == "linux" ]]; then

        install_linux_dependencies

    elif [[ "${PLATFORM_OS}" == "macos" ]]; then

        install_macos_dependencies

    else

        fail \
            "Unsupported operating system: ${PLATFORM_OS}"

    fi
}


# ============================================================
# Linux
# ============================================================

install_linux_dependencies() {

    if ! command_exists apt-get; then

        fail \
            "Automatic Linux installation currently supports Debian/Ubuntu systems using apt."

    fi


    local packages=(
        git
        curl
        wget
        unzip
        build-essential
        cmake
        python3
        python3-pip
        python3-venv
        python3-dev
        libxml2-dev
        libxslt1-dev
        libffi-dev
        libssl-dev
        zlib1g-dev
        openssl
        screen
        htop
    )


    local missing=()


    for package in "${packages[@]}"; do

        if ! dpkg -s "${package}" >/dev/null 2>&1; then

            missing+=("${package}")

        fi

    done


    if [[ "${#missing[@]}" -eq 0 ]]; then

        success \
            "Required Debian/Ubuntu packages are installed."

        return
    fi


    info "Missing packages:"
    info "${missing[*]}"


    if is_root; then

        run apt-get update

        run apt-get install \
            -y \
            "${missing[@]}"

    else

        if ! sudo_available; then

            fail \
                "Missing packages detected but sudo is unavailable."

        fi


        run sudo apt-get update

        run sudo apt-get install \
            -y \
            "${missing[@]}"

    fi


    success \
        "System dependencies installed."
}


# ============================================================
# macOS
# ============================================================

install_macos_dependencies() {

    if ! command_exists brew; then

        fail \
            "Homebrew is required on macOS for automatic dependency installation."

    fi


    local packages=(
        git
        cmake
        python
        wget
    )


    local missing=()


    for package in "${packages[@]}"; do

        if ! brew list \
            --formula \
            "${package}" \
            >/dev/null 2>&1
        then

            missing+=("${package}")

        fi

    done


    if [[ "${#missing[@]}" -eq 0 ]]; then

        success \
            "Required Homebrew packages are installed."

        return
    fi


    info "Missing Homebrew packages:"
    info "${missing[*]}"


    if [[ "${PLATFORM_ARCH}" == "arm64" ]]; then

        run arch \
            -arm64 \
            brew \
            install \
            "${missing[@]}"

    else

        run brew \
            install \
            "${missing[@]}"

    fi


    success \
        "macOS dependencies installed."
}