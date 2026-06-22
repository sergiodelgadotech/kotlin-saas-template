#!/usr/bin/env bash
# Launch the app locally via bootRun.
#
# Sets STARTER_PATH so the composite build resolves kotlin-saas-starter from the
# local clone instead of GitHub Packages. This is required from a deep sbx
# worktree, where settings.gradle.kts's relative `../kotlin-saas-starter`
# fallback doesn't resolve (it lands one level above the worktree, not the
# canonical repo's sibling). Override by exporting STARTER_PATH yourself.
#
# The `local` Spring profile is the default (spring.profiles.default in
# application.yml), so no --args flag is needed. Extra args pass through, e.g.
#   ./bootrun.sh --args='--spring.profiles.active=local,stripe'
set -euo pipefail
cd "$(dirname "$0")"
export STARTER_PATH="${STARTER_PATH:-/var/home/serandel/Projects/kotlin-saas-starter}"

# Clear any stale compose stack first. In an sbx sandbox the previous run's
# containers get idle-killed except zitadel-login (restart: unless-stopped). That
# leftover makes Spring Boot's docker-compose lifecycle log "services already
# running, skipping startup" and never start Postgres/Redis/Zitadel, so the app
# then fails with connection-refused. Starting from a clean stack avoids it.
docker compose down --remove-orphans >/dev/null 2>&1 || true

exec ./gradlew :app:bootRun "$@"
