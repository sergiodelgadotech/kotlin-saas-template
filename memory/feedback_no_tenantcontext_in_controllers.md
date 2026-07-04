---
name: feedback_no_tenantcontext_in_controllers
description: Never import or use TenantContext in @Controller/@RestController — Konsist architecture rule enforces this and CI catches it
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 8b4cf1b3-8f87-4e9f-a498-715c368b23b9
---

Never use `TenantContext` inside a `@Controller` or `@RestController` class.

**Why:** Konsist architecture test `tenant context is not used in controllers` (`ArchitectureTest.kt:44`) enforces this. Controllers must not read `TenantContext`; the `TenantInterceptor` sets it before controllers run, and services/repositories consume it. This was caught when adding a `TenantContext.isPresent()` guard to `AvatarController` — the guard seemed like defense-in-depth but violated the rule and failed CI.

**How to apply:** If a controller needs to avoid a `TenantContext.get()` call (e.g. on a path where the interceptor hasn't run), fix the interception path (add the path to `saasstarter.tenant.path-patterns` in `application.yml`) rather than guarding inside the controller. If a path genuinely must not have a tenant (e.g. webhooks), it should not call any service method that reaches `TenantContext.get()` at all — or that service method needs its own `isPresent()` guard.
