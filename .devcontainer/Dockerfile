FROM mcr.microsoft.com/devcontainers/java:25

# iptables + ipset are required by the claude-code feature's init-firewall.sh
# but are not present in the base image. Everything else (the script itself,
# NET_ADMIN/NET_RAW caps, sudo rule) is handled by the feature.
RUN apt-get update && apt-get install -y --no-install-recommends \
    iptables \
    ipset \
  && rm -rf /var/lib/apt/lists/* \
  && echo "vscode ALL=(root) NOPASSWD: /workspaces/kotlin-saas-template/.devcontainer/init-firewall-extra.sh" \
     > /etc/sudoers.d/firewall-extra \
  && chmod 0440 /etc/sudoers.d/firewall-extra
