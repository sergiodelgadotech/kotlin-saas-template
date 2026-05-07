# Plan 0 — Rename `mvp-saas-template` → `kotlin-saas-template` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the repo, the Kotlin package, the Spring Boot main class, and all internal references from `mvp-saas-template` / `org.granchi.mvpsaas` / `MvpSaasApplication` to `kotlin-saas-template` / `org.granchi.saastemplate` / `SaasTemplateApplication`. Aligns naming with the sibling `kotlin-saas-starter` library.

**Architecture:** Mechanical refactor. No behavior changes. Sequence: rename remote and local dir → rename Gradle project → rename Kotlin package + main class → update config files → update infrastructure refs → update documentation. After each task, run the build to verify nothing is broken; commit per-task.

**Tech Stack:** Kotlin, Gradle, Spring Boot, Docker Compose, Terraform (untouched).

**Spec reference:** `docs/superpowers/specs/2026-05-07-starter-template-split-design.md` § Plan 0.

**Out of scope (intentional):**
- The orphaned Claude Code session-history directory at `~/.claude/projects/-var-home-serandel-Projects-mvp-saas-template/` is not migrated. Future sessions will create a fresh `-var-home-serandel-Projects-kotlin-saas-template/` automatically.

**Nothing has been deployed yet,** so infrastructure identifiers (Railway project name, Cloudflare Pages project name) are renamed too. Now is the cheapest time to align them.

---

## File Structure

Renamed/created/modified files:

**Directories renamed via `git mv`:**
- `app/src/main/kotlin/org/granchi/mvpsaas/` → `app/src/main/kotlin/org/granchi/saastemplate/`
- `app/src/test/kotlin/org/granchi/mvpsaas/` → `app/src/test/kotlin/org/granchi/saastemplate/`

**File renamed via `git mv`:**
- `app/src/main/kotlin/org/granchi/saastemplate/MvpSaasApplication.kt` → `app/src/main/kotlin/org/granchi/saastemplate/SaasTemplateApplication.kt`

**Files modified (package + class name updates):**
- All 23 `.kt` files under `app/src/main/kotlin/org/granchi/saastemplate/` (post-move)
- All 3 `.kt` files under `app/src/test/kotlin/org/granchi/saastemplate/` (post-move)

**Files modified (configuration):**
- `settings.gradle.kts` — `rootProject.name`
- `app/src/main/resources/application.yml` — `spring.application.name`
- `app/src/test/resources/application-test.yml` — JDBC URL + logger package
- `app/src/main/resources/application-local.yml.example` — DB credentials + logger package
- `docker-compose.yml` — Postgres + Zitadel DB credentials

**Files modified (infrastructure):**
- `infra/variables.tf` — Terraform `project_name` default
- `web/build.gradle.kts` — Cloudflare Pages `--project-name` flag

**Files modified (documentation):**
- `README.md`
- `CLAUDE.md`

---

### Task 1: Rename GitHub repo and local directory

**Files:**
- Modify: git remote URL (auto-updated by GitHub on rename, plus `git remote set-url` locally)
- Rename: local working directory

This task is reversible up until the push in later tasks. Run from inside the current `mvp-saas-template` directory.

- [ ] **Step 1: Verify clean working tree**

```bash
cd /var/home/serandel/Projects/mvp-saas-template
git status
```

Expected: `working tree clean` (the design-spec commit is already pushed).

- [ ] **Step 2: Rename the GitHub repo**

```bash
gh repo rename kotlin-saas-template --repo serandel/mvp-saas-template --yes
```

Expected output:
```
✓ Renamed repository serandel/kotlin-saas-template
```

GitHub automatically sets up an HTTP redirect from the old URL to the new one, so existing remotes keep working temporarily. We still update the local remote in the next step for cleanliness.

- [ ] **Step 3: Update the local git remote URL**

```bash
git remote set-url origin https://github.com/serandel/kotlin-saas-template.git
git remote -v
```

Expected:
```
origin	https://github.com/serandel/kotlin-saas-template.git (fetch)
origin	https://github.com/serandel/kotlin-saas-template.git (push)
```

- [ ] **Step 4: Verify the remote is reachable**

```bash
git fetch
```

Expected: no errors. Branch is up-to-date.

- [ ] **Step 5: Rename the local working directory**

```bash
cd /var/home/serandel/Projects
mv mvp-saas-template kotlin-saas-template
cd kotlin-saas-template
pwd
```

Expected: `/var/home/serandel/Projects/kotlin-saas-template`.

**Note:** Any open editor / Claude Code session referencing the old path must be restarted. From this point on, ALL paths in this plan are relative to `/var/home/serandel/Projects/kotlin-saas-template`.

