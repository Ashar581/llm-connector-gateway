#!/usr/bin/env bash
# ============================================================
# Native Firecrawl bootstrap module
# ============================================================
# Source from the project's bootstrap. All Firecrawl source,
# build artifacts, service data, logs and state live below:
#
#   ${RUNTIME_DIR}/firecrawl
#
# No Docker is used.
#
# Firecrawl release: v2.11.0
# Queue backend: NuQ PostgreSQL
# ============================================================

if [[ "${FIRECRAWL_MODULE_LOADED:-0}" == "1" ]]; then
  return 0 2>/dev/null || exit 0
fi
export FIRECRAWL_MODULE_LOADED=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# When this module is sourced by bootstrap.sh, these are already exported.
# When invoked directly (for example: bash bootstrap/firecrawl.sh stop),
# derive safe project-local defaults instead of failing.
export PROJECT_ROOT="${PROJECT_ROOT:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
export RUNTIME_DIR="${RUNTIME_DIR:-${PROJECT_ROOT}/runtime}"

export FIRECRAWL_VERSION="${FIRECRAWL_VERSION:-v2.11.0}"
export FIRECRAWL_DIR="${RUNTIME_DIR}/firecrawl"
export FIRECRAWL_SRC="${FIRECRAWL_DIR}/firecrawl-src"
export FIRECRAWL_LOG_DIR="${FIRECRAWL_DIR}/logs"
export FIRECRAWL_STATE_DIR="${FIRECRAWL_DIR}/state"
export FIRECRAWL_CONFIG_DIR="${FIRECRAWL_DIR}/config"
export FIRECRAWL_BIN_DIR="${FIRECRAWL_DIR}/bin"
export FIRECRAWL_PLAYWRIGHT_CACHE="${FIRECRAWL_DIR}/playwright"
export FIRECRAWL_PNPM_STORE="${FIRECRAWL_DIR}/pnpm-store"
export FIRECRAWL_POSTGRES_DATA="${FIRECRAWL_DIR}/postgres-data"
export FIRECRAWL_REDIS_DATA="${FIRECRAWL_DIR}/redis-data"
export FIRECRAWL_RABBITMQ_DATA="${FIRECRAWL_DIR}/rabbitmq-data"
export FIRECRAWL_PGCRON_SRC="${FIRECRAWL_DIR}/pg_cron"
export FIRECRAWL_PGCRON_INSTALL="${FIRECRAWL_DIR}/pg_cron-install"

export FIRECRAWL_HOST="${FIRECRAWL_HOST:-127.0.0.1}"
export FIRECRAWL_PORT="${FIRECRAWL_PORT:-3002}"
export FIRECRAWL_PLAYWRIGHT_PORT="${FIRECRAWL_PLAYWRIGHT_PORT:-3003}"
export FIRECRAWL_POSTGRES_PORT="${FIRECRAWL_POSTGRES_PORT:-5433}"
export FIRECRAWL_REDIS_PORT="${FIRECRAWL_REDIS_PORT:-6380}"
export FIRECRAWL_RABBITMQ_PORT="${FIRECRAWL_RABBITMQ_PORT:-5673}"
export FIRECRAWL_POSTGRES_USER="${FIRECRAWL_POSTGRES_USER:-firecrawl}"
export FIRECRAWL_POSTGRES_PASSWORD="${FIRECRAWL_POSTGRES_PASSWORD:-firecrawl}"
export FIRECRAWL_POSTGRES_DB="${FIRECRAWL_POSTGRES_DB:-postgres}"

firecrawl_log() {
  mkdir -p "${FIRECRAWL_LOG_DIR}"
  printf '[FIRECRAWL] %s\n' "$*" | tee -a "${FIRECRAWL_LOG_DIR}/bootstrap.log"
}

firecrawl_error() {
  firecrawl_log "ERROR: $*" >&2
}

firecrawl_require() {
  command -v "$1" >/dev/null 2>&1 || {
    firecrawl_error "Required command not found: $1"
    return 1
  }
}

firecrawl_prepare_dirs() {
  mkdir -p \
    "${FIRECRAWL_DIR}" \
    "${FIRECRAWL_LOG_DIR}" \
    "${FIRECRAWL_STATE_DIR}" \
    "${FIRECRAWL_CONFIG_DIR}" \
    "${FIRECRAWL_BIN_DIR}" \
    "${FIRECRAWL_PLAYWRIGHT_CACHE}" \
    "${FIRECRAWL_PNPM_STORE}" \
    "${FIRECRAWL_POSTGRES_DATA}" \
    "${FIRECRAWL_REDIS_DATA}" \
    "${FIRECRAWL_RABBITMQ_DATA}"
}

firecrawl_brew_init() {
  if command -v brew >/dev/null 2>&1; then
    eval "$(brew shellenv)"
    return 0
  fi
  firecrawl_error "Homebrew is required for automatic macOS native prerequisites."
  return 1
}

firecrawl_install_mac_prereqs() {
  firecrawl_brew_init || return 1

  local formulas=()
  formulas+=(node@22)
  formulas+=(postgresql@17)
  formulas+=(redis)
  formulas+=(rabbitmq)
  formulas+=(pkg-config)

  for f in "${formulas[@]}"; do
    if ! brew list --formula "$f" >/dev/null 2>&1; then
      firecrawl_log "Installing Homebrew formula: $f"
      brew install "$f"
    fi
  done

  # Add keg-only tools to this process only. Nothing is written to
  # shell profiles.
  local p
  for f in node@22 postgresql@17; do
    p="$(brew --prefix "$f" 2>/dev/null || true)"
    [[ -n "$p" && -d "$p/bin" ]] && export PATH="$p/bin:$PATH"
  done

  local rust_bin="${HOME}/.cargo/bin"
  [[ -d "$rust_bin" ]] && export PATH="$rust_bin:$PATH"

  return 0
}

firecrawl_install_linux_prereqs() {
  if ! command -v apt-get >/dev/null 2>&1; then
    firecrawl_error "Automatic Linux prerequisites currently require apt-get."
    return 1
  fi

  local SUDO=""
  if [[ "$(id -u)" -ne 0 ]]; then
    command -v sudo >/dev/null 2>&1 || {
      firecrawl_error "sudo is required on non-root Linux."
      return 1
    }
    SUDO=sudo
  fi

  $SUDO apt-get update

  # PGDG provides PostgreSQL 17 on supported Debian/Ubuntu systems.
  if ! apt-cache policy postgresql-17 2>/dev/null | grep -q 'Candidate:'; then
    firecrawl_log "Adding PostgreSQL PGDG repository..."
    $SUDO apt-get install -y ca-certificates curl gnupg lsb-release
    $SUDO install -d -m 0755 /etc/apt/keyrings
    curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
      | $SUDO gpg --dearmor -o /etc/apt/keyrings/pgdg.gpg
    echo "deb [signed-by=/etc/apt/keyrings/pgdg.gpg] http://apt.postgresql.org/pub/repos/apt $(. /etc/os-release && echo "$VERSION_CODENAME")-pgdg main" \
      | $SUDO tee /etc/apt/sources.list.d/pgdg.list >/dev/null
    $SUDO apt-get update
  fi

  $SUDO apt-get install -y \
    git curl ca-certificates build-essential pkg-config \
    python3 make g++ \
    nodejs \
    postgresql-17 postgresql-server-dev-17 \
    redis-server rabbitmq-server

  # NodeSource is preferable if the distro's node is not 22.
  local major=""
  if command -v node >/dev/null 2>&1; then
    major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || true)"
  fi
  if [[ "$major" != "22" ]]; then
    firecrawl_log "Installing Node.js 22 from NodeSource..."
    curl -fsSL https://deb.nodesource.com/setup_22.x | $SUDO -E bash -
    $SUDO apt-get install -y nodejs
  fi

  return 0
}

