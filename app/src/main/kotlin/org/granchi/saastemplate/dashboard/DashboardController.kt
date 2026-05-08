package org.granchi.saastemplate.dashboard

import org.granchi.saastemplate.billing.BillingService
import org.granchi.saastemplate.organization.OrganizationService
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Controller
@RequestMapping("/dashboard")
class DashboardController(
    private val organizationService: OrganizationService,
    private val billingService: BillingService
) {
    @GetMapping
    fun index(model: Model): String {
        model.addAttribute("organization", organizationService.current())
        model.addAttribute("subscription", billingService.currentSubscription())
        return "dashboard/index"
    }

    // HTMX polling endpoint — devuelve solo el fragmento de stats
    @GetMapping("/stats")
    fun stats(model: Model): String {
        model.addAttribute("organization", organizationService.current())
        return "fragments/dashboard-stats"
    }

    // SSE para actividad en tiempo real
    @GetMapping("/activity-stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun activityStream(): SseEmitter {
        val emitter = SseEmitter(60_000L)
        // TODO: suscribir al bus de eventos del dominio
        emitter.complete()
        return emitter
    }
}
