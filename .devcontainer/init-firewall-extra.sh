#!/bin/bash
# Extends the claude-code feature's init-firewall.sh with project-specific domains.
# Runs after the feature's script has created the 'allowed-domains' ipset.
# Add new entries here when a tool is blocked; then rebuild the container.
#
# Project domains                            Reason
# -------------------------------------------------------
EXTRA_DOMAINS=(
  "repo.maven.apache.org"                  # Maven Central — Gradle deps
  "repo1.maven.org"                        # Maven Central mirror
  "central.sonatype.com"                   # Maven Central API
  "context7.com"                           # Context7 MCP — doc lookups
  "mcp.context7.com"                       # Context7 MCP API endpoint
  "api.resend.com"                         # Resend — email
  "api.stripe.com"                         # Stripe — payments
  "sentry.io"                              # Sentry — observability
  "pypi.org"                               # PyPI — zitadel-init pip install
  "files.pythonhosted.org"                 # PyPI file downloads
  "sdkman.io"                              # SDKMAN — SDK updates inside container
  "broker.sdkman.io"
  "api.sdkman.io"
)

for DOMAIN in "${EXTRA_DOMAINS[@]}"; do
  [[ "$DOMAIN" == \#* ]] && continue
  IPS=$(getent ahosts "$DOMAIN" 2>/dev/null | awk '{print $1}' | grep -E '^[0-9]+\.' | sort -u || true)
  for IP in $IPS; do
    ipset add allowed-domains "$IP/32" 2>/dev/null || true
  done
done