firecrawl_ensure_rust() {
  if command -v cargo >/dev/null 2>&1; then
    return 0
  fi

  firecrawl_log "Installing Rust toolchain..."
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \
    | sh -s -- -y --no-modify-path

  export PATH="${HOME}/.cargo/bin:${PATH}"
  command -v cargo >/dev/null 2>&1 || {
    firecrawl_error "Rust installation failed."
    return 1
  }
}

firecrawl_ensure_node_pnpm() {
  if [[ "$(uname -s)" == "Darwin" ]]; then
    firecrawl_install_mac_prereqs || return 1
  else
    firecrawl_install_linux_prereqs || return 1
  fi

  command -v node >/dev/null 2>&1 || {
    firecrawl_error "Node.js is unavailable."
    return 1
  }

  local major
  major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || true)"
  [[ "$major" == "22" ]] || {
    firecrawl_error "Node.js 22 is required; found $(node --version 2>/dev/null || echo unknown)."
    return 1
  }

  # Firecrawl v2.11.0 pins pnpm 10.16.1 in apps/api/package.json.
  # Keep the version deterministic.
  if command -v corepack >/dev/null 2>&1; then
    corepack enable
    corepack prepare pnpm@10.16.1 --activate
  else
    npm install --global pnpm@10.16.1
  fi

  hash -r 2>/dev/null || true
  command -v pnpm >/dev/null 2>&1 || {
    firecrawl_error "pnpm is unavailable after activation."
    return 1
  }

  [[ "$(pnpm --version)" == "10.16.1" ]] || {
    firecrawl_error "Expected pnpm 10.16.1; found $(pnpm --version)."
    return 1
  }

  export PNPM_HOME="${FIRECRAWL_DIR}/pnpm-home"
  mkdir -p "${PNPM_HOME}"
  export PATH="${PNPM_HOME}:${PATH}"
  export npm_config_store_dir="${FIRECRAWL_PNPM_STORE}"
}

firecrawl_clone() {
  if [[ -d "${FIRECRAWL_SRC}/.git" ]]; then
    firecrawl_log "Using existing Firecrawl ${FIRECRAWL_VERSION}: ${FIRECRAWL_SRC}"
    return 0
  fi

  rm -rf "${FIRECRAWL_SRC}"
  firecrawl_log "Cloning Firecrawl ${FIRECRAWL_VERSION}..."
  git clone --branch "${FIRECRAWL_VERSION}" --depth 1 \
    https://github.com/firecrawl/firecrawl.git \
    "${FIRECRAWL_SRC}"
}

firecrawl_install_js() {
    local api_dir="${FIRECRAWL_SRC}/apps/api"
    local pw_dir="${FIRECRAWL_SRC}/apps/playwright-service-ts"

    [[ -f "${api_dir}/package.json" ]] || firecrawl_error "Missing Firecrawl API package.json: ${api_dir}"
    [[ -f "${pw_dir}/package.json" ]] || firecrawl_error "Missing Firecrawl Playwright package.json: ${pw_dir}"

    # Firecrawl is a pnpm monorepo, but the runnable services have their own
    # dependency manifests/lockfiles. Do not run pnpm install at repo root.
    #
    # The API contains an optional FoundationDB native dependency. Firecrawl's
    # PostgreSQL/NuQ self-host path does not require its native install script,
    # so install API dependencies with scripts disabled.
    firecrawl_log "Installing Firecrawl API dependencies (FoundationDB scripts disabled)..."
    (
        cd "${api_dir}"
        pnpm install --frozen-lockfile --ignore-scripts
    )

    firecrawl_log "Installing Firecrawl Playwright service dependencies..."
    (
        cd "${pw_dir}"
        pnpm install --frozen-lockfile
    )

    # Keep browser binaries inside this project's runtime rather than
    # ~/.cache/ms-playwright.
    firecrawl_log "Installing Chromium into ${FIRECRAWL_PLAYWRIGHT_CACHE}..."
    mkdir -p "${FIRECRAWL_PLAYWRIGHT_CACHE}"
    (
        cd "${pw_dir}"
        PLAYWRIGHT_BROWSERS_PATH="${FIRECRAWL_PLAYWRIGHT_CACHE}" \
            pnpm exec playwright install chromium
    )

    firecrawl_log "JavaScript dependencies and Chromium are ready."
}

firecrawl_pg_paths() {
  if command -v pg_config >/dev/null 2>&1; then
    PG_CONFIG="$(command -v pg_config)"
  elif [[ "$(uname -s)" == "Darwin" ]] && command -v brew >/dev/null 2>&1; then
    local p
    p="$(brew --prefix postgresql@17 2>/dev/null || true)"
    [[ -x "$p/bin/pg_config" ]] && PG_CONFIG="$p/bin/pg_config"
  fi
  export PG_CONFIG
}

