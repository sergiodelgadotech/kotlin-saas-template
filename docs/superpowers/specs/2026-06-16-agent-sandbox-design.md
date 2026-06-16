# Agent Sandbox Architecture — Design Spec

**Date:** 2026-06-16
**Status:** Design only — not committed to build. The "build this or continue with sbx + workarounds" decision is the open question this spec is meant to inform.

---

## Goal

Define an isolated environment for parallel agent runs (Claude Code with `--dangerously-skip-permissions`, multiple branches in flight) that:

- Keeps host credentials *unreachable* from inside the sandbox, even from a fully-compromised guest.
- Locks the guest's network egress to a credential-injecting proxy on the host.
- Provides fast first-run boot for the project's `docker compose` services (Postgres, Redis, Zitadel).
- Enforces "one worktree per sandbox" and "no shared mutable state across sandboxes" by construction.
- Is owned end-to-end so we can fix issues without waiting on an upstream vendor release.

The current `sbx`-based workflow (documented in CLAUDE.md → "Agent sandboxes") satisfies most of this but ships with several recurring tax items — this spec proposes a Lima-based replacement and lays out the architecture in enough detail to scope the build.

---

## Why an alternative is on the table

Five concrete `sbx` gaps already documented in CLAUDE.md, each costing recurring effort:

1. **`maven.pkg.github.com` Basic auth missing from built-in github kit.** Workaround: `STARTER_PATH` composite build. A project-specific `sbx kit` would fix it but doesn't yet exist.
2. **Workspace mount exposes `.env`.** `sbx create claude .` virtio-fs-shares the entire git root including gitignored files (`.env`, `.cloudflared.env`, `.devcontainer/.env`). They aren't auto-loaded into the agent's env but are `cat`-readable from inside the sandbox.
3. **Nested-Docker image cache is empty on every cold sandbox.** First `docker compose up` pulls Postgres, Redis, Zitadel, cloudflared, etc. — multi-GB and multi-minute. Cached only within a single sandbox lifetime.
4. **Sandbox auto-stops on idle.** Detached `sbx exec -d` processes die. `bootRun`-style headless work needs an explicit host-side keepalive.
5. **`bootRun` blocked by `zitadel-init` networking.** `zitadel-init` uses `network_mode: host` to satisfy Zitadel's vhost-based OIDC routing; that pattern doesn't survive translation into nested Docker.

Each of these has a known *workaround* under sbx, but the workarounds compound. The Lima path resolves (1)–(4) by construction (we control the proxy, the mount surface, the image baking, and the lifecycle); (5) is an orthogonal docker-compose problem either way.

---

## Architecture

Per-project topology. Each project owns its own mitmdump, its own CA, its own golden snapshot, and its own host-side state directory. Multiple projects can run sandboxes simultaneously without interfering.

```
┌─────────────────────────────── HOST ────────────────────────────────────┐
│                                                                         │
│  <project>/                                                             │
│    ├── .env                  ← gitignored: credential VALUES            │
│    ├── .wrapper/                                                        │
│    │   ├── proxy.yml         ← committed: injection RULES (host→header) │
│    │   ├── .gitignore        ← ignores everything except proxy.yml      │
│    │   ├── proxy-ca.crt      ← per-project CA, generated on first start │
│    │   ├── mitmproxy/        ← mitmproxy confdir (key material)         │
│    │   ├── golden.qcow2      ← Lima base disk (digest-pinned images)    │
│    │   └── <name>/           ← per-sandbox CoW disk + state             │
│    │       └── worktree/     ← git worktree, mounted into VM            │
│    └── docker-compose.yml                                               │
│                                                                         │
│  mitmdump (per-project, port allocated from project path hash)          │
│    reads <project>/.wrapper/proxy.yml  + <project>/.env                 │
│    listens on 127.0.0.1:<project-port>, exposed to VMs via Lima         │
│    credential injector + TLS terminator + transparent passthrough       │
│                                                ▲                        │
│  wrapper CLI (project-scoped — operates from $PWD's .wrapper/)         │
│    create / run / exec / rm / rebuild-base     │ HTTPS                  │
│    proxy start / stop / restart                │                        │
│                                                │                        │
└────────────────────────────────────────┬───────┴───────────────────────-┘
                                         │
                          ┌──────────── LIMA VM ────────────┐
                          │                                 │
                          │  /etc/ssl/certs/proxy-ca.crt    │
                          │  HTTPS_PROXY=                   │
                          │    http://host.lima.internal:<port>
                          │  iptables OUTPUT -P DROP        │
                          │    + allow lo                   │
                          │    + allow host.lima.internal:<port>
                          │    + allow 53/udp to resolver   │
                          │                                 │
                          │  Docker daemon (HTTPS_PROXY env)│
                          │  Pre-pulled compose images      │
                          │                                 │
                          │  virtio-fs mount of             │
                          │  <project>/.wrapper/<name>/     │
                          │    worktree                     │
                          │  ( .env is one level up,        │
                          │    NOT in the mount surface )   │
                          │                                 │
                          │  Claude Code in YOLO mode       │
                          └─────────────────────────────────┘
```

