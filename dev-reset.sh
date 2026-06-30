#!/usr/bin/env bash
set -euo pipefail

docker compose down -v
# Podman sometimes leaves the zitadel-init container in an Exited state after
# `down -v`, which keeps zitadel-init-keys locked. The deferred volume removal
# then kills the *next* Zitadel container that reuses the volume, causing
# zitadel-init to time out waiting for OIDC. Remove the container first, then
# the volume, so the next `up` always gets a fresh key pair.
docker ps -a -q --filter "name=-zitadel-init-" | xargs -r docker rm 2>/dev/null || true
docker volume ls -q | grep '_zitadel-init-keys$' | xargs -r docker volume rm 2>/dev/null || true
rm -f docker/zitadel-init/.local-client.properties \
      docker/zitadel-init/.local-management.properties \
      docker/zitadel-init/management-api.pat \
      docker/zitadel-init/.smtp-configured

# Spring Boot reads spring.config.import files before starting Docker Compose,
# so the generated properties must exist before bootRun is called.
# Start only zitadel-init and its dependencies (postgres + zitadel), wait for
# the files to be written, then bring them back down so bootRun starts clean.
docker compose up -d --remove-orphans zitadel-init
docker compose wait zitadel-init
docker compose down