firecrawl_build_pg_cron() {
  firecrawl_pg_paths
  [[ -n "${PG_CONFIG:-}" && -x "${PG_CONFIG}" ]] || {
    firecrawl_error "pg_config for PostgreSQL 17 was not found."
    return 1
  }

  local pg_major
  pg_major="$("${PG_CONFIG}" --version | awk '{print $2}' | cut -d. -f1)"
  [[ "$pg_major" == "17" ]] || {
    firecrawl_error "Firecrawl v2.11.0 requires PostgreSQL 17 for its bundled NuQ image; found PG ${pg_major}."
    return 1
  }

  local pkglib
  pkglib="$("${PG_CONFIG}" --pkglibdir)"
  local sharedir
  sharedir="$("${PG_CONFIG}" --sharedir)"

  if [[ -f "${pkglib}/pg_cron.so" && -f "${sharedir}/extension/pg_cron.control" ]]; then
    firecrawl_log "pg_cron already installed for PostgreSQL ${pg_major}."
    return 0
  fi

  if [[ ! -d "${FIRECRAWL_PGCRON_SRC}/.git" ]]; then
    rm -rf "${FIRECRAWL_PGCRON_SRC}"
    firecrawl_log "Cloning pg_cron v1.6.7..."
    git clone --depth 1 --branch v1.6.7 \
      https://github.com/citusdata/pg_cron.git \
      "${FIRECRAWL_PGCRON_SRC}"
  else
    local pgcron_version
    pgcron_version="$(git -C "${FIRECRAWL_PGCRON_SRC}" describe --tags --exact-match 2>/dev/null || true)"
    if [[ "${pgcron_version}" != "v1.6.7" ]]; then
      firecrawl_log "Updating existing pg_cron checkout to v1.6.7..."
      git -C "${FIRECRAWL_PGCRON_SRC}" fetch --depth 1 origin tag v1.6.7
      git -C "${FIRECRAWL_PGCRON_SRC}" checkout --force FETCH_HEAD
    fi
  fi

  cd "${FIRECRAWL_PGCRON_SRC}"
  firecrawl_log "pg_cron build target: ${PG_CONFIG}"
  firecrawl_log "PostgreSQL: $("${PG_CONFIG}" --version)"
  firecrawl_log "PGXS: $("${PG_CONFIG}" --pgxs)"
  make clean >/dev/null 2>&1 || true

  # IMPORTANT:
  # PostgreSQL packages on macOS can contain compiler flags captured when
  # PostgreSQL itself was built. After a macOS/Xcode/CLT update those flags
  # may point at a non-existent SDK such as:
  #
  #   /Library/Developer/CommandLineTools/SDKs/MacOSX15.sdk
  #
  # Do not hard-code an SDK version. Instead, obtain the active SDK from
  # xcrun and sanitize *all* compiler/linker flags passed to pg_cron.
  #
  # PGXS remains the actual PostgreSQL extension build system. We only
  # sanitize stale flags at the make environment boundary.

  local sdkroot=""
  local base_cflags="${CFLAGS:-}"
  local base_cppflags="${CPPFLAGS:-}"
  local base_cxxflags="${CXXFLAGS:-}"
  local base_ldflags="${LDFLAGS:-}"

  if [[ "$(uname -s)" == "Darwin" ]]; then
    sdkroot="$(xcrun --sdk macosx --show-sdk-path 2>/dev/null || true)"
    [[ -n "${sdkroot}" && -d "${sdkroot}" ]] || {
      firecrawl_error "A usable macOS SDK was not found. Install/repair Xcode Command Line Tools."
      return 1
    }

    firecrawl_log "Using active macOS SDK: ${sdkroot}"

    # Remove every existing -isysroot <path> pair and replace it with the
    # SDK currently reported by xcrun. This handles flags coming from:
    #   - the environment
    #   - Homebrew's pg_config
    #   - PostgreSQL's PGXS makefiles
    #
    # Also strip the stale SDK from SDKROOT if the environment contains one.
    base_cflags="$(printf '%s' "${base_cflags}" | awk '{
      for (i=1;i<=NF;i++) {
        if ($i == "-isysroot") { i++; continue }
        printf "%s%s", (n++ ? " " : ""), $i
      }
    }')"
    base_cppflags="$(printf '%s' "${base_cppflags}" | awk '{
      for (i=1;i<=NF;i++) {
        if ($i == "-isysroot") { i++; continue }
        printf "%s%s", (n++ ? " " : ""), $i
      }
    }')"
    base_cxxflags="$(printf '%s' "${base_cxxflags}" | awk '{
      for (i=1;i<=NF;i++) {
        if ($i == "-isysroot") { i++; continue }
        printf "%s%s", (n++ ? " " : ""), $i
      }
    }')"
    base_ldflags="$(printf '%s' "${base_ldflags}" | awk '{
      for (i=1;i<=NF;i++) {
        if ($i == "-isysroot") { i++; continue }
        printf "%s%s", (n++ ? " " : ""), $i
      }
    }')"

    # Explicitly export the current SDK and pass it at the final make
    # boundary. PGXS can add PostgreSQL's own flags, but the stale sysroot
    # from the environment is removed.
    export SDKROOT="${sdkroot}"
    export MACOSX_DEPLOYMENT_TARGET="${MACOSX_DEPLOYMENT_TARGET:-$(sw_vers -productVersion | awk -F. '{print $1 "." $2}')}"

    # Homebrew PostgreSQL is built with GNU gettext enabled. libintl.h is
    # therefore required when compiling PostgreSQL extensions such as
    # pg_cron. gettext is keg-only on Homebrew, so its include/lib paths
    # are not necessarily on the default compiler search path.
    local gettext_prefix=""
    if command -v brew >/dev/null 2>&1; then
      gettext_prefix="$(brew --prefix gettext 2>/dev/null || true)"
    fi
    if [[ -z "${gettext_prefix}" || ! -f "${gettext_prefix}/include/libintl.h" ]]; then
      firecrawl_error "Homebrew gettext development files are required for the Homebrew PostgreSQL pg_cron build."
      firecrawl_error "Expected: ${gettext_prefix:-<gettext prefix>}/include/libintl.h"
      return 1
    fi

    firecrawl_log "Using gettext: ${gettext_prefix}"

    export CFLAGS="${base_cflags} -isysroot ${sdkroot}"
    export CPPFLAGS="${base_cppflags} -I${gettext_prefix}/include -isysroot ${sdkroot}"
    export CXXFLAGS="${base_cxxflags} -I${gettext_prefix}/include -isysroot ${sdkroot}"
    # Homebrew gettext is a separate library on macOS. pg_cron references
    # libintl_ngettext(), so the final bundle must explicitly link libintl.
    export LDFLAGS="${base_ldflags} -L${gettext_prefix}/lib -lintl -isysroot ${sdkroot}"

    # Homebrew PostgreSQL's pg_config may itself report a stale sysroot.
    # PGXS may query it while generating its make variables, so provide a
    # tiny pg_config proxy that sanitizes the flag-producing queries.
    local build_pg_config="${FIRECRAWL_BIN_DIR}/pg_config"
    {
      printf '%s\n' '#!/usr/bin/env bash'
      printf 'REAL_PG_CONFIG=%q\n' "${PG_CONFIG}"
      cat <<'PGCONFIG_WRAPPER'
sanitize_flags() {
  printf '%s\n' "$1" | awk '{
    for (i=1;i<=NF;i++) {
      if ($i == "-isysroot") { i++; continue }
      printf "%s%s", (n++ ? " " : ""), $i
    }
  }'
}
case "${1:-}" in
  --cflags|--cppflags|--ldflags)
    out="$("${REAL_PG_CONFIG}" "${1}")"
    out="$(sanitize_flags "${out}")"
    printf '%s -isysroot %s\n' "${out}" "${SDKROOT}"
    ;;
  *)
    exec "${REAL_PG_CONFIG}" "$@"
    ;;
