# NOTICE File Generation — Design

**Date:** 2026-05-27  
**Status:** Approved  
**Repos affected:** `kotlin-saas-template` (MIT), `kotlin-saas-starter` (LGPL v2.1)

---

## Goal

Add an automated process to generate a `NOTICE` file in each repo that:

1. **Satisfies Apache 2.0 attribution obligations** for bundled third-party dependencies (e.g. Stripe SDK, Auth0 JWT, Konform — all Apache 2.0).
2. **Provides transparency** to contributors and forks about what third-party software is included and under which licenses.
3. **Stays honest automatically** — CI fails if the committed `NOTICE` does not match a freshly generated one, preventing dependency updates from silently going unattributed.

The generated `NOTICE` is committed to the repo root (not bundled inside the JAR or Docker image at this stage).

---

## Tool: `gradle-license-report` (jk1)

Both repos use the [`com.github.jk1:gradle-license-report`](https://github.com/jk1/Gradle-License-Report) Gradle plugin. It resolves dependency POM metadata across the full dependency graph and produces a structured JSON report. A thin custom Gradle task (`generateNotice`) reads that JSON and formats it into the final `NOTICE` file.

This approach is preferred over a custom POM-parsing task (fragile metadata) or a CycloneDX SBOM pipeline (overkill for this need).

---

## Dependency scope

Both repos scan **`runtimeClasspath`** exclusively:

- **Starter** (`kotlin-saas-starter`, LGPL v2.1): `runtimeClasspath` includes `api` and `implementation` deps — exactly what ends up in the published JAR. `compileOnly` deps (Spring Boot, Jobrunr, etc.) are excluded because consumers provide those themselves; they are never bundled in the starter's JAR.
- **Template** (`kotlin-saas-template`, MIT): `runtimeClasspath` covers all production dependencies deployed to Railway.

Test configurations (`testImplementation`, `testRuntimeOnly`) are excluded from both.

---

## Plugin configuration (`build.gradle.kts`)

```kotlin
plugins {
    id("com.github.jk1.dependency-license-report") version "<latest>"
}

licenseReport {
    configurations = arrayOf("runtimeClasspath")
    renderers = arrayOf(JsonReportRenderer("licenses.json"))
    // Override entries with missing or wrong POM metadata:
    overrides = arrayOf(
        // example: LicenseOverride.module("tech.sergiodelgado:kotlin-saas-starter")
        //     .withLicense("LGPL-2.1", "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html")
    )
}
```

The override map starts minimal and grows as we encounter deps with incomplete POM metadata.

---

## `generateNotice` task

A custom `tasks.register<DefaultTask>("generateNotice")` that:

1. Depends on `generateLicenseReport`.
2. Reads `build/reports/dependency-license/licenses.json`.
3. Sorts entries alphabetically by `group:name` for stable diffs.
4. Writes `NOTICE` directly to the repo root (overwrites in place).
5. Is idempotent — when dependencies haven't changed the output is identical, producing no git diff.

Output format:

```
<Project Name>
Copyright (c) <year> <Author>

This product includes the following third-party components:

------------------------------------------------------------------------
<name> (<group>:<artifact>:<version>)
License: <license name>
<license URL — omitted if unavailable>
------------------------------------------------------------------------
```

`generateNotice` is wired into the `build` lifecycle:

```kotlin
tasks.named("build") {
    dependsOn("generateNotice")
}
```

This means every `./gradlew build` (and the CI build) keeps `NOTICE` up to date automatically. If a dependency version changes, the `NOTICE` file will have a git diff after the next build, making the attribution update visible and reviewable in the commit.

---

## CI integration

None required beyond the existing `./gradlew build` step in CI. No separate verification task — the file is simply kept current by the build itself.

---

## Initial bootstrap

After implementing the tasks, run `./gradlew generateNotice` once in each repo and commit the resulting `NOTICE` file. Subsequent runs are only needed when dependencies change.

---

## Issue structure

Two linked GitHub issues, one per repo, with labels `notice-generation` and `cross-repo`. The template issue is primary; the starter issue carries a `> **Companion:**` blockquote linking back to the template issue.

---

## Out of scope

- Bundling `NOTICE` inside the published JAR or Docker image (can be added later if needed).
- Aggregating upstream `NOTICE` files from Apache 2.0 dependencies (the Apache 2.0 spec requires preserving copyright notices, which this design covers; full upstream NOTICE aggregation is a more advanced step).
- License header enforcement in source files (separate concern).
