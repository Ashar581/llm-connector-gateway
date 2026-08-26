#!/usr/bin/env bash

set -Eeuo pipefail

# ------------------------------------------------------------
# SearXNG
# ------------------------------------------------------------

SEARXNG_PORT="${SEARXNG_PORT:-8080}"


prepare_searxng() {

    log "Preparing SearXNG..."

    if ! command_exists docker; then

        fail \
            "Docker is required for the automatic SearXNG installation."
    fi


    if ! docker compose version >/dev/null 2>&1; then

        fail \
            "Docker Compose is required but was not found."
    fi


    mkdir -p "${SEARXNG_DIR}/core-config"


    download_searxng_templates

    create_searxng_environment

    create_searxng_settings

    start_searxng

    wait_for_searxng

    success "SearXNG is ready."
}


download_searxng_templates() {

    local compose_file="${SEARXNG_DIR}/docker-compose.yml"
    local env_example="${SEARXNG_DIR}/.env.example"


    if [[ ! -f "${compose_file}" ]]; then

        log "Downloading official SearXNG Compose template..."

        run curl \
            -fsSL \
            -o "${compose_file}" \
            "https://raw.githubusercontent.com/searxng/searxng/master/container/docker-compose.yml"
    fi


    if [[ ! -f "${env_example}" ]]; then

        run curl \
            -fsSL \
            -o "${env_example}" \
            "https://raw.githubusercontent.com/searxng/searxng/master/container/.env.example"
    fi
}


create_searxng_environment() {

    local env_file="${SEARXNG_DIR}/.env"


    if [[ ! -f "${env_file}" ]]; then

        cp \
            "${SEARXNG_DIR}/.env.example" \
            "${env_file}"
    fi


    # The official Compose template uses SEARXNG_HOSTNAME
    # and SEARXNG_PORT through its environment.
    #
    # We keep the service bound locally because your
    # Spring Boot application is the consumer.

    if ! grep -q "^SEARXNG_PORT=" "${env_file}" 2>/dev/null; then

        printf '\nSEARXNG_PORT=%s\n' \
            "${SEARXNG_PORT}" \
            >> "${env_file}"

    fi
}


create_searxng_settings() {

    local settings_file="${SEARXNG_DIR}/core-config/settings.yml"


    if [[ -f "${settings_file}" ]]; then

        success "Existing SearXNG settings.yml preserved."

        return
    fi


    cat > "${settings_file}" <<'EOF'
use_default_settings: true

general:
  debug: false
  instance_name: "LLM Connector Gateway Search"

server:
  bind_address: "0.0.0.0"
  port: 8080
  secret_key: "CHANGE_THIS_SECRET_KEY"

search:
  safe_search: 0
  autocomplete: ""
EOF


    warning \
        "SearXNG settings.yml created with a development secret key."

    warning \
        "Change the secret_key before exposing SearXNG externally."
}


start_searxng() {

    log "Starting SearXNG..."

    run docker compose \
        -f "${SEARXNG_DIR}/docker-compose.yml" \
        --project-directory "${SEARXNG_DIR}" \
        up -d
}


wait_for_searxng() {

    log "Waiting for SearXNG..."

    local attempts=0
    local max_attempts=30


    while (( attempts < max_attempts )); do

        if curl \
            -fsS \
            "http://127.0.0.1:${SEARXNG_PORT}/" \
            >/dev/null 2>&1; then

            success "SearXNG is responding."

            return
        fi


        attempts=$((attempts + 1))

        sleep 2
    done


    warning \
        "SearXNG container started but did not become reachable within the expected time."

    run docker compose \
        -f "${SEARXNG_DIR}/docker-compose.yml" \
        --project-directory "${SEARXNG_DIR}" \
        ps

    fail \
        "SearXNG did not become ready."
}