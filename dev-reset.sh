#!/usr/bin/env bash
set -euo pipefail

docker compose down -v
rm -f docker/zitadel-init/.local-client.properties \
      docker/zitadel-init/.local-management.properties \
      docker/zitadel-init/management-api.pat \
      docker/zitadel-init/.smtp-configured

# Spring Boot reads spring.config.import files before starting Docker Compose,
# so the generated properties must exist before bootRun is called.
# Start only zitadel-init and its dependencies (postgres + zitadel), wait for
# the files to be written, then bring them back down so bootRun starts clean.
docker compose up -d zitadel-init
docker compose wait zitadel-init
docker compose down