- [ ] **Step 6: Sanity-check that Gradle still works**

```bash
./gradlew :app:tasks --no-configuration-cache > /dev/null && echo OK
```

Expected: `OK`. Gradle doesn't care about the parent directory name.

No commit yet — the directory rename leaves no diff. The first commit comes in Task 2.

---

### Task 2: Rename Gradle root project

**Files:**
- Modify: `settings.gradle.kts` line 1

- [ ] **Step 1: Update `rootProject.name`**

Change line 1 of `settings.gradle.kts` from:

```kotlin
rootProject.name = "mvp-saas-template"
```

to:

```kotlin
rootProject.name = "kotlin-saas-template"
```

- [ ] **Step 2: Verify Gradle builds with the new name**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add settings.gradle.kts
git commit -m "$(cat <<'EOF'
chore: rename Gradle root project to kotlin-saas-template

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Rename Kotlin package and main class

This is the largest mechanical change. Done as a single task because partial state (some files in old package, some in new) does not compile.

**Files:**
- Rename: `app/src/main/kotlin/org/granchi/mvpsaas/` → `app/src/main/kotlin/org/granchi/saastemplate/`
- Rename: `app/src/test/kotlin/org/granchi/mvpsaas/` → `app/src/test/kotlin/org/granchi/saastemplate/`
- Rename: `MvpSaasApplication.kt` → `SaasTemplateApplication.kt` (and the class within)
- Modify: every `.kt` file under those directories (package declaration; for `SaasTemplateApplication.kt` the class name)

- [ ] **Step 1: Move main source directory**

```bash
git mv app/src/main/kotlin/org/granchi/mvpsaas \
       app/src/main/kotlin/org/granchi/saastemplate
```

Expected: no errors. Run `git status` to confirm files are staged as renames.

- [ ] **Step 2: Move test source directory**

```bash
git mv app/src/test/kotlin/org/granchi/mvpsaas \
       app/src/test/kotlin/org/granchi/saastemplate
```

Expected: no errors.

- [ ] **Step 3: Bulk-replace package string in all moved Kotlin files**

```bash
find app/src/main/kotlin/org/granchi/saastemplate \
     app/src/test/kotlin/org/granchi/saastemplate \
     -name "*.kt" \
     -exec sed -i 's/org\.granchi\.mvpsaas/org.granchi.saastemplate/g' {} +
```

This updates both `package org.granchi.mvpsaas...` declarations and any internal `import org.granchi.mvpsaas...` references.

- [ ] **Step 4: Verify no `org.granchi.mvpsaas` references remain in source**

```bash
grep -rn "org\.granchi\.mvpsaas" app/src
```

Expected: no output (exit code 1 from grep). If any output appears, fix manually before proceeding.

- [ ] **Step 5: Rename the main class file**

```bash
git mv app/src/main/kotlin/org/granchi/saastemplate/MvpSaasApplication.kt \
       app/src/main/kotlin/org/granchi/saastemplate/SaasTemplateApplication.kt
```

- [ ] **Step 6: Replace the `MvpSaasApplication` class identifier**

```bash
find . -type f \( -name "*.kt" -o -name "*.kts" -o -name "*.yml" -o -name "*.md" \) \
       -not -path "./.git/*" \
       -not -path "./build/*" \
       -not -path "./.gradle/*" \
       -exec sed -i 's/MvpSaasApplication/SaasTemplateApplication/g' {} +
```

- [ ] **Step 7: Verify no `MvpSaasApplication` references remain**

```bash
grep -rn "MvpSaasApplication" . --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle
```

Expected: no output.

- [ ] **Step 8: Compile to confirm everything still wires together**

```bash
./gradlew :app:compileKotlin :app:compileTestKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run unit + architecture tests**

```bash
./gradlew :app:test
```

Expected: BUILD SUCCESSFUL. All tests pass.

If `ArchitectureTest` (Konsist) fails because rules reference `org.granchi.mvpsaas`, fix those references in `app/src/test/kotlin/org/granchi/saastemplate/architecture/ArchitectureTest.kt` (the file moved in Step 2; sed in Step 3 should already have updated the strings, but Konsist rules sometimes use string literals — verify).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor: rename Kotlin package to org.granchi.saastemplate

- org.granchi.mvpsaas -> org.granchi.saastemplate (all source + test files)
- MvpSaasApplication -> SaasTemplateApplication
- Move package directories via git mv to preserve file history

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Update application config files

**Files:**
- Modify: `app/src/main/resources/application.yml` line 3 (`spring.application.name`)
- Modify: `app/src/test/resources/application-test.yml` (JDBC URL + logger package)
- Modify: `app/src/main/resources/application-local.yml.example` (DB credentials + logger package)
- Modify: `docker-compose.yml` (Postgres credentials + Zitadel DB credentials)

The DB user/password change for local dev (`mvpsaas` → `saastemplate`) requires resetting the local Postgres volume. This is acceptable per the spec — no production data exists.

- [ ] **Step 1: Update `application.yml`**

In `app/src/main/resources/application.yml`, change line 3 from:

```yaml
    name: mvp-saas-template
