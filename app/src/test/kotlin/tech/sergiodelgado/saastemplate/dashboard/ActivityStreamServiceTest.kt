package tech.sergiodelgado.saastemplate.dashboard

import io.mockk.mockk
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
}
