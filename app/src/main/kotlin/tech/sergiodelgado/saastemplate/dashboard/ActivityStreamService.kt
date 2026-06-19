package tech.sergiodelgado.saastemplate.dashboard

import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.util.HtmlUtils
import tech.sergiodelgado.saasstarter.organization.MemberInvitedEvent
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class ActivityStreamService {

    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    fun register(emitter: SseEmitter) {
        val orgId = TenantContext.get()
        val list = emitters.computeIfAbsent(orgId) { CopyOnWriteArrayList() }
        list.add(emitter)
        emitter.onCompletion { list.remove(emitter) }
        // The container finalises the async response itself on timeout/error
        // (onCompletion fires afterwards). Calling complete() here would try to
        // flush a response that is already unusable and throw
        // AsyncRequestNotUsableException.
        emitter.onTimeout { list.remove(emitter) }
        emitter.onError { list.remove(emitter) }
    }

    @Scheduled(fixedRate = 25_000)
    fun sendHeartbeat() {
        val heartbeat = SseEmitter.event().comment("ka")
        emitters.values.forEach { list ->
            val toRemove = mutableListOf<SseEmitter>()
            list.forEach { emitter ->
                try {
                    emitter.send(heartbeat)
                } catch (_: Exception) {
                    toRemove.add(emitter)
                }
            }
            list.removeAll(toRemove)
        }
    }

    // Fires only after the transaction commits — guarantees no SSE event is sent
    // for a member that was never actually persisted (e.g. transaction rolled back).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberInvitedEvent) {
        val list = emitters[event.organizationId] ?: return
        val escaped = HtmlUtils.htmlEscape(event.invitedExternalUserId)
        val message = SseEmitter.event()
            .data(
                """<p class="text-sm">$escaped was invited to the organization</p>""",
                MediaType.TEXT_HTML
            )
        val toRemove = mutableListOf<SseEmitter>()
        list.forEach { emitter ->
            try {
                emitter.send(message)
            } catch (_: Exception) {
                toRemove.add(emitter)
            }
        }
        list.removeAll(toRemove)
    }
}
