#!/bin/bash
# Egress firewall for the kotlin-saas-template dev container.
# Installed via onCreateCommand so it runs after devcontainer features
# (the claude-code feature v1.0.5 overwrites this path during image build).
#
# Design: tolerates individual DNS resolution failures — a blocked telemetry
# domain must not abort the whole firewall setup.
#
# ADDING DOMAINS: add to the relevant section below, then run
#   sudo /usr/local/bin/init-firewall.sh
# or rebuild the container to reload. No image rebuild required.

set -euo pipefail

# ── Allowlist ────────────────────────────────────────────────────────────────
# Format: "domain"  # reason
ALLOWED_DOMAINS=(
  # Anthropic / Claude Code
  "api.anthropic.com"
  "console.anthropic.com"
  "statsig.anthropic.com"          # telemetry — may not resolve on all networks
  "statsig.com"

  # GitHub — git, API, releases, packages, registry
  "github.com"
  "api.github.com"
  "codeload.github.com"
  "raw.githubusercontent.com"
  "objects.githubusercontent.com"
  "uploads.github.com"
  "maven.pkg.github.com"
  "ghcr.io"

  # npm — Node.js packages (Claude Code feature installs via npm)
  "registry.npmjs.org"

  # Maven Central — Gradle dependency resolution
  "repo.maven.apache.org"
  "repo1.maven.org"
  "central.sonatype.com"

  # VS Code remote server + extension marketplace
  "marketplace.visualstudio.com"
  "vscode.dev"
  "update.code.visualstudio.com"
  "vscode.blob.core.windows.net"
  "az764295.vo.msecnd.net"
  "download.visualstudio.microsoft.com"

  # Context7 MCP — doc lookups (mcp.context7.com is the API endpoint)
  "context7.com"
  "mcp.context7.com"

  # Project services
  "api.stripe.com"
  "api.resend.com"
  "sentry.io"
  "o4504408485797888.ingest.sentry.io"

  # PyPI — zitadel-init container installs cryptography via pip
  "pypi.org"
  "files.pythonhosted.org"

  # SDKMAN — Java/Gradle SDK management inside the container
  "sdkman.io"
  "broker.sdkman.io"
  "api.sdkman.io"
)
# ─────────────────────────────────────────────────────────────────────────────

log()  { echo "  $*"; }
warn() { echo "  WARN: $*" >&2; }

echo "==> Configuring egress firewall..."

# Destroy and recreate the ipset
ipset destroy allowed-domains 2>/dev/null || true
ipset create allowed-domains hash:net

# ── GitHub published IP ranges ───────────────────────────────────────────────
log "Fetching GitHub IP ranges..."
GITHUB_META=$(curl -sS --max-time 10 https://api.github.com/meta 2>/dev/null || true)
if [ -n "$GITHUB_META" ]; then
  while IFS= read -r CIDR; do
    ipset add allowed-domains "$CIDR" 2>/dev/null || true
  done < <(echo "$GITHUB_META" | grep -oE '"[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+/[0-9]+"' | tr -d '"')
else
  warn "Could not fetch GitHub IP ranges — GitHub-hosted runners may be blocked"
fi

# ── Resolve each domain, tolerating individual failures ─────────────────────
log "Resolving allowed domains..."
for DOMAIN in "${ALLOWED_DOMAINS[@]}"; do
  IPS=$(getent ahosts "$DOMAIN" 2>/dev/null | awk '{print $1}' | grep -E '^[0-9]+\.' | sort -u || true)
  if [ -z "$IPS" ]; then
    warn "Could not resolve $DOMAIN — skipping"
    continue
  fi
  for IP in $IPS; do
    ipset add allowed-domains "$IP/32" 2>/dev/null || true
  done
done

# ── Add the container's own subnet (compose service-to-service traffic) ──────
CONTAINER_SUBNET=$(ip route | awk '/^[0-9]/ && /\// {print $1; exit}' || true)
[ -n "$CONTAINER_SUBNET" ] && ipset add allowed-domains "$CONTAINER_SUBNET" 2>/dev/null || true

# ── Apply iptables rules ─────────────────────────────────────────────────────
iptables -F OUTPUT 2>/dev/null || true

iptables -A OUTPUT -o lo -j ACCEPT
iptables -A OUTPUT -p udp --dport 53 -j ACCEPT
iptables -A OUTPUT -p tcp --dport 53 -j ACCEPT
iptables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
iptables -A OUTPUT -m set --match-set allowed-domains dst -j ACCEPT
iptables -A OUTPUT -j REJECT --reject-with icmp-port-unreachable

# ── Verify ───────────────────────────────────────────────────────────────────
if curl -sS --max-time 3 https://example.com &>/dev/null; then
  warn "example.com is reachable — firewall may not have applied"
else
  log "OK: example.com is blocked"
fi
if curl -sS --max-time 5 https://api.github.com/zen &>/dev/null; then
  log "OK: api.github.com is reachable"
else
  warn "api.github.com is blocked — check GitHub IP resolution above"
fi

echo "==> Egress firewall active."