```

to:

```yaml
    name: kotlin-saas-template
```

- [ ] **Step 2: Update `application-test.yml`**

In `app/src/test/resources/application-test.yml`:

Line 3: change `jdbc:tc:postgresql:16-alpine:///mvpsaas_test` to `jdbc:tc:postgresql:16-alpine:///saastemplate_test`.

Line 33: change `org.granchi.mvpsaas: WARN` to `org.granchi.saastemplate: WARN`.

- [ ] **Step 3: Update `application-local.yml.example`**

In `app/src/main/resources/application-local.yml.example`:

Lines 5-7: change the JDBC URL/user/password from `mvpsaas` to `saastemplate`:

```yaml
    url: jdbc:postgresql://localhost:5432/saastemplate
    username: saastemplate
    password: saastemplate
```

Line 32: change `org.granchi.mvpsaas: DEBUG` to `org.granchi.saastemplate: DEBUG`.

- [ ] **Step 4: Update `docker-compose.yml`**

In `docker-compose.yml`, replace all eight `mvpsaas` occurrences with `saastemplate`:

- Line 5: `POSTGRES_DB: saastemplate`
- Line 6: `POSTGRES_USER: saastemplate`
- Line 7: `POSTGRES_PASSWORD: saastemplate`
- Line 13: `test: ["CMD-SHELL", "pg_isready -U saastemplate"]`
- Line 38: `ZITADEL_DATABASE_POSTGRES_USER_USERNAME: saastemplate`
- Line 39: `ZITADEL_DATABASE_POSTGRES_USER_PASSWORD: saastemplate`
- Line 41: `ZITADEL_DATABASE_POSTGRES_ADMIN_USERNAME: saastemplate`
- Line 42: `ZITADEL_DATABASE_POSTGRES_ADMIN_PASSWORD: saastemplate`

A single command to do this:

```bash
sed -i 's/mvpsaas/saastemplate/g' docker-compose.yml
```

- [ ] **Step 5: Verify no `mvpsaas` references remain in config files**

```bash
grep -rn "mvpsaas" app/src/main/resources app/src/test/resources docker-compose.yml
```

Expected: no output.

- [ ] **Step 6: Reset the local Postgres volume**

The DB user/db name change means Postgres rejects the old volume's credentials. Stop containers and wipe state:

```bash
docker compose down -v
docker compose up -d
```

Expected: clean Postgres + Redis + Zitadel containers running with the new credentials.

- [ ] **Step 7: Run integration tests**

Integration tests use Testcontainers and don't depend on the local Postgres volume, but they do read `application-test.yml`:

```bash
./gradlew :app:integrationTest
```

Expected: BUILD SUCCESSFUL. All integration tests pass against the renamed `saastemplate_test` Testcontainers DB.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/resources/application.yml \
        app/src/test/resources/application-test.yml \
        app/src/main/resources/application-local.yml.example \
        docker-compose.yml
git commit -m "$(cat <<'EOF'
chore: rename application + DB identifiers to saastemplate

- spring.application.name: mvp-saas-template -> kotlin-saas-template
- Postgres DB / user / password: mvpsaas -> saastemplate
- Logger package filter: org.granchi.mvpsaas -> org.granchi.saastemplate

Local dev databases must be reset (docker compose down -v).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Update infrastructure identifiers

**Files:**
- Modify: `infra/variables.tf` — `project_name` variable default
- Modify: `web/build.gradle.kts` — Cloudflare Pages `--project-name` flag

These identifiers map to deployed resource names. Since nothing has been deployed yet, renaming now is free.

- [ ] **Step 1: Update Terraform `project_name` default**

In `infra/variables.tf`, change the `project_name` variable's default from `"mvp-saas"` to `"kotlin-saas-template"`:

```hcl
variable "project_name" {
  description = "Project name in Railway"
  type        = string
  default     = "kotlin-saas-template"
}
```

- [ ] **Step 2: Update Cloudflare Pages project name**

In `web/build.gradle.kts`, the `deploy` task currently runs:

```kotlin
commandLine("npx", "wrangler", "pages", "deploy", ".",
    "--project-name=mvp-saas-web"
)
```

Change `--project-name=mvp-saas-web` to `--project-name=kotlin-saas-web`.