### Why each piece exists

- **Lima VM** provides a KVM boundary. iptables-based egress lockdown inside a regular Docker container is theatre — runc/kernel CVEs make the network namespace bypass-able. The KVM/QEMU boundary makes the egress rules actually load-bearing.
- **Per-project mitmdump on host** injects credentials *into* HTTPS requests after the proxy terminates TLS using a CA the VM trusts. The credentials never enter the VM filesystem or env. Each project gets its own proxy process, its own CA, and its own port (allocated deterministically from a hash of the project's absolute path) — so a compromised CA scope is bounded to one project's VMs. Same mechanic as sbx's "proxy MITMs api.github.com" (verified empirically — the sbx microVM ships `/usr/local/share/ca-certificates/proxy-ca.crt` issued by `Docker Sandboxes Proxy CA` and curl through `HTTPS_PROXY` sees a forged leaf signed by it).
- **`host.lima.internal`** is Lima's built-in DNS name from inside VMs pointing at the host's slirp gateway. mitmdump listens on `127.0.0.1:<port>` on the host and is reachable from each VM via this name. Combined with the iptables egress lock, the only way out is through that one named address.
- **Golden qcow2** has the project's `docker-compose.yml` images already pulled inside. New sandboxes clone it as a copy-on-write disk, so `docker compose up` is near-instant. Rebuilt on Renovate-PR-merge whenever a digest changes.
- **Wrapper CLI** orchestrates: worktree creation, Lima VM lifecycle, per-project mitmdump supervision, snapshot rebuild. Project-scoped — every command operates on `$PWD`'s `.wrapper/` directory. Written in Go because everything it talks to (`limactl`, `git`, `docker`, `gh`, `mitmdump`) is Go-native or a clean subprocess; single static binary; same ecosystem as sbx/Docker/Lima themselves.

---

## Components

### 1. Per-project proxy configuration

Two files define what the proxy does for a given project:

**`<project>/.wrapper/proxy.yml` — committed, declares injection rules:**

```yaml
# Declares which credentials to inject for which outbound hosts.
# Values are resolved from <project>/.env at proxy-start time.
injections:
  - host: api.github.com
    header: Authorization
    template: "token ${GITHUB_TOKEN}"

  - host: maven.pkg.github.com
    header: Authorization
    template: "Basic ${GITHUB_PACKAGES_BASIC}"   # pre-encoded base64("user:token")

  - host: api.anthropic.com
    header: x-api-key
    template: "${ANTHROPIC_API_KEY}"
```

The file is committed because it documents *which* services the project authenticates to — useful for code review, audit, and onboarding. It contains no secrets, only the shape of the injection.

**`<project>/.env` — gitignored, per-developer values:**

```
GITHUB_TOKEN=ghp_...
GITHUB_PACKAGES_BASIC=eHpuOnByb3h5LW1hbmFnZWQ=
ANTHROPIC_API_KEY=sk-ant-...
# other docker-compose env vars also live here, unchanged
```

This is the *same* `.env` file the project already uses for `docker compose`. No duplication, no separate secret store, no `wrapper secret set` commands. Editing the file IS rotating the secret — followed by `wrapper proxy restart`.

**The addon (`inject_creds.py`, ~25 LOC):**

```python
import os, yaml
from string import Template
from mitmproxy import http

def parse_env(path):
    result = {}
    with open(path) as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            k, _, v = line.partition("=")
            result[k.strip()] = v.strip().strip('"').strip("'")
    return result

class CredentialInjector:
    def __init__(self):
        env = parse_env(os.environ["WRAPPER_ENV_FILE"])
        with open(os.environ["WRAPPER_PROXY_CONFIG"]) as f:
            cfg = yaml.safe_load(f)
        self.rules = [
            (r["host"], r["header"], Template(r["template"]).substitute(env))
            for r in cfg["injections"]
        ]

    def request(self, flow: http.HTTPFlow):
        host = flow.request.pretty_host
        for h, header, value in self.rules:
            if host == h:
                flow.request.headers[header] = value
                return

addons = [CredentialInjector()]
```

Spawned by the wrapper as:

```
WRAPPER_ENV_FILE=<project>/.env \
WRAPPER_PROXY_CONFIG=<project>/.wrapper/proxy.yml \
mitmdump \
  -s <wrapper-data-dir>/inject_creds.py \
  --set confdir=<project>/.wrapper/mitmproxy \
  --listen-host 127.0.0.1 --listen-port <project-port> \
  --set stream_large_bodies=1m
```

- `confdir` scopes the CA to this project — mitmdump generates `mitmproxy-ca-cert.pem` on first start under `<project>/.wrapper/mitmproxy/`. The wrapper copies it to `proxy-ca.crt` for the VM trust store.
- `stream_large_bodies=1m` is required so ad-hoc `docker pull` traffic through the proxy doesn't buffer GB-scale layer responses in Python memory.
- `host.lima.internal` from inside the VM resolves to the slirp gateway, which forwards to the host's `127.0.0.1:<port>`.

### 2. lima.yaml template

Templated by the wrapper for each sandbox. Key fields (interpolations from the wrapper marked `{{...}}`):

```yaml
images:
  - location: "{{.ProjectRoot}}/.wrapper/golden.qcow2"  # copy-on-write clone

mounts:
  - location: "{{.ProjectRoot}}/.wrapper/{{.Name}}/worktree"  # ONLY this path
    mountPoint: "/workspace"
    writable: true
    mountType: "virtiofs"
  - location: "{{.StarterPath}}"                              # sibling starter
    mountPoint: "/starter"
    writable: false
    mountType: "virtiofs"

networks:
  - lima: user-v2

provision:
  - mode: system
    script: |
      cp /workspace/.wrapper-bootstrap/proxy-ca.crt /usr/local/share/ca-certificates/
      update-ca-certificates
      PROXY="http://host.lima.internal:{{.ProxyPort}}"
      cat > /etc/environment.d/99-wrapper.conf <<EOF
      HTTPS_PROXY=$PROXY
      HTTP_PROXY=$PROXY
      NO_PROXY=localhost,127.0.0.1,::1,host.lima.internal
      NODE_EXTRA_CA_CERTS=/etc/ssl/certs/ca-certificates.crt
      REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt
      SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
      JAVA_TOOL_OPTIONS=-Dhttp.proxyHost=host.lima.internal -Dhttp.proxyPort={{.ProxyPort}} -Dhttps.proxyHost=host.lima.internal -Dhttps.proxyPort={{.ProxyPort}}
      EOF
      mkdir -p /etc/systemd/system/docker.service.d
      cat > /etc/systemd/system/docker.service.d/http-proxy.conf <<EOF
      [Service]
      Environment="HTTPS_PROXY=$PROXY"
      EOF
      systemctl daemon-reload && systemctl restart docker
      HOST_IP=$(getent hosts host.lima.internal | awk '{print $1}')
      iptables -P OUTPUT DROP
      iptables -A OUTPUT -o lo -j ACCEPT
      iptables -A OUTPUT -d "$HOST_IP" -p tcp --dport {{.ProxyPort}} -j ACCEPT
      ip6tables -P OUTPUT DROP
```

**Mount surface** — the only host directory the VM sees is the per-sandbox worktree at `<project>/.wrapper/<name>/worktree`. The project root (where `.env` lives) is *not* mounted; neither is `~`, `/etc`, or any other host path. The `.env`-exposure category of attack is resolved by construction.

The wrapper copies `proxy-ca.crt` into the worktree at a known subpath (`.wrapper-bootstrap/proxy-ca.crt`) before booting the VM, then the provision script picks it up. This is a one-shot bootstrap — the file is deleted by the same provision script after `update-ca-certificates` runs, so it doesn't persist in the worktree.

### 3. Wrapper CLI commands

All commands are project-scoped: they walk up from `$PWD` to find a `.wrapper/` directory. Running `wrapper create foo` in two different projects creates two independent sandboxes with two independent proxies on two different ports.

| Command | Behavior |
|---|---|
| `wrapper init` | Bootstrap the project: create `.wrapper/`, write `.wrapper/.gitignore`, write a starter `.wrapper/proxy.yml`, allocate a project port (deterministic from `sha256(absPath(projectRoot))[:2] % 1000 + 3128`), record it in `.wrapper/port`. |
| `wrapper create <name>` | `git worktree add .wrapper/<name>/worktree`, clone `.wrapper/golden.qcow2`, template `lima.yaml`, copy `proxy-ca.crt` into the worktree under `.wrapper-bootstrap/`, `limactl start`. If the project's mitmdump isn't running, start it first. |
| `wrapper run <name>` | `limactl shell <name>` invoking `claude` in the worktree. |
| `wrapper exec <name> -- <cmd>` | `limactl shell <name> <cmd>`. |
| `wrapper rm <name>` | `limactl stop && limactl delete`, `git worktree remove`, delete the per-sandbox CoW diff disk. If the last sandbox for this project is gone, stop the project's mitmdump. |
| `wrapper rebuild-base` | Boot fresh Lima from a vanilla Ubuntu cloud image, pull `docker-compose.yml`'s digest-pinned images, `limactl stop`, copy diffdisk to `.wrapper/golden-<sha>.qcow2`, atomically symlink as `.wrapper/golden.qcow2`. |
| `wrapper proxy start \| stop \| restart \| status` | Lifecycle for this project's mitmdump. `restart` re-reads `.env` and `proxy.yml`. |

**Rotating a secret:** edit `<project>/.env`, run `wrapper proxy restart`. No separate "set secret" path — `.env` is the source of truth, and the existing dev workflow for setting env vars already covers it.

`rebuild-base` is the only command that talks to upstream registries. It's manual-by-default; wiring it to a Renovate-PR-merge hook is an explicit opt-in.

### 4. Golden snapshot lifecycle

```
Renovate opens PR bumping postgres digest in docker-compose.yml
  → CI runs full test suite against the new digest
  → human merges
  → post-merge hook fires `wrapper rebuild-base`
    → ~5–10 min to boot vanilla VM + pull images + snapshot
  → next `wrapper create` uses the new golden
```

Digest pinning (`postgres@sha256:…` instead of `postgres:16`) is what makes this safe: the snapshot is exactly the image set the compose file declares, and "snapshot is stale" can only mean "an unreviewed digest bump is pending."

### 5. Security boundary summary

| Threat | Mitigation |
|---|---|
| Compromised agent reads `.env` from the project root | `.env` lives at `<project>/.env`, but the VM's `mounts:` surface only includes `<project>/.wrapper/<name>/worktree`. `.env` is one directory up and outside the mount — unreachable from inside the VM by filesystem traversal. |
| Compromised agent reads host secrets via mitmproxy's filesystem | mitmproxy runs on host. The VM has no host filesystem access except the worktree mount, which contains neither `.env` nor the mitmproxy confdir. |
| Compromised agent exfiltrates via direct egress | iptables OUTPUT lock + KVM boundary make the proxy the only path; `host.lima.internal:<project-port>` is the single allowed destination. |
| Compromised agent steals injected credentials in flight | Credentials added by the proxy *after* TLS termination, on the host; never present in the VM's plaintext traffic going *into* the proxy. The VM-side TLS endpoint of the connection is the proxy, not the real upstream — the real header value never leaves the host. |
| Compromised agent in project A talks to project B's proxy | Each project's port is distinct; iptables in project A's VM only allows project A's port; even if the agent guessed project B's port, the iptables drop would catch it. CA scope is per-project, so a leaked CA from project A cannot be used to MITM project B's VMs. |
| Compromised agent escapes guest OS | KVM boundary; same security property as sbx microVMs. |
| Compromised agent abuses the egress proxy to make arbitrary outbound requests | Acknowledged residual: proxy allowlist is per-host but not per-request-shape. Tightening (rate limits, audit log) is post-MVP. |

---

## Rejected alternatives

### sbx + project-specific kit (status quo, polished)

A `sbx kit` declaring `serviceAuth` for `maven.pkg.github.com` would fix gap (1). The `.env` mount (gap 2) needs reorganizing repo layout to move secret files out of the workspace tree. Image cache (gap 3) waits for a Docker upstream feature. Auto-stop (gap 4) is a documented workaround. **Why not chosen:** four recurring tax items don't disappear, just get smaller. Building the kit is ~1–2 days but gaps 2–4 stay. Lima path is more upfront work (~1 week) but those gaps go to zero.

### Roll our own proxy in Go using elazarl/goproxy

Goproxy gives the same MITM mechanic in ~200 LOC of Go. **Why not chosen:** mitmproxy is better at this specific job — HTTP/2 MITM is mature (goproxy's is historically incomplete), CA handling is built-in, the addon API is a clean Python file. The Python runtime dependency is the only cost, and either `dnf install mitmproxy` or shipping the standalone binary covers it.

### Pull-through registry mirror on host

Initially included alongside the golden snapshot. Removed once we realized: digest pinning + automated rebuild means the snapshot is *always* fresh, so the mirror's "serve deltas to stale snapshots" role goes to zero. The "save bandwidth during rebuilds" role is marginal (rebuilds happen on the host where bandwidth is fine, ~500 MB–1 GB per cycle, weekly at most). Not worth the daemon. **Side effect of removing it:** ad-hoc `docker pull` inside a running sandbox (debugging, testing a different postgres major) goes through mitmproxy, which streams the layer response. Confirmed mitmproxy handles this fine with `stream_large_bodies=1m`.

### Switch in-VM runtime to containerd for multi-registry mirror config

Considered when the mirror was still in scope. Docker's `registry-mirrors` is Docker-Hub-only; containerd has proper per-registry mirror config. **Why not chosen:** dropping the mirror entirely eliminated the need for multi-registry config. containerd would have been a real workflow shift (`nerdctl` instead of `docker`) with no remaining payoff.

### Bind-mount host's `/var/lib/docker` into the VM

Would share image cache automatically. **Why not chosen:** two Docker daemons writing the same overlay2 store corrupt each other's BoltDB metadata. Hard no.

### Compose override file pointing at local registry

Considered as a fix for ghcr.io coverage when the mirror was in scope. **Why not chosen:** dropped along with the mirror.

### Single global mitmdump shared by all projects

Considered for simplicity (one daemon, one port, one CA across all projects on the host). **Why not chosen:** a single CA trusted by all VMs means a compromise of one project's proxy can be used to MITM any other project's VM that shares the trust anchor. Per-project CA + per-project port gives blast-radius isolation between projects at minimal additional complexity (one mitmdump process per active project, allocated lazily).

### Dedicated wrapper secret store (separate from `.env`)

Originally specced as `~/.config/wrapper/secrets/<name>` with `wrapper secret set` commands. **Why not chosen:** the project's `.env` already contains every credential the project uses (docker-compose, Spring Boot, local dev). Reading the same file the developer already maintains avoids the "secret out of sync" failure mode where the wrapper's store and the dev environment drift apart. The `.env`-on-host / not-in-VM-mount property holds the security guarantee equally well.

---

## Open questions

1. **Build this, or continue with sbx + workarounds?** This is the only load-bearing open question. Recommendation: **build it** — the four recurring sbx tax items compound, the Lima architecture is straightforward enough (~1 week of work), and owning the proxy + image pipeline + mount surface buys us long-term independence from sbx's release cadence. But "build it" should be confirmed before any implementation issue gets filed.

2. **macOS support?** Lima is cross-platform but the host-side networking story differs. On Linux, Lima's `user-v2` mode gives the VM a predictable subnet that mitmdump can bind to directly — no extra install. On macOS, host-only networking needs Apple's `vmnet.framework`, accessed either via `socket_vmnet` (a small Homebrew-installed privileged daemon that wraps the framework for Lima's QEMU driver) or via Lima's `vz` driver (which uses vmnet natively without the helper). Either path is a one-time install but adds a documented setup step. Out of scope for the MVP — Linux-host first. Deferred.

3. **Per-sandbox secrets within one project?** Current design has one set of secrets per project (driven by `.env`), shared across all of that project's sandboxes. Per-sandbox identity (e.g. "this sandbox uses a scoped read-only GitHub token from a separate `.env.sandbox-<name>`") would need the proxy to know which sandbox a request came from — solvable via per-sandbox proxy ports or a sandbox-injected header. Deferred.

4. **`.env` parsing precision.** The addon uses a minimal shell-style parser (KEY=VALUE per line, `#` comments, quoted values stripped). docker-compose's real env parser is more forgiving (multi-line values, escapes, variable expansion within `.env`). If a developer's `.env` uses fancier syntax that compose accepts but the wrapper doesn't, the injection will silently use the wrong value. MVP accepts this; a follow-up could shell out to a known-good `.env` parser. Deferred.

---

## What is NOT in scope

- **CI replacement.** CI keeps using GitHub-hosted runners. This is for local parallel agent runs only.
- **Multi-user shared host.** Architecture assumes one developer per host machine. Per-project state, host-only network, and snapshot cache are all per-user.
- **Replacing the Devcontainer setup.** Devcontainers (per `2026-05-22-devcontainer-design.md`) remain the supported path for VS Code / JetBrains Gateway interactive development. This spec is specifically for `--dangerously-skip-permissions` agent runs.
- **Compatibility wrapper exposing an `sbx`-shaped CLI.** Once we commit to this, we commit fully — no parallel CLI maintenance.

---

## Verification approach (before committing to build)

Three prototypes, each independently demonstrable, before filing the implementation plan issue:

1. **Proxy prototype:** mitmdump on host with the addon, reading values from a real `.env` and rules from a real `proxy.yml`, manually configured Lima VM with CA + `HTTPS_PROXY=host.lima.internal:<port>`. Demonstrate `gh auth status` inside the VM succeeds without the VM having a token in env or filesystem, *and* without the `.env` file being inside the VM's mount surface.
2. **Snapshot prototype:** boot vanilla Lima, pull this project's compose images, save diffdisk, clone it for a new VM, demonstrate `docker compose up -d` completes in <10 s with zero network egress to upstream registries.
3. **End-to-end:** wrapper script (bash MVP, not Go yet) that does worktree + Lima + project-scoped mitmdump for one sandbox, runs `./gradlew :app:integrationTest` inside it successfully against the worktree, demonstrates iptables egress lock by verifying `curl https://example.com` from inside the VM goes through the proxy and is logged there, *and* that a second sandbox spun up from the same project shares the same proxy (no second mitmdump spawned).

If all three pass, file the implementation plan issue and port the bash to Go. If any of them surface a structural blocker (e.g. socket_vmnet networking is too fiddly on Linux, or mitmproxy's HTTP/2 handling breaks a common Gradle pull), revisit the "build vs continue with sbx" question.
