# LLM Connector Gateway Bootstrap — Native Firecrawl / No Docker

This package contains the complete bootstrap tree, including the existing
llama.cpp/model/SearXNG modules plus the native Firecrawl module.

## Supported native environments

- macOS Intel
- macOS Apple Silicon
- Debian/Ubuntu Linux on x86_64 or ARM64

Firecrawl is pinned to `v2.11.0` and uses PostgreSQL/NuQ (`NUQ_BACKEND=pg`).
No Docker is used.

## Project-local Firecrawl runtime

All Firecrawl-owned source, build state, PostgreSQL data, Redis data, RabbitMQ
state, Playwright browsers, pnpm store, configuration and logs are placed under:

```text
runtime/firecrawl/
```

System package managers such as Homebrew/apt and their system-installed
PostgreSQL/Node/RabbitMQ/Redis binaries necessarily remain outside the project.

## Full bootstrap

From the project root:

```bash
bash bootstrap/bootstrap.sh
```

Firecrawl is enabled by default. To skip Firecrawl for a run:

```bash
FIRECRAWL_ENABLED=0 bash bootstrap/bootstrap.sh
```

## Firecrawl-only functional test

```bash
bash test/firecrawl.sh
```

The test checks the native dependencies and services and performs a real:

```text
POST http://127.0.0.1:3002/v2/scrape
https://example.com
```

The test must receive a markdown result before reporting success.

## Important implementation details

- Firecrawl API dependencies are installed from `apps/api` with
  `--frozen-lockfile --ignore-scripts` so the unused FoundationDB native
  install script does not break the PostgreSQL/NuQ configuration.
- Playwright dependencies are installed separately from
  `apps/playwright-service-ts`.
- Chromium is installed into `runtime/firecrawl/playwright`.
- pg_cron is built with PostgreSQL PGXS using the selected PostgreSQL 17
  `pg_config`.
- On macOS, stale SDK paths embedded in a Homebrew PostgreSQL `pg_config` are
  removed and replaced with the SDK reported by `xcrun`.
- pg_cron is pinned to v1.6.7, which includes PostgreSQL 17 compatibility.
- PostgreSQL's Firecrawl/NuQ database is `postgres`, matching Firecrawl's
  self-host configuration and pg_cron's target database.
- Services bind to `127.0.0.1` by default.


## pg_cron portability fix

The native pg_cron build does not hard-code a macOS SDK version. On macOS,
the bootstrap obtains the active SDK from `xcrun --sdk macosx --show-sdk-path`
and removes stale `-isysroot` flags that may have been embedded in the
PostgreSQL installation's `pg_config` output. The extension is still built
through PostgreSQL PGXS and the selected `pg_config`.

On Linux, the same pg_cron build uses the selected PostgreSQL 17 `pg_config`
without any macOS-specific logic.

For a clean test on an existing checkout:

    rm -rf runtime/firecrawl
    bash test/firecrawl.sh

Then run the complete bootstrap:

    bash bootstrap/bootstrap.sh


## pg_cron + gettext

Homebrew's PostgreSQL 17 is built with gettext/NLS support, so compiling
pg_cron also requires Homebrew gettext's `libintl.h`. The bootstrap now
ensures gettext is installed and explicitly adds its include/library paths
on macOS. Linux validates that `libintl.h` is available and provisions
gettext through the package manager.


## pg_cron macOS linker fix

On macOS, Homebrew gettext's `libintl` is a separate library. The native
pg_cron build therefore links with `-L$(brew --prefix gettext)/lib -lintl`.
The bootstrap also recognizes the macOS PGXS output name `pg_cron.dylib`;
Linux continues to use `pg_cron.so`.


### v5 fix
macOS Homebrew PGXS successfully builds and installs `pg_cron.dylib`. The previous validation incorrectly required `pg_cron.so` and therefore reported a false failure after a successful install. v5 validates `pg_cron.dylib` on macOS and `pg_cron.so` on Linux.


## Existing PostgreSQL handling (v6)

The bootstrap no longer blindly starts a second PostgreSQL server.

- If PostgreSQL 17 is already listening on `127.0.0.1:5432`, it is reused.
- The bootstrap never stops or modifies an unrelated PostgreSQL server.
- If another PostgreSQL version is already using `5432`, the bootstrap leaves
  it alone and starts its own PostgreSQL 17 on `127.0.0.1:55432`.
- `FIRECRAWL_EXISTING_POSTGRES_PORT` can override the port used for detection.
- `FIRECRAWL_POSTGRES_PORT` can choose the project-local port.
- A project-local PostgreSQL instance is stored under `runtime/firecrawl/`.
- The bootstrap verifies that pg_cron is available before reusing an external
  PostgreSQL 17 instance.


### v7 PostgreSQL coexistence behavior

The bootstrap now distinguishes three cases:

1. PostgreSQL 17 is running on `127.0.0.1:5432` and already has pg_cron
   preloaded: reuse it without restarting or changing it.
2. PostgreSQL 17 is running but pg_cron is not preloaded: leave that server
   untouched and use the project-local PostgreSQL instance on `55432`.
3. PostgreSQL 16/15/etc. (or another service) is using `5432`: leave it
   untouched and use project-local PostgreSQL 17 on `55432`.

This prevents the Firecrawl bootstrap from taking over an existing Homebrew
database service.


### v8 fix
Fixed an undefined `FIRECRAWL_RUNTIME` variable in the PostgreSQL startup error-log path. The script now uses the canonical project log/runtime variables and also provides a compatibility alias to prevent `set -u` failures.


### v9 fix
Corrected the remaining PostgreSQL startup log references to the canonical `FIRECRAWL_LOG_DIR`; this removes the `set -u` `FIRECRAWL_RUNTIME: unbound variable` failure.
