# Devcontainer Support — Design Spec

**Date:** 2026-05-22
**Repos:** `kotlin-saas-template`, `kotlin-saas-starter`

---

## Goal

Add `.devcontainer/` configurations to both repos so contributors can open a fully-wired development environment in VS Code, Cursor, or JetBrains Gateway without manual toolchain setup.

---

## Scope

- One `.devcontainer/` per repo — each repo is independently usable.
- Template devcontainer integrates with the existing `docker-compose.yml` (Postgres, Redis, Zitadel).
- Starter devcontainer is standalone; Testcontainers handles service lifecycle during tests.
- Composite build (`../kotlin-saas-starter`) is **not** wired inside the template devcontainer — the starter resolves from GitHub Packages (same as CI). Migrate to a workspace-level mount if day-to-day development proves this too limiting.
- **Claude Code CLI is installed in both containers** via the [Claude Code Dev Container Feature](https://github.com/anthropics/devcontainer-features/tree/main/src/claude-code) (`ghcr.io/anthropics/devcontainer-features/claude-code:1.0`). Auth and settings persist across rebuilds via a named Docker volume at `~/.claude`.
- **Java 25 LTS** is used in both devcontainers (`mcr.microsoft.com/devcontainers/java:25`). Both build files are bumped to `jvmToolchain(25)` in the same commit so the devcontainer JDK and Gradle toolchain target stay in sync.

---

## kotlin-saas-template

### Files

```
.devcontainer/
├── devcontainer.json
└── docker-compose.extend.yml
```

### devcontainer.json

- **dockerComposeFile:** `["../docker-compose.yml", "docker-compose.extend.yml"]`
- **service:** `dev`
- **workspaceFolder:** `/workspace`
- **features:**
  - `ghcr.io/anthropics/devcontainer-features/claude-code:1.0` — installs Claude Code CLI; also adds the Claude Code VS Code extension when opened in VS Code/Cursor
- **mounts:**
  - `source=claude-code-config-${devcontainerId},target=/home/vscode/.claude,type=volume` — persists Claude Code auth and settings across container rebuilds, isolated per project via `${devcontainerId}`
- **remoteUser:** `vscode` _(the `mcr.microsoft.com/devcontainers/java` image ships with a non-root user named `vscode`; the name is unrelated to the VS Code editor and works identically with JetBrains Gateway)_
- **postCreateCommand:**
  ```bash
  ./gradlew dependencies && \
  cp app/src/main/resources/application-local.yml.example \
     app/src/main/resources/application-local.yml
  ```
- **remoteEnv:** forwards the following from the host shell environment:
  - `GITHUB_ACTOR`, `GITHUB_TOKEN` — GitHub Packages auth (read:packages scope)
  - `AUTH_JWKS_URL`, `AUTH_ISSUER` — Zitadel
  - `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_STARTER`, `STRIPE_PRICE_PRO`
  - `RESEND_API_KEY`
  - `SENTRY_DSN`
- **VS Code extensions:**
  - `fwcd.kotlin` — Kotlin language support
  - `vscjava.vscode-java-pack` — Java extension pack (debugger, Maven/Gradle explorer, etc.)
  - `vscjava.vscode-spring-boot-dashboard` — Spring Boot run/debug dashboard
  - `ms-azuretools.vscode-docker` — Docker explorer
  - _(Claude Code extension is added automatically by the feature above)_
- **JetBrains:** No extensions configured in devcontainer.json — Gateway downloads its IDE backend into the container and handles plugins via its own marketplace.

### docker-compose.extend.yml

Adds a `dev` service to the compose project:

```yaml
services:
  dev:
    image: mcr.microsoft.com/devcontainers/java:25
    volumes:
      - ..:/workspace:cached
    command: sleep infinity
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/saastemplate
      REDIS_URL: redis://redis:6379
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
```

The `dev` container joins the same Docker network as Postgres, Redis, and Zitadel automatically (Compose default network). Services are reachable by their service names — no port forwarding needed from inside the container.

Zitadel is not listed in `depends_on` because it takes longer to initialize and the app can start without it during development (auth calls will fail gracefully until Zitadel is ready).

### Environment notes

- `DATABASE_URL` and `REDIS_URL` are set directly in `docker-compose.extend.yml` because their values are deterministic from the compose service names.
- Secrets (`STRIPE_*`, `RESEND_API_KEY`, etc.) are forwarded from the host via `remoteEnv`; the user keeps them in their shell profile (`~/.zshrc` or `~/.bashrc`). `application-local.yml` (copied by postCreateCommand) reads these from the environment via Spring's `${VAR}` syntax.
- `GITHUB_TOKEN` needs `read:packages` scope to pull `kotlin-saas-starter` from GitHub Packages.

---

## kotlin-saas-starter

### Files

```
.devcontainer/
└── devcontainer.json
```

### devcontainer.json

- **image:** `mcr.microsoft.com/devcontainers/java:25`
- **workspaceFolder:** `/workspace`
- **remoteUser:** `vscode` _(same note as template — just the image's built-in non-root user)_
- **features:**
  - `ghcr.io/devcontainers/features/docker-outside-of-docker:1` — mounts the host Docker socket so Testcontainers can launch containers during `./gradlew test`
  - `ghcr.io/anthropics/devcontainer-features/claude-code:1.0` — installs Claude Code CLI; also adds the Claude Code VS Code extension when opened in VS Code/Cursor
- **mounts:**
  - `source=claude-code-config-${devcontainerId},target=/home/vscode/.claude,type=volume` — persists Claude Code auth and settings across container rebuilds, isolated per project
- **postCreateCommand:** `./gradlew dependencies`
- **remoteEnv:** `GITHUB_ACTOR`, `GITHUB_TOKEN` (only needed if the starter's own tests pull any GitHub Packages dependencies)
- **VS Code extensions:**
  - `fwcd.kotlin`
  - `vscjava.vscode-java-pack`
  - `ms-azuretools.vscode-docker`
  - (no Spring Boot Dashboard — the starter is a library, not a runnable app)
  - _(Claude Code extension is added automatically by the feature above)_
- **JetBrains:** Same as template — Gateway handles it.

---

## What is NOT in scope

- GitHub Codespaces secrets / prebuild configuration — can be added later if Codespaces adoption warrants it.
- Workspace-level devcontainer spanning both repos — deferred; migrate if composite-build workflows become painful.
- Automated Zitadel configuration (creating clients, users) inside the container — still manual; documented in README.

---

## Testing the devcontainer

Before closing a PR, verify:

1. `Dev Containers: Reopen in Container` succeeds in VS Code.
2. `./gradlew :app:test` passes inside the container (template).
3. `./gradlew test` passes inside the container (starter — Testcontainers must be able to pull images).
4. `./gradlew :app:bootRun --args='--spring.profiles.active=local'` starts the app and connects to Postgres and Redis (template).
5. `claude --version` works inside both containers (confirms the Claude Code feature installed correctly).
6. After running `claude` once and authenticating, rebuild the container and confirm authentication is preserved (the `~/.claude` volume persists across rebuilds).
