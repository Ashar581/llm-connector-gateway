#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
BOOTSTRAP_DIR="${PROJECT_ROOT}/bootstrap"

[[ -f "${BOOTSTRAP_DIR}/common.sh" ]] || { echo "ERROR: ${BOOTSTRAP_DIR}/common.sh not found"; exit 1; }
[[ -f "${BOOTSTRAP_DIR}/platform.sh" ]] || { echo "ERROR: ${BOOTSTRAP_DIR}/platform.sh not found"; exit 1; }
[[ -f "${BOOTSTRAP_DIR}/firecrawl.sh" ]] || { echo "ERROR: ${BOOTSTRAP_DIR}/firecrawl.sh not found"; exit 1; }

# shellcheck disable=SC1091
source "${BOOTSTRAP_DIR}/common.sh"
source "${BOOTSTRAP_DIR}/platform.sh"

export PROJECT_ROOT
export RUNTIME_DIR="${PROJECT_ROOT}/runtime"

# shellcheck disable=SC1090
source "${BOOTSTRAP_DIR}/firecrawl.sh"

EXPECTED="${RUNTIME_DIR}/firecrawl"
[[ "${FIRECRAWL_DIR}" == "${EXPECTED}" ]] || {
  echo "ERROR: Firecrawl runtime escaped project directory."
  echo "Expected: ${EXPECTED}"
  echo "Actual:   ${FIRECRAWL_DIR}"
  exit 1
}

echo
echo "============================================================"
echo "      Firecrawl Native / No-Docker Full Stack Test"
echo "============================================================"
echo "Project : ${PROJECT_ROOT}"
echo "Runtime : ${FIRECRAWL_DIR}"
echo

detect_platform

# No Docker invocation is permitted by this test.
if [[ "${DOCKER_HOST:-}" != "" ]]; then
  echo "INFO: DOCKER_HOST is set, but this script does not use Docker."
fi

prepare_firecrawl

    echo
    echo "Checking service dependency installations..."
    [[ -d "${FIRECRAWL_SRC}/apps/api/node_modules" ]] || { echo "ERROR: API node_modules missing."; exit 1; }
    [[ -d "${FIRECRAWL_SRC}/apps/playwright-service-ts/node_modules" ]] || { echo "ERROR: Playwright node_modules missing."; exit 1; }
    [[ -d "${FIRECRAWL_PLAYWRIGHT_CACHE}" ]] || { echo "ERROR: Project-local Chromium cache missing."; exit 1; }
    echo "  ✓ API dependencies"
    echo "  ✓ Playwright dependencies"
    echo "  ✓ Project-local Chromium cache"

echo
echo "Checking services..."

curl -fsS --max-time 5 \
  "http://${FIRECRAWL_HOST}:${FIRECRAWL_PORT}/v0/health/liveness" >/dev/null
echo "  ✓ Firecrawl API"

curl -fsS --max-time 5 \
  "http://${FIRECRAWL_HOST}:${FIRECRAWL_PLAYWRIGHT_PORT}/health" >/dev/null
echo "  ✓ Playwright"

PGREADY="$(dirname "${PG_CONFIG}")/pg_isready"
PSQL="$(dirname "${PG_CONFIG}")/psql"
"${PGREADY}" -h "${FIRECRAWL_HOST}" -p "${FIRECRAWL_POSTGRES_PORT}" >/dev/null
PGCRON_OK="$(PGPASSWORD="${FIRECRAWL_POSTGRES_PASSWORD}" "${PSQL}" -h "${FIRECRAWL_HOST}" -p "${FIRECRAWL_POSTGRES_PORT}" -U "${FIRECRAWL_POSTGRES_USER}" -d "${FIRECRAWL_POSTGRES_DB}" -Atc "SELECT 1 FROM pg_extension WHERE extname='pg_cron';")"
[[ "${PGCRON_OK}" == "1" ]] || { echo "ERROR: pg_cron extension is not installed in ${FIRECRAWL_POSTGRES_DB}."; exit 1; }
echo "  ✓ PostgreSQL 17 + pg_cron"

redis-cli -h "${FIRECRAWL_HOST}" -p "${FIRECRAWL_REDIS_PORT}" ping | grep -q PONG
echo "  ✓ Redis"

rabbitmq-diagnostics -q -n "firecrawl@localhost" check_running >/dev/null
echo "  ✓ RabbitMQ"

echo
echo "Running real scrape test..."

RESPONSE="${FIRECRAWL_LOG_DIR}/functional-test.json"

curl -fsS --max-time 120 \
  -X POST \
  "http://${FIRECRAWL_HOST}:${FIRECRAWL_PORT}/v2/scrape" \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com","formats":["markdown"]}' \
  > "${RESPONSE}"

if ! grep -Eq '"markdown"[[:space:]]*:' "${RESPONSE}"; then
  echo "ERROR: Firecrawl responded but no markdown result was found."
  cat "${RESPONSE}"
  exit 1
fi

echo "  ✓ /v2/scrape https://example.com"

echo
echo "============================================================"
echo "          FIRECRAWL FULL NATIVE TEST PASSED"
echo "============================================================"
echo
echo "API        : http://${FIRECRAWL_HOST}:${FIRECRAWL_PORT}"
echo "Playwright : http://${FIRECRAWL_HOST}:${FIRECRAWL_PLAYWRIGHT_PORT}"
echo "Runtime    : ${FIRECRAWL_DIR}"
echo "Logs       : ${FIRECRAWL_LOG_DIR}"
echo
echo "No Docker was invoked."
echo "All Firecrawl runtime/data/log files are under the project runtime."
echo
