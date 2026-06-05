package tech.sergiodelgado.saastemplate.dashboard

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.organization.OrganizationService

@Controller
@RequestMapping("/dashboard")
class DashboardController(
    private val organizationService: OrganizationService,
    private val billingService: BillingService,
    private val activityStreamService: ActivityStreamService,
) {
    @GetMapping
    fun index(model: Model): String {
        model.addAttribute("organization", organizationService.current())
        model.addAttribute("subscription", billingService.currentSubscription())
        return "dashboard/index"
    }

    @GetMapping("/stats")
    fun stats(model: Model): String {
        model.addAttribute("organization", organizationService.current())
        model.addAttribute("subscription", billingService.currentSubscription())
        return "fragments/dashboard-stats"
    }

    @GetMapping("/activity-stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun activityStream(): SseEmitter {
        val emitter = SseEmitter(60_000L)
        activityStreamService.register(emitter)
        return emitter
    }
}
