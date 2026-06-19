package tech.sergiodelgado.saastemplate.dashboard

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tech.sergiodelgado.saasstarter.organization.MemberInvitedEvent
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import java.util.UUID

class ActivityStreamServiceTest {

    private val service = ActivityStreamService()

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `on sends event only to the matching org's emitters`() {
        val orgA = UUID.randomUUID()
        val orgB = UUID.randomUUID()
        val emitterA = mockk<SseEmitter>(relaxed = true)
        val emitterB = mockk<SseEmitter>(relaxed = true)

        TenantContext.set(orgA)
        service.register(emitterA)
        TenantContext.clear()

        TenantContext.set(orgB)
        service.register(emitterB)
        TenantContext.clear()

        service.on(
            MemberInvitedEvent(
                organizationId = orgA,
                memberId = UUID.randomUUID(),
                invitedExternalUserId = "new-user",
                actorExternalUserId = "actor",
            )
        )

        verify(exactly = 1) { emitterA.send(any<SseEmitter.SseEventBuilder>()) }
        verify(exactly = 0) { emitterB.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test
    fun `on does nothing when no emitters are registered for the org`() {
        val orgId = UUID.randomUUID()
        service.on(
            MemberInvitedEvent(
                organizationId = orgId,
                memberId = UUID.randomUUID(),
                invitedExternalUserId = "new-user",
                actorExternalUserId = "actor",
            )
        )
        // No exception = pass
    }

    @Test
    fun `onTimeout removes emitter without calling complete`() {
        // The container finalises the async response itself on timeout; calling
        // complete() from the callback would throw AsyncRequestNotUsableException.
        val orgId = UUID.randomUUID()
        val emitter = mockk<SseEmitter>(relaxed = true)
        val timeoutCallback = slot<Runnable>()
        every { emitter.onTimeout(capture(timeoutCallback)) } just runs

        TenantContext.set(orgId)
        service.register(emitter)
        TenantContext.clear()

        timeoutCallback.captured.run()

        verify(exactly = 0) { emitter.complete() }
        // Verify removed: a subsequent event doesn't reach this emitter
        service.on(MemberInvitedEvent(
            organizationId = orgId,
            memberId = UUID.randomUUID(),
            invitedExternalUserId = "u",
            actorExternalUserId = "a",
        ))
        verify(exactly = 0) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test
    fun `onError removes emitter without calling complete`() {
        val orgId = UUID.randomUUID()
        val emitter = mockk<SseEmitter>(relaxed = true)
        val errorCallback = slot<java.util.function.Consumer<Throwable>>()
        every { emitter.onError(capture(errorCallback)) } just runs

        TenantContext.set(orgId)
        service.register(emitter)
        TenantContext.clear()

        errorCallback.captured.accept(RuntimeException("connection reset"))

        verify(exactly = 0) { emitter.complete() }
        // Verify removed: a subsequent event doesn't reach this emitter
        service.on(MemberInvitedEvent(
            organizationId = orgId,
            memberId = UUID.randomUUID(),
            invitedExternalUserId = "u",
            actorExternalUserId = "a",
        ))
        verify(exactly = 0) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test
    fun `sendHeartbeat sends comment to all registered emitters`() {
        val orgId = UUID.randomUUID()
        val emitter1 = mockk<SseEmitter>(relaxed = true)
        val emitter2 = mockk<SseEmitter>(relaxed = true)

        TenantContext.set(orgId)
        service.register(emitter1)
        service.register(emitter2)
        TenantContext.clear()

        service.sendHeartbeat()

        verify(exactly = 1) { emitter1.send(any<SseEmitter.SseEventBuilder>()) }
        verify(exactly = 1) { emitter2.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test
    fun `sendHeartbeat removes emitter that throws on send`() {
        val orgId = UUID.randomUUID()
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.send(any<SseEmitter.SseEventBuilder>()) } throws Exception("connection reset")

        TenantContext.set(orgId)
        service.register(emitter)
        TenantContext.clear()

        service.sendHeartbeat()

        // Second heartbeat should not attempt to send again — emitter was evicted
        service.sendHeartbeat()
        verify(exactly = 1) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }
}