esac
PGCONFIG_WRAPPER
    } > "${build_pg_config}"
    chmod +x "${build_pg_config}"
  else
    local build_pg_config="${FIRECRAWL_BIN_DIR}/pg_config"
    ln -sf "${PG_CONFIG}" "${build_pg_config}"

    # Linux PostgreSQL server headers can be configured with NLS/gettext.
    # Make sure the gettext development header is available before PGXS
    # starts compiling pg_cron. The dependency installer provisions gettext.
    if ! printf '#include <libintl.h>\n' | "${CC:-cc}" -E - >/dev/null 2>&1; then
      firecrawl_error "libintl.h is not available. Install the platform gettext development package."
      return 1
    fi
  fi

  # Explicitly clear inherited compiler flags on non-macOS only when they
  # contain no useful values; on Linux PGXS should use its native defaults.
  make PG_CONFIG="${build_pg_config}" CFLAGS="${CFLAGS:-}" CPPFLAGS="${CPPFLAGS:-}"        CXXFLAGS="${CXXFLAGS:-}" LDFLAGS="${LDFLAGS:-}"
  make PG_CONFIG="${build_pg_config}" CFLAGS="${CFLAGS:-}" CPPFLAGS="${CPPFLAGS:-}"        CXXFLAGS="${CXXFLAGS:-}" LDFLAGS="${LDFLAGS:-}" install

  # PGXS produces a dylib on macOS and a shared object on Linux.
  # Validate the artifact actually generated by the platform.
  local pg_cron_artifact
  if [[ "$(uname -s)" == "Darwin" ]]; then
    pg_cron_artifact="${pkglib}/pg_cron.dylib"
  else
    pg_cron_artifact="${pkglib}/pg_cron.so"
  fi

  if [[ ! -f "${pg_cron_artifact}" ]]; then
    firecrawl_error "pg_cron build/install did not produce ${pg_cron_artifact}."
    return 1
  fi

  firecrawl_log "pg_cron installed successfully: ${pg_cron_artifact}"

}

firecrawl_write_pg_conf() {
  local conf="${FIRECRAWL_POSTGRES_DATA}/postgresql.conf"

  # Keep all project-local PostgreSQL settings in the project data directory.
  # Use the active port selected by the coexistence logic.
  if grep -q '^listen_addresses = ' "${conf}"; then
    sed -i.bak "s#^listen_addresses = .*#listen_addresses = '127.0.0.1'#" "${conf}"
  else
    printf "listen_addresses = '127.0.0.1'\n" >> "${conf}"
  fi

  if grep -q '^port = ' "${conf}"; then
    sed -i.bak "s/^port = .*/port = ${FIRECRAWL_POSTGRES_PORT}/" "${conf}"
  else
    printf 'port = %s\n' "${FIRECRAWL_POSTGRES_PORT}" >> "${conf}"
  fi

  if grep -q '^shared_preload_libraries = ' "${conf}"; then
    sed -i.bak "s/^shared_preload_libraries = .*/shared_preload_libraries = 'pg_cron'/" "${conf}"
  else
    printf "shared_preload_libraries = 'pg_cron'\n" >> "${conf}"
  fi

  if grep -q '^cron.database_name = ' "${conf}"; then
    sed -i.bak "s/^cron.database_name = .*/cron.database_name = 'postgres'/" "${conf}"
  else
    printf "cron.database_name = 'postgres'\n" >> "${conf}"
  fi

  rm -f "${conf}.bak"
}

