#!/usr/bin/env bash
set -euo pipefail

docker compose down -v
rm -f docker/zitadel-init/.local-client.properties \
      docker/zitadel-init/.local-management.properties \
      docker/zitadel-init/management-api.pat
