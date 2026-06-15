FROM mcr.microsoft.com/devcontainers/java:25

# Install firewall toolchain needed by init-firewall.sh
RUN apt-get update && apt-get install -y --no-install-recommends \
    iptables \
    ipset \
    dnsutils \
    curl \
    jq \
  && rm -rf /var/lib/apt/lists/*

# Install the firewall script and grant vscode passwordless sudo for it only
# Install at a different path so the claude-code feature doesn't overwrite it
# (the feature installs its own /usr/local/bin/init-firewall.sh)
COPY init-firewall.sh /usr/local/bin/init-firewall-project.sh
RUN chmod +x /usr/local/bin/init-firewall-project.sh \
  && echo "vscode ALL=(root) NOPASSWD: /usr/local/bin/init-firewall-project.sh" \
     > /etc/sudoers.d/firewall \
  && chmod 0440 /etc/sudoers.d/firewall