firecrawl_pg_init() {
  firecrawl_pg_paths

  local bindir
  bindir="$(dirname "${PG_CONFIG}")"
  local pg_ctl="${bindir}/pg_ctl"
  local initdb="${bindir}/initdb"
  local psql="${bindir}/psql"
  local pg_isready="${bindir}/pg_isready"
  local requested_port="${FIRECRAWL_POSTGRES_PORT:-55432}"
  local project_port="${requested_port}"

  # Existing PostgreSQL handling:
  #
  # 1. If PostgreSQL 17 is already listening on the normal local PostgreSQL
  #    port, reuse it. Do NOT start another server.
  # 2. If another PostgreSQL version is listening, leave it untouched and
  #    use the project-local PostgreSQL instance on FIRECRAWL_POSTGRES_PORT.
  # 3. If the project-local port is already occupied, fail clearly rather
  #    than touching an unrelated process.
  #
  # The Firecrawl database itself is still created/configured by this
  # bootstrap. We never stop or modify an unrelated running PostgreSQL
  # server.

  local existing_port="${FIRECRAWL_EXISTING_POSTGRES_PORT:-5432}"
  local existing_version=""
  local existing_pg_config=""

  firecrawl_log "Checking for an existing local PostgreSQL server..."

  if "${pg_isready}" -h 127.0.0.1 -p "${existing_port}" >/dev/null 2>&1; then
    existing_version="$("${psql}" -h 127.0.0.1 -p "${existing_port}" -d postgres -Atqc 'SHOW server_version;' 2>/dev/null || true)"
    existing_pg_config="$("${psql}" -h 127.0.0.1 -p "${existing_port}" -d postgres -Atqc 'SHOW config_file;' 2>/dev/null || true)"

    if [[ "${existing_version}" == 17.* ]]; then
      FIRECRAWL_POSTGRES_PORT="${existing_port}"
      FIRECRAWL_POSTGRES_EXTERNAL=1
      export FIRECRAWL_POSTGRES_PORT FIRECRAWL_POSTGRES_EXTERNAL
      firecrawl_log "Existing PostgreSQL 17 detected on 127.0.0.1:${existing_port}; reusing it."
      [[ -n "${existing_pg_config}" ]] && firecrawl_log "Existing PostgreSQL config: ${existing_pg_config}"

  # pg_cron must be installed in the server we actually use. If this
      # is the Homebrew PostgreSQL 17 server, the extension installed above
      # belongs to this installation.
      local ext_ok
      ext_ok="$("${psql}" -h 127.0.0.1 -p "${existing_port}" -d postgres -Atqc \
        "SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name='pg_cron');" 2>/dev/null || true)"
      if [[ "${ext_ok}" != "t" ]]; then
        firecrawl_error "PostgreSQL 17 is running, but pg_cron is not available in that server."
        firecrawl_error "The bootstrap will not modify or stop your existing PostgreSQL."
        firecrawl_error "Use FIRECRAWL_EXISTING_POSTGRES_PORT with a PostgreSQL 17 instance that has pg_cron, or disable reuse."
        return 1
      fi

      firecrawl_configure_pg_cron
      return 0
    fi

    firecrawl_log "PostgreSQL is already running on 127.0.0.1:${existing_port} (${existing_version:-unknown}). It will not be touched."
  fi

  # Project-local PostgreSQL uses a separate port so it can coexist with a
  # Homebrew PostgreSQL service already listening on 5432.
  if "${pg_isready}" -h 127.0.0.1 -p "${project_port}" >/dev/null 2>&1; then
    # It may be our own previously-started project PostgreSQL.
    local local_version
    local_version="$("${psql}" -h 127.0.0.1 -p "${project_port}" -d postgres -Atqc 'SHOW server_version;' 2>/dev/null || true)"
    if [[ "${local_version}" == 17.* ]]; then
      FIRECRAWL_POSTGRES_PORT="${project_port}"
      FIRECRAWL_POSTGRES_EXTERNAL=0
      export FIRECRAWL_POSTGRES_PORT FIRECRAWL_POSTGRES_EXTERNAL
      firecrawl_log "Project-local PostgreSQL 17 is already running on port ${project_port}; reusing it."
      firecrawl_configure_pg_cron
      return 0
    fi

    firecrawl_error "Port ${project_port} is already occupied by another service."
    firecrawl_error "Set FIRECRAWL_POSTGRES_PORT to a free local port."
    return 1
  fi

  FIRECRAWL_POSTGRES_PORT="${project_port}"
  FIRECRAWL_POSTGRES_EXTERNAL=0
  export FIRECRAWL_POSTGRES_PORT FIRECRAWL_POSTGRES_EXTERNAL

  firecrawl_log "Initializing project-local PostgreSQL 17 on 127.0.0.1:${FIRECRAWL_POSTGRES_PORT}..."

  # Never try to start a data directory created by a different PostgreSQL
  # major version. This can happen when the runtime is reused after changing
  # the Homebrew PostgreSQL package/version.
  if [[ -f "${FIRECRAWL_POSTGRES_DATA}/PG_VERSION" ]]; then
    local data_major
    data_major="$(cat "${FIRECRAWL_POSTGRES_DATA}/PG_VERSION" 2>/dev/null || true)"
    if [[ "${data_major}" != "17" ]]; then
      local stale_dir="${FIRECRAWL_POSTGRES_DATA}.pg${data_major:-unknown}.stale"
      firecrawl_log "Existing project PostgreSQL data directory is version ${data_major:-unknown}; PostgreSQL 17 is required."
      firecrawl_log "Moving stale data directory to ${stale_dir}"
      rm -rf "${stale_dir}"
      mv "${FIRECRAWL_POSTGRES_DATA}" "${stale_dir}"
    fi
  fi

  # On macOS, PostgreSQL can become multithreaded during startup when
  # libintl/setlocale resolves the process locale from an unset/invalid
  # environment. This is especially relevant here because pg_cron is linked
  # with Homebrew gettext. Force a known valid locale for the entire
  # project-local PostgreSQL lifecycle.
  local pg_locale="${FIRECRAWL_PG_LOCALE:-C}"
  export LC_ALL="${pg_locale}"
  export LANG="${pg_locale}"

  if [[ ! -f "${FIRECRAWL_POSTGRES_DATA}/PG_VERSION" ]]; then
    rm -rf "${FIRECRAWL_POSTGRES_DATA}"
    mkdir -p "${FIRECRAWL_POSTGRES_DATA}"

    LC_ALL="${pg_locale}" LANG="${pg_locale}" "${initdb}" \
      -D "${FIRECRAWL_POSTGRES_DATA}" \
      --encoding=UTF8 \
      --locale="${pg_locale}" \
      --auth-local=trust \
      --auth-host=trust
  fi

  firecrawl_write_pg_conf

  local pg_log="${FIRECRAWL_LOG_DIR}/postgres.log"
  mkdir -p "${FIRECRAWL_LOG_DIR}"
  rm -f "${pg_log}"

  firecrawl_log "Starting project-local PostgreSQL on 127.0.0.1:${FIRECRAWL_POSTGRES_PORT}..."

  # -w makes pg_ctl wait for startup and return the real startup status.
  # Capture the result so a PostgreSQL configuration/library failure is shown
  # immediately instead of producing only the generic 'stopped waiting'.
  if ! LC_ALL="${pg_locale}" LANG="${pg_locale}" "${pg_ctl}" \
    -D "${FIRECRAWL_POSTGRES_DATA}" \
    -l "${pg_log}" \
    -o "-p ${FIRECRAWL_POSTGRES_PORT} -h 127.0.0.1" \
    -w start; then
    firecrawl_error "PostgreSQL failed to start on 127.0.0.1:${FIRECRAWL_POSTGRES_PORT}."
    firecrawl_error "PostgreSQL startup log: ${pg_log}"
    if [[ -f "${pg_log}" ]]; then
      echo "---------------- PostgreSQL startup log ----------------" >&2
      tail -100 "${pg_log}" >&2 || true
      echo "--------------------------------------------------------" >&2
    fi
    return 1
  fi

  local ready=0
  for _ in {1..30}; do
    if "${pg_isready}" -h 127.0.0.1 -p "${FIRECRAWL_POSTGRES_PORT}" >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 1
  done

  if [[ "${ready}" != "1" ]]; then
    firecrawl_error "Project-local PostgreSQL did not start."
    firecrawl_error "PostgreSQL log: ${FIRECRAWL_LOG_DIR}/postgres.log"
    tail -50 "${FIRECRAWL_LOG_DIR}/postgres.log" 2>/dev/null || true
    return 1
  fi

  firecrawl_configure_pg_cron
}

firecrawl_configure_pg_cron() {
  local psql
  psql="$(dirname "${PG_CONFIG}")/psql"

  # Firecrawl's NuQ PostgreSQL setup uses the default `postgres` database.
  # pg_cron is allowed in exactly one database per PostgreSQL cluster, and
  # its cron.database_name is configured as `postgres`. Do not create a
  # second database and do not attempt to install pg_cron into it.
  firecrawl_log "Configuring pg_cron on PostgreSQL port ${FIRECRAWL_POSTGRES_PORT}..."

  # The native bootstrap uses trust authentication locally, but Firecrawl
  # itself connects using POSTGRES_USER/POSTGRES_PASSWORD. Create that role
  # explicitly before starting the application. Keep it a superuser because
  # Firecrawl/NuQ performs database-level setup and the official local
  # PostgreSQL container likewise uses its configured POSTGRES_USER as the
  # database administrator.
  # Create/update the Firecrawl login role using shell-expanded SQL values.
  # The values are escaped before being inserted into the SQL string, so no
  # psql :variable substitution is involved at all.
  local role_sql password_sql
  role_sql="$(printf '%s' "${FIRECRAWL_POSTGRES_USER}" | sed "s/'/''/g")"
  password_sql="$(printf '%s' "${FIRECRAWL_POSTGRES_PASSWORD}" | sed "s/'/''/g")"
  "${psql}" -h 127.0.0.1 -p "${FIRECRAWL_POSTGRES_PORT}" -d postgres \
    -v ON_ERROR_STOP=1 \
    -c "DO \$do\$ BEGIN
      IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role_sql}') THEN
        EXECUTE 'ALTER ROLE ' || quote_ident('${role_sql}') || ' WITH LOGIN SUPERUSER PASSWORD ' || quote_literal('${password_sql}');
      ELSE
        EXECUTE 'CREATE ROLE ' || quote_ident('${role_sql}') || ' WITH LOGIN SUPERUSER PASSWORD ' || quote_literal('${password_sql}');
      END IF;
    END \$do\$;" >/dev/null

  # pg_cron must be installed in the database named by cron.database_name.
  "${psql}" -h 127.0.0.1 -p "${FIRECRAWL_POSTGRES_PORT}" -d postgres \
    -v ON_ERROR_STOP=1 \
    -c "CREATE EXTENSION IF NOT EXISTS pg_cron;" >/dev/null

  # Keep Firecrawl/NuQ pointed at the same database as the official
  # self-hosted configuration: POSTGRES_DB=postgres.
  export FIRECRAWL_POSTGRES_DB="postgres"

  firecrawl_log "PostgreSQL 17 + pg_cron ready on 127.0.0.1:${FIRECRAWL_POSTGRES_PORT} (database: postgres, user: ${FIRECRAWL_POSTGRES_USER})"
}

