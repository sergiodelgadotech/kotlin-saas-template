#!/bin/bash
# Egress firewall for the kotlin-saas-template dev container.
# Ported from Anthropic's reference at https://github.com/anthropics/claude-code/blob/main/.devcontainer/init-firewall.sh
#
# Blocks all outbound traffic except the domains listed below.
# Runs as postStartCommand (via sudo) so the network is reachable when this runs.
#
# ADDING NEW DOMAINS: add to ALLOWED_DOMAINS below, then rebuild the container
# (Dev Containers: Rebuild Container). Domains to add are typically discovered
# when a tool silently fails — check with: curl -v --max-time 3 https://the-domain.com
#
# =====================================================================
# ALLOWLIST — one entry per line, comment indicates why it's needed
# =====================================================================
ALLOWED_DOMAINS=(
  # Anthropic / Claude Code
  "api.anthropic.com"
  "statsig.anthropic.com"
  "console.anthropic.com"
  "claude.ai"

  # GitHub — git, API, releases
  "github.com"
  "api.github.com"
  "codeload.github.com"
  "raw.githubusercontent.com"
  "objects.githubusercontent.com"
  "uploads.github.com"

  # GitHub Packages — Maven (kotlin-saas-starter) + container registry (Zitadel image)
  "maven.pkg.github.com"
  "ghcr.io"
  "pkg.github.com"

  # npm — Claude Code feature installs Node packages
  "registry.npmjs.org"

  # Maven Central — Gradle dependency resolution
  "repo.maven.apache.org"
  "repo1.maven.org"
  "central.sonatype.com"

  # VS Code remote server + extensions
  "marketplace.visualstudio.com"
  "vscode.dev"
  "update.code.visualstudio.com"
  "vscode.blob.core.windows.net"
  "az764295.vo.msecnd.net"
  "download.visualstudio.microsoft.com"

  # Context7 MCP server — doc lookups for libraries/frameworks
  "context7.com"
  "mcp.context7.com"

  # Stripe — payments
  "api.stripe.com"

  # Resend — email
  "api.resend.com"

  # Sentry — observability
  "sentry.io"
  "o4504408485797888.ingest.sentry.io"

  # PyPI — zitadel-init container installs cryptography package
  "pypi.org"
  "files.pythonhosted.org"
)

# =====================================================================
# Implementation — adapted from Anthropic reference (init-firewall.sh)
# =====================================================================

echo "==> Configuring egress firewall..."

# Preserve any existing NAT rules that container networking relies on
# (Podman/Docker set up masquerading on the compose bridge before this runs)
EXISTING_NAT_RULES=$(iptables -t nat -S 2>/dev/null | grep -v "^-P" || true)

# Flush existing rules
iptables -F OUTPUT 2>/dev/null || true
iptables -X 2>/dev/null || true

# Destroy and recreate the ipset for our allowed IPs
ipset destroy allowed-domains 2>/dev/null || true
ipset create allowed-domains hash:net

# ---- Add GitHub's published IP ranges ----
echo "  -> Fetching GitHub IP ranges..."
GITHUB_META=$(curl -sS --max-time 10 https://api.github.com/meta 2>/dev/null || echo "{}")
for CIDR in $(echo "$GITHUB_META" | grep -oE '"[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+/[0-9]+"' | tr -d '"'); do
  ipset add allowed-domains "$CIDR" 2>/dev/null || true
done

# ---- Resolve and add each domain in the allowlist ----
echo "  -> Resolving allowed domains..."
for DOMAIN in "${ALLOWED_DOMAINS[@]}"; do
  # Use getent first (no external deps), fall back to dig if available
  IPS=$(getent ahosts "$DOMAIN" 2>/dev/null | awk '{print $1}' | sort -u || true)
  if [ -z "$IPS" ] && command -v dig &>/dev/null; then
    IPS=$(dig +short "$DOMAIN" A 2>/dev/null || true)
  fi
  for IP in $IPS; do
    # Skip IPv6 and bogus entries
    if [[ "$IP" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      ipset add allowed-domains "$IP/32" 2>/dev/null || true
    fi
  done
done

# ---- Detect the container's own subnet so compose services can talk ----
CONTAINER_SUBNET=$(ip route | grep -E "^[0-9]" | awk '{print $1}' | grep "/" | head -1 || true)
if [ -n "$CONTAINER_SUBNET" ]; then
  ipset add allowed-domains "$CONTAINER_SUBNET" 2>/dev/null || true
fi

# ---- Restore compose/podman NAT rules ----
if [ -n "$EXISTING_NAT_RULES" ]; then
  while IFS= read -r RULE; do
    iptables -t nat "${RULE/-A/-A}" 2>/dev/null || true
  done <<< "$EXISTING_NAT_RULES"
fi

# ---- Apply output rules ----
# Allow loopback
iptables -A OUTPUT -o lo -j ACCEPT

# Allow DNS (UDP + TCP port 53) — compose service name resolution
iptables -A OUTPUT -p udp --dport 53 -j ACCEPT
iptables -A OUTPUT -p tcp --dport 53 -j ACCEPT

# Allow established/related connections (responses to inbound)
iptables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# Allow traffic to the allowlist
iptables -A OUTPUT -m set --match-set allowed-domains dst -j ACCEPT

# Default-deny all other outbound
iptables -A OUTPUT -j REJECT --reject-with icmp-port-unreachable

# ---- Verify ----
echo "  -> Verifying..."
if curl -sS --max-time 3 https://example.com &>/dev/null; then
  echo "  WARNING: example.com is reachable — firewall may not be active"
else
  echo "  OK: example.com is blocked"
fi
if curl -sS --max-time 5 https://api.github.com/zen &>/dev/null; then
  echo "  OK: api.github.com is reachable"
else
  echo "  WARNING: api.github.com is blocked — check GitHub IP resolution above"
fi

echo "==> Egress firewall active."