- [ ] **Step 3: Verify no `mvp-saas` substrings remain in infra / web**

```bash
grep -rn "mvp-saas\|mvpsaas" infra web
```

Expected: no output.

- [ ] **Step 4: Verify Gradle still configures cleanly**

```bash
./gradlew :web:tasks --no-configuration-cache > /dev/null && echo OK
```

Expected: `OK`. (Won't actually deploy — just checks the task definition is valid.)

- [ ] **Step 5: Commit**

```bash
git add infra/variables.tf web/build.gradle.kts
git commit -m "$(cat <<'EOF'
chore: rename infrastructure identifiers to kotlin-saas

- Railway project_name default: mvp-saas -> kotlin-saas-template
- Cloudflare Pages project: mvp-saas-web -> kotlin-saas-web

Nothing deployed yet, so no state migration needed.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Update README and CLAUDE.md

**Files:**
- Modify: `README.md` (title + any references)
- Modify: `CLAUDE.md` (title + structure diagram + references)

- [ ] **Step 1: Find every reference to update**

```bash
grep -rn "mvp-saas-template\|mvpsaas\|MVP SaaS Template\|mvp_saas\|MvpSaas" README.md CLAUDE.md
```

This produces the list of substrings to replace. Update each match below:

- `mvp-saas-template` → `kotlin-saas-template`
- `mvpsaas` → `saastemplate`
- `MVP SaaS Template` → `Kotlin SaaS Template`
- `MvpSaas` → `SaasTemplate`

- [ ] **Step 2: Update `README.md`**

Specifically, line 1 changes from `# MVP SaaS Template` to `# Kotlin SaaS Template`. Use sed for the bulk replacements:

```bash
sed -i \
  -e 's/mvp-saas-template/kotlin-saas-template/g' \
  -e 's/mvpsaas/saastemplate/g' \
  -e 's/MVP SaaS Template/Kotlin SaaS Template/g' \
  -e 's/MvpSaas/SaasTemplate/g' \
  README.md
```

- [ ] **Step 3: Update `CLAUDE.md`**

```bash
sed -i \
  -e 's/mvp-saas-template/kotlin-saas-template/g' \
  -e 's/mvpsaas/saastemplate/g' \
  -e 's/MVP SaaS Template/Kotlin SaaS Template/g' \
  -e 's/MvpSaas/SaasTemplate/g' \
  CLAUDE.md
```

- [ ] **Step 4: Spot-check the result**

```bash
head -25 README.md
head -10 CLAUDE.md
```

Expected: title says `# Kotlin SaaS Template` / `# CLAUDE.md — kotlin-saas-template`. No leftover `mvpsaas` or `MvpSaas` strings.

- [ ] **Step 5: Verify no `mvpsaas` references remain in docs**

```bash
grep -rn "mvpsaas\|MvpSaas\|MVP SaaS Template" README.md CLAUDE.md
```

Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "$(cat <<'EOF'
docs: rename to kotlin-saas-template in README and CLAUDE.md

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Final verification and push

- [ ] **Step 1: Confirm zero remaining mvpsaas references in tracked files**

```bash
grep -rn "mvpsaas\|mvp-saas\|MvpSaas\|MVP SaaS" \
     --exclude-dir=.git \
     --exclude-dir=build \
     --exclude-dir=.gradle \
     . | grep -v "docs/superpowers"
```

Expected: no output. The `docs/superpowers` exclusion is for the design spec and this plan, which reference the old name historically as a record of the rename.

If any other matches surface, investigate and update them in a fresh commit before pushing.

- [ ] **Step 2: Full test suite**

```bash
./gradlew :app:test :app:integrationTest
```

Expected: BUILD SUCCESSFUL. All tests pass.

- [ ] **Step 3: Push all commits**

```bash
git push
```

Expected: 5 commits pushed (Tasks 2, 3, 4, 5, 6). Remote `origin` already updated to the new URL in Task 1.

- [ ] **Step 4: Verify the renamed repo on GitHub**

```bash
gh repo view --web
```

Expected: opens `https://github.com/serandel/kotlin-saas-template` in browser. Repo header reads `kotlin-saas-template`.

---

## Done When

- `./gradlew :app:test :app:integrationTest` passes from the new directory.
- `grep -rn "mvpsaas\|MvpSaas" app/src` returns nothing.
- The repo on GitHub is named `kotlin-saas-template`.
- The local working directory is `/var/home/serandel/Projects/kotlin-saas-template`.
- The `CLAUDE.md`, `README.md`, `infra/variables.tf`, and `web/build.gradle.kts` all use the new naming.
- All commits are pushed to `origin/main`.