firecrawl_apply_nuq() {
  firecrawl_pg_paths
  local psql
  psql="$(dirname "${PG_CONFIG}")/psql"
  local nuq="${FIRECRAWL_SRC}/apps/nuq-postgres/nuq.sql"

  [[ -f "$nuq" ]] || {
    firecrawl_error "NuQ SQL not found: $nuq"
    return 1
  }

  PGPASSWORD="${FIRECRAWL_POSTGRES_PASSWORD}" \
    "${psql}" \
      -h "${FIRECRAWL_HOST}" \
      -p "${FIRECRAWL_POSTGRES_PORT}" \
      -U "${FIRECRAWL_POSTGRES_USER}" \
      -d "${FIRECRAWL_POSTGRES_DB}" \
      -v ON_ERROR_STOP=1 \
      -c 'CREATE EXTENSION IF NOT EXISTS pg_cron;'

  # Firecrawl v2.11.0's NuQ SQL sets I/O concurrency values that are valid on
  # Linux but invalid on this macOS PostgreSQL build because it lacks
  # posix_fadvise(). Patch every affected setting in a temporary copy.
  local nuq_apply="${FIRECRAWL_CONFIG_DIR}/nuq-native.sql"
  cp "${nuq}" "${nuq_apply}"

  if [[ "$(uname -s)" == "Darwin" ]]; then
    # PostgreSQL on macOS requires both parameters to be zero when
    # posix_fadvise() is unavailable. Keep the Firecrawl source untouched.
    perl -0pi -e '
      s/effective_io_concurrency\s*=\s*200/effective_io_concurrency = 0/g;
      s/maintenance_io_concurrency\s*=\s*100/maintenance_io_concurrency = 0/g;
    ' "${nuq_apply}"

    # Never send an incompatible value to PostgreSQL. Fail before psql if
    # either known macOS-incompatible setting survived the transformation.
    if grep -Eq 'effective_io_concurrency[[:space:]]*=[[:space:]]*200([;[:space:]]|$)' "${nuq_apply}" || \
       grep -Eq 'maintenance_io_concurrency[[:space:]]*=[[:space:]]*100([;[:space:]]|$)' "${nuq_apply}"; then
      firecrawl_error "Failed to patch macOS-incompatible I/O concurrency settings in NuQ SQL."
      return 1
    fi
    firecrawl_log "Applied macOS PostgreSQL compatibility: effective_io_concurrency=0 and maintenance_io_concurrency=0 for NuQ."
  fi

  PGPASSWORD="${FIRECRAWL_POSTGRES_PASSWORD}" \
    "${psql}" \
      -h "${FIRECRAWL_HOST}" \
      -p "${FIRECRAWL_POSTGRES_PORT}" \
      -U "${FIRECRAWL_POSTGRES_USER}" \
      -d "${FIRECRAWL_POSTGRES_DB}" \
      -v ON_ERROR_STOP=1 \
      -f "${nuq_apply}"
}

firecrawl_redis_running() {
  redis-cli -h "${FIRECRAWL_HOST}" -p "${FIRECRAWL_REDIS_PORT}" ping 2>/dev/null | grep -q '^PONG$'
}

firecrawl_redis_config_ok() {
  local dir
  dir="$(redis-cli -h "${FIRECRAWL_HOST}" -p "${FIRECRAWL_REDIS_PORT}" CONFIG GET dir 2>/dev/null | tail -n 1 || true)"
  [[ "${dir}" == "${FIRECRAWL_REDIS_DATA}" ]]
}

firecrawl_redis_reset_persistence_errors() {
  # Firecrawl uses Redis as a queue/cache. For this native local runtime,
  # keep durable AOF persistence but disable RDB snapshots. RDB snapshot
  # failures on a nearly-full macOS volume otherwise trigger Redis's
  # stop-writes-on-bgsave-error protection and kill Firecrawl workers.
  redis-cli -h "${FIRECRAWL_HOST}" -p "${FIRECRAWL_REDIS_PORT}" \
    CONFIG SET save "" >/dev/null 2>&1 || true
  redis-cli -h "${FIRECRAWL_HOST}" -p "${FIRECRAWL_REDIS_PORT}" \
    CONFIG SET stop-writes-on-bgsave-error no >/dev/null 2>&1 || true
}

firecrawl_redis_start() {
  local redis=""
  if command -v redis-server >/dev/null 2>&1; then
    redis="$(command -v redis-server)"
  elif [[ "$(uname -s)" == "Darwin" ]] && command -v brew >/dev/null 2>&1; then
    redis="$(brew --prefix redis)/bin/redis-server"
  fi
  [[ -x "$redis" ]] || { firecrawl_error "redis-server not found"; return 1; }

  mkdir -p "${FIRECRAWL_REDIS_DATA}" "${FIRECRAWL_LOG_DIR}" "${FIRECRAWL_STATE_DIR}"

  # Port 6380 is owned by this project. Reuse a healthy project Redis;
  # restart a stale/misconfigured Redis on this port so the bootstrap is
  # deterministic. Never touch the user's other Redis instances.
  if firecrawl_redis_running; then
    if firecrawl_redis_config_ok; then
      firecrawl_redis_reset_persistence_errors
      firecrawl_log "Redis already running on port ${FIRECRAWL_REDIS_PORT}; reusing project-local instance."
      return 0
    fi

    firecrawl_log "Redis is running on port ${FIRECRAWL_REDIS_PORT} with an unexpected data directory; restarting the project-local instance."
    redis-cli -h "${FIRECRAWL_HOST}" -p "${FIRECRAWL_REDIS_PORT}" shutdown nosave >/dev/null 2>&1 || true
    for _ in $(seq 1 15); do
      firecrawl_redis_running || break
      sleep 1
    done
  fi

  cat > "${FIRECRAWL_CONFIG_DIR}/redis.conf" <<EOF
bind ${FIRECRAWL_HOST}
port ${FIRECRAWL_REDIS_PORT}
dir ${FIRECRAWL_REDIS_DATA}
dbfilename dump.rdb
save ""
appendonly yes
appendfilename appendonly.aof
appendfsync everysec
stop-writes-on-bgsave-error no
daemonize yes
pidfile ${FIRECRAWL_STATE_DIR}/redis.pid
logfile ${FIRECRAWL_LOG_DIR}/redis.log
EOF

  firecrawl_log "Starting project-local Redis on 127.0.0.1:${FIRECRAWL_REDIS_PORT}..."
  "$redis" "${FIRECRAWL_CONFIG_DIR}/redis.conf"

  for _ in $(seq 1 30); do
    if firecrawl_redis_running; then
      firecrawl_redis_reset_persistence_errors
      if firecrawl_redis_config_ok; then
        firecrawl_log "Redis is ready on 127.0.0.1:${FIRECRAWL_REDIS_PORT} using ${FIRECRAWL_REDIS_DATA}."
        return 0
      fi
    fi
    sleep 1
  done

  firecrawl_error "Redis did not become ready."
  if [[ -f "${FIRECRAWL_LOG_DIR}/redis.log" ]]; then
    tail -50 "${FIRECRAWL_LOG_DIR}/redis.log" >&2 || true
  fi
  return 1
}

