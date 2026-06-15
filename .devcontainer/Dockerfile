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
COPY init-firewall.sh /usr/local/bin/init-firewall.sh
RUN chmod +x /usr/local/bin/init-firewall.sh \
  && echo "vscode ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh" \
     > /etc/sudoers.d/firewall \
  && chmod 0440 /etc/sudoers.d/firewall
