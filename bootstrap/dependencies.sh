#!/usr/bin/env bash

set -Eeuo pipefail

# ------------------------------------------------------------
# System dependencies
# ------------------------------------------------------------

install_dependencies() {

    log "Checking system dependencies..."

    if [[ "${PLATFORM_OS}" == "linux" ]]; then

        install_linux_dependencies

    elif [[ "${PLATFORM_OS}" == "macos" ]]; then

        install_macos_dependencies

    else

        fail "Dependency installation is not supported for: ${PLATFORM_OS}"
    fi
}


install_linux_dependencies() {

    if ! command_exists apt-get; then

        fail \
            "This automatic Linux installer currently supports Debian/Ubuntu (apt)."
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
        openjdk-21-jdk
        maven
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

        success "Required Debian/Ubuntu packages are installed."

        return
    fi


    info "Missing packages:"
    info "${missing[*]}"


    if is_root; then

        run apt-get update
        run apt-get install -y "${missing[@]}"

    else

        if ! sudo_available; then

            fail \
                "Missing packages and sudo is unavailable."
        fi

        run sudo apt-get update
        run sudo apt-get install -y "${missing[@]}"

    fi


    success "System dependencies installed."
}


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
        maven
    )


    local missing=()

    for package in "${packages[@]}"; do

        if ! brew list --formula "${package}" >/dev/null 2>&1; then
            missing+=("${package}")
        fi

    done


    if [[ "${#missing[@]}" -eq 0 ]]; then

        success "Required Homebrew packages are installed."

        return
    fi


    run brew install "${missing[@]}"

    success "macOS dependencies installed."
}