firecrawl_rabbitmq_start() {
  command -v rabbitmq-server >/dev/null 2>&1 || {
    firecrawl_error "rabbitmq-server not found"
    return 1
  }

  if command -v rabbitmq-diagnostics >/dev/null 2>&1 \
      && rabbitmq-diagnostics -q -n "firecrawl@localhost" check_running >/dev/null 2>&1; then
    return 0
  fi

  export RABBITMQ_NODENAME="firecrawl@localhost"
  export RABBITMQ_NODE_PORT="${FIRECRAWL_RABBITMQ_PORT}"
  export RABBITMQ_MNESIA_BASE="${FIRECRAWL_RABBITMQ_DATA}/mnesia"
  export RABBITMQ_LOG_BASE="${FIRECRAWL_LOG_DIR}/rabbitmq"
  export RABBITMQ_PID_FILE="${FIRECRAWL_STATE_DIR}/rabbitmq.pid"
  export RABBITMQ_ENABLED_PLUGINS_FILE="${FIRECRAWL_CONFIG_DIR}/rabbitmq-enabled-plugins"

  mkdir -p "${RABBITMQ_MNESIA_BASE}" "${RABBITMQ_LOG_BASE}"

  rabbitmq-server -detached

  for _ in $(seq 1 60); do
    if rabbitmq-diagnostics -q -n "${RABBITMQ_NODENAME}" check_running >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  rabbitmq-diagnostics -q -n "${RABBITMQ_NODENAME}" check_running >/dev/null 2>&1 || {
    firecrawl_error "RabbitMQ did not become ready."
    return 1
  }

  # Local-only broker credentials.
  rabbitmqctl -n "${RABBITMQ_NODENAME}" add_user "${FIRECRAWL_POSTGRES_USER}" "${FIRECRAWL_POSTGRES_PASSWORD}" >/dev/null 2>&1 || true
  rabbitmqctl -n "${RABBITMQ_NODENAME}" set_permissions -p / "${FIRECRAWL_POSTGRES_USER}" ".*" ".*" ".*" >/dev/null 2>&1 || true
}

firecrawl_write_env() {
  cat > "${FIRECRAWL_CONFIG_DIR}/firecrawl.env" <<EOF
ENV=local
HOST=${FIRECRAWL_HOST}
PORT=${FIRECRAWL_PORT}
INTERNAL_PORT=${FIRECRAWL_PORT}
USE_DB_AUTHENTICATION=false
NUM_WORKERS_PER_QUEUE=4
CRAWL_CONCURRENT_REQUESTS=5
MAX_CONCURRENT_JOBS=3
BROWSER_POOL_SIZE=3
REDIS_URL=redis://${FIRECRAWL_HOST}:${FIRECRAWL_REDIS_PORT}
REDIS_RATE_LIMIT_URL=redis://${FIRECRAWL_HOST}:${FIRECRAWL_REDIS_PORT}
PLAYWRIGHT_MICROSERVICE_URL=http://${FIRECRAWL_HOST}:${FIRECRAWL_PLAYWRIGHT_PORT}/scrape
POSTGRES_USER=${FIRECRAWL_POSTGRES_USER}
POSTGRES_PASSWORD=${FIRECRAWL_POSTGRES_PASSWORD}
POSTGRES_DB=${FIRECRAWL_POSTGRES_DB}
POSTGRES_HOST=${FIRECRAWL_HOST}
POSTGRES_PORT=${FIRECRAWL_POSTGRES_PORT}
NUQ_DATABASE_URL=postgresql://${FIRECRAWL_POSTGRES_USER}:${FIRECRAWL_POSTGRES_PASSWORD}@${FIRECRAWL_HOST}:${FIRECRAWL_POSTGRES_PORT}/${FIRECRAWL_POSTGRES_DB}
NUQ_RABBITMQ_URL=amqp://${FIRECRAWL_POSTGRES_USER}:${FIRECRAWL_POSTGRES_PASSWORD}@${FIRECRAWL_HOST}:${FIRECRAWL_RABBITMQ_PORT}
NUQ_BACKEND=pg
PLAYWRIGHT_BROWSERS_PATH=${FIRECRAWL_PLAYWRIGHT_CACHE}
LOGGING_LEVEL=info
EOF
}

firecrawl_build_native() {
  local native_dir="${FIRECRAWL_SRC}/apps/api/native"
  local native_pkg="${native_dir}/package.json"
  local native_module="${native_dir}/index.js"

  [[ -f "${native_pkg}" ]] || {
    firecrawl_error "Firecrawl native package is missing: ${native_pkg}"
    return 1
  }

  firecrawl_log "Building Firecrawl Rust native module (@mendable/firecrawl-rs)..."
  (
    cd "${native_dir}"
    # The API dependency install intentionally uses --ignore-scripts because
    # foundationdb@2.0.1 is not needed for the PostgreSQL/NuQ backend. That
    # also suppresses this workspace package's install/build hook, so build
    # the Rust N-API addon explicitly here.
    pnpm install --frozen-lockfile --ignore-scripts 2>/dev/null || \
      pnpm install --ignore-scripts
    pnpm run build
  )

  # napi-rs generates the platform .node addon alongside the JS wrapper.
  # Validate the native N-API addon for the actual host architecture.
  # Apple Silicon must use darwin-arm64; Intel macOS uses darwin-x64.
  # Do not assume x64 on Darwin.
  local native_target=""
  case "$(uname -s):$(uname -m)" in
    Darwin:arm64)
      native_target="darwin-arm64"
      ;;
    Darwin:x86_64)
      native_target="darwin-x64"
      ;;
    Linux:x86_64|Linux:amd64)
      native_target="linux-x64-gnu"
      ;;
    Linux:aarch64|Linux:arm64)
      native_target="linux-arm64-gnu"
      ;;
    *)
      firecrawl_error "Unsupported native addon platform: $(uname -s) $(uname -m)"
      return 1
      ;;
  esac

  local expected_native_addon="${native_dir}/firecrawl-rs.${native_target}.node"
  if [[ ! -f "${expected_native_addon}" ]]; then
    firecrawl_error "Rust native build completed without the ${native_target} firecrawl-rs addon."
    firecrawl_error "Expected: ${expected_native_addon}"
    return 1
  fi

  [[ -f "${native_module}" ]] || {
    firecrawl_error "Firecrawl native package wrapper was not generated: ${native_module}"
    return 1
  }

  firecrawl_log "Firecrawl Rust native module is ready."
}

firecrawl_build_api() {
  cd "${FIRECRAWL_SRC}/apps/api"
  firecrawl_log "Building Firecrawl API..."
  pnpm run build
}

firecrawl_build_playwright() {
  cd "${FIRECRAWL_SRC}/apps/playwright-service-ts"
  firecrawl_log "Building Firecrawl Playwright service..."
  pnpm run build
}

