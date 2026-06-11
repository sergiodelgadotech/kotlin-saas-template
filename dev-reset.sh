#!/usr/bin/env bash
set -euo pipefail

docker compose down -v
rm -f docker/zitadel-init/.local-client.properties \
      docker/zitadel-init/.local-management.properties \
      docker/zitadel-init/management-api.pat

# Spring Boot reads spring.config.import files before starting Docker Compose,
# so the generated properties must exist before bootRun is called.
# Start services now and wait for zitadel-init to finish writing the files.
docker compose up -d
docker compose wait zitadel-init
