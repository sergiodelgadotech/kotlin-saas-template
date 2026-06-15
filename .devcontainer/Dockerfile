FROM mcr.microsoft.com/devcontainers/java:25

# iptables + ipset are required by init-firewall.sh (installed via onCreateCommand)
# but are not present in the base image.
RUN apt-get update && apt-get install -y --no-install-recommends \
    iptables \
    ipset \
  && rm -rf /var/lib/apt/lists/*