firecrawl_playwright_running() {
  lsof -nP -iTCP:"${FIRECRAWL_PLAYWRIGHT_PORT}" -sTCP:LISTEN 2>/dev/null |
    grep -q LISTEN
}

firecrawl_start_playwright() {
  # The v2.11.0 Playwright /health endpoint reports browser-internal health.
  # On this native macOS setup it can return HTTP 503 even while the service
  # itself is correctly running and listening. Use the listening socket as
  # the startup/readiness check and let Firecrawl exercise the browser when
  # an actual scrape is requested.
  if firecrawl_playwright_running; then
    firecrawl_log "Playwright service already running on port ${FIRECRAWL_PLAYWRIGHT_PORT}; reusing it."
    return 0
  fi

  cd "${FIRECRAWL_SRC}/apps/playwright-service-ts"
  set -a
  # shellcheck disable=SC1090
  source "${FIRECRAWL_CONFIG_DIR}/firecrawl.env"
  set +a
  export PORT="${FIRECRAWL_PLAYWRIGHT_PORT}"
  export HOST="${FIRECRAWL_HOST}"
  export PLAYWRIGHT_BROWSERS_PATH="${FIRECRAWL_PLAYWRIGHT_CACHE}"

  nohup node dist/api.js \
    >> "${FIRECRAWL_LOG_DIR}/playwright.log" 2>&1 &
  echo $! > "${FIRECRAWL_STATE_DIR}/playwright.pid"

  for _ in $(seq 1 60); do
    if firecrawl_playwright_running; then
      firecrawl_log "Playwright service is listening on port ${FIRECRAWL_PLAYWRIGHT_PORT}."
      return 0
    fi
    sleep 1
  done

  firecrawl_error "Playwright service did not start listening on port ${FIRECRAWL_PLAYWRIGHT_PORT}."
  return 1
}

firecrawl_start_api() {
  if curl -fsS --max-time 2 \
      "http://${FIRECRAWL_HOST}:${FIRECRAWL_PORT}/v0/health/liveness" >/dev/null 2>&1; then
    firecrawl_log "Firecrawl API already healthy on port ${FIRECRAWL_PORT}; reusing it."
    return 0
  fi

  cd "${FIRECRAWL_SRC}/apps/api"
  set -a
  # shellcheck disable=SC1090
  source "${FIRECRAWL_CONFIG_DIR}/firecrawl.env"
  set +a

  firecrawl_log "Starting Firecrawl API on 127.0.0.1:${FIRECRAWL_PORT}..."
  nohup node dist/src/harness.js --start-built \
    >> "${FIRECRAWL_LOG_DIR}/api.log" 2>&1 &
  local api_pid=$!
  echo "${api_pid}" > "${FIRECRAWL_STATE_DIR}/api.pid"

  for _ in $(seq 1 120); do
    if curl -fsS --max-time 2 \
      "http://${FIRECRAWL_HOST}:${FIRECRAWL_PORT}/v0/health/liveness" >/dev/null 2>&1; then
      firecrawl_log "Firecrawl API is healthy on port ${FIRECRAWL_PORT}."
      return 0
    fi

    if ! kill -0 "${api_pid}" 2>/dev/null; then
      firecrawl_error "Firecrawl API process exited during startup (PID ${api_pid})."
      if [[ -f "${FIRECRAWL_LOG_DIR}/api.log" ]]; then
        echo "---------------- Firecrawl API startup log ----------------" >&2
        tail -100 "${FIRECRAWL_LOG_DIR}/api.log" >&2 || true
        echo "-----------------------------------------------------------" >&2
      fi
      return 1
    fi
    sleep 1
  done

  firecrawl_error "Firecrawl API did not become ready."
  if [[ -f "${FIRECRAWL_LOG_DIR}/api.log" ]]; then
    echo "---------------- Firecrawl API log ----------------" >&2
    tail -100 "${FIRECRAWL_LOG_DIR}/api.log" >&2 || true
    echo "---------------------------------------------------" >&2
  fi
  return 1
}

prepare_firecrawl() {
  firecrawl_prepare_dirs
  firecrawl_ensure_node_pnpm || return 1
  firecrawl_ensure_rust || return 1
  firecrawl_require git || return 1
  firecrawl_clone || return 1
  firecrawl_install_js || return 1
  firecrawl_build_pg_cron || return 1
  firecrawl_pg_init || return 1
  firecrawl_apply_nuq || return 1
  firecrawl_build_native || return 1
  firecrawl_redis_start || return 1
  firecrawl_rabbitmq_start || return 1
  firecrawl_write_env
  firecrawl_build_api || return 1
  firecrawl_build_playwright || return 1
  firecrawl_start_playwright || return 1
  firecrawl_start_api || return 1
  firecrawl_log "Firecrawl native stack is ready."
}

firecrawl_stop() {
  firecrawl_log "Stopping Firecrawl services..."

  for f in api playwright; do
    local pf="${FIRECRAWL_STATE_DIR}/${f}.pid"
    if [[ -f "${pf}" ]]; then
      local pid
      pid="$(cat "${pf}" 2>/dev/null || true)"

      if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
        firecrawl_log "Stopping ${f} (PID ${pid})..."
        kill "${pid}" 2>/dev/null || true

        for _ in $(seq 1 10); do
          kill -0 "${pid}" 2>/dev/null || break
          sleep 1
        done

        kill -9 "${pid}" 2>/dev/null || true
      fi

      rm -f "${pf}"
    fi
  done

  if command -v redis-cli >/dev/null 2>&1; then
    redis-cli -h "${FIRECRAWL_HOST}" -p "${FIRECRAWL_REDIS_PORT}" shutdown nosave >/dev/null 2>&1 || true
  fi

  # Only stop the project-local PostgreSQL data directory. If Firecrawl
  # reused an external PostgreSQL 17 server, do not touch that server.
  if [[ "${FIRECRAWL_POSTGRES_EXTERNAL:-0}" != "1" ]] && command -v pg_ctl >/dev/null 2>&1; then
    pg_ctl -D "${FIRECRAWL_POSTGRES_DATA}" stop -m fast >/dev/null 2>&1 || true
  fi

  if command -v rabbitmqctl >/dev/null 2>&1; then
    rabbitmqctl -n "${RABBITMQ_NODENAME:-firecrawl@localhost}" stop >/dev/null 2>&1 || true
  fi

  firecrawl_log "Firecrawl services stopped."
}

# ------------------------------------------------------------
# Optional direct CLI usage
# ------------------------------------------------------------
# bootstrap.sh sources this file, so do not execute a command when sourced.
# Direct usage is supported for lifecycle operations:
#   bash bootstrap/firecrawl.sh stop
#   bash bootstrap/firecrawl.sh start
# ------------------------------------------------------------
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  case "${1:-start}" in
    start)
      prepare_firecrawl
      ;;
    stop)
      firecrawl_stop
      ;;
    restart)
      firecrawl_stop
      prepare_firecrawl
      ;;
    *)
      echo "Usage: bash ${BASH_SOURCE[0]} {start|stop|restart}" >&2
      exit 2
      ;;
  esac
fi
