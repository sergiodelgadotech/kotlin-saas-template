# Domain Event Bus — Design Spec

**Issue:** #57  
**Date:** 2026-06-02

## Summary

Add a lightweight in-process domain event bus so that the dashboard's SSE activity stream can show real-time events as they happen. The initial producer is "member invited" from `OrganizationService` in the starter. Future producers (audit log, billing events, etc.) follow the same pattern.

## Architecture

Spring's `ApplicationEventPublisher` is the bus — it's already in the container, needs no additional dependency, and fires synchronously in the caller's thread (same `TenantContext` and `SecurityContext`).

```
OrganizationService.inviteMember()
  └─ applicationEventPublisher.publishEvent(MemberInvitedEvent)
       └─ ActivityStreamService @EventListener
            └─ emitters[orgId].forEach { it.send(htmlFragment) }
                                    │
                              SseEmitter held open by DashboardController
                                    │
                              Browser (HTMX sse-swap="message")
```

## Components

### 1. `MemberInvitedEvent` (starter — `organization` package)

```kotlin
data class MemberInvitedEvent(
    val organizationId: UUID,
    val memberId: UUID,
    val invitedExternalUserId: String,
    val actorExternalUserId: String,
)
```

`actorExternalUserId` is read from `SecurityContextHolder.getContext().authentication.name` at publish time (same thread as the service call).

### 2. `OrganizationService` update (starter)

Add `ApplicationEventPublisher` as a constructor parameter. After `memberRepository.save()` in `inviteMember`, publish the event:

```kotlin
applicationEventPublisher.publishEvent(
    MemberInvitedEvent(
        organizationId = orgId,
        memberId = saved.id!!,
        invitedExternalUserId = externalUserId,
        actorExternalUserId = SecurityContextHolder.getContext().authentication?.name ?: "system",
    )
)
```

No other methods in `OrganizationService` change.

### 3. `ActivityStreamService` (template — `dashboard` package)

A `@Service` that owns the SSE fan-out:

- **State:** `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>` (org ID → active emitters)
- **`register(emitter: SseEmitter)`** — reads `TenantContext.get()` internally to determine the org, adds the emitter, attaches `onCompletion` and `onTimeout` callbacks that remove it from the map
- **`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` on `MemberInvitedEvent`** — fires only after the publishing transaction commits, so no SSE event is ever sent for a member that was never actually persisted. Looks up emitters for the event's org ID, sends a simple HTML `<p>` fragment to each, removes any that throw on send (client disconnected)

The SSE message is a plain HTML fragment (`<p>` with event description) so HTMX's `sse-swap="message"` + `hx-swap="beforeend"` appends it directly to `#activity-feed`.

### 4. `DashboardController.activityStream` update (template)

Remove `emitter.complete()`. Instead:

```kotlin
fun activityStream(): SseEmitter {
    val emitter = SseEmitter(60_000L)
    activityStreamService.register(emitter)
    return emitter
}
```

The controller does not read `TenantContext` directly (architectural rule). `ActivityStreamService.register` captures the org ID from `TenantContext` internally — safe because the service call happens within the same request thread where the interceptor has already set the context.

## Event format (SSE payload)

An HTML `<p>` fragment. Example:

```html
<p class="text-sm">user@example.com was invited to the organization</p>
```

This keeps the SSE consumer stateless and avoids JSON parsing in JS. Can be upgraded to a Thymeleaf partial later.

## Tenant isolation invariant

Each emitter is registered under a specific `orgId`. The `@EventListener` only routes to emitters matching the event's `organizationId`. No cross-org data ever reaches an emitter.

## Error handling

- `SseEmitter.send()` throws `IOException` when the client disconnects mid-stream — caught and removed from the map
- `SseEmitter` timeout (60 s by default) fires `onTimeout`, which completes the emitter and removes it from the map — the browser reconnects automatically via HTMX SSE extension

## Testing

### Integration test: `ActivityStreamIntegrationTest` (template)

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers (Postgres + Redis)
- Requires `spring-webflux` in `testImplementation` scope (reactive client only, app stays servlet-based)
- Uses `WebTestClient` bound to the real server

**Test 1 — event delivered to correct org:**
1. Register an SSE connection for org A via `WebTestClient`
2. In a background thread, invoke `organizationService.inviteMember(...)` within org A's `TenantContext`
3. `result.responseBody.blockFirst(Duration.ofSeconds(5))` — assert the message contains the invited user ID

**Test 2 — no cross-org leakage:**
1. Register an SSE connection for org B
2. Trigger `inviteMember` for org A
3. Assert `result.responseBody.blockFirst(Duration.ofSeconds(2))` returns `null` (no event received by org B)

## Scope

This MVP is intentionally minimal:
- One event type (`MemberInvitedEvent`)
- In-process only (no Redis pub/sub, no cross-instance fanout)
- No persistence (events are fire-and-forget; missed events are not replayed on reconnect)

If a second SaaS needs the same event bus infrastructure, promote it to the starter. If cross-instance fanout is needed, swap `ActivityStreamService` internals for a Redis Pub/Sub subscriber — producer and consumer contracts stay the same.
