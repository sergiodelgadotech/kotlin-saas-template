package tech.sergiodelgado.saastemplate.integration.web

import io.mockk.every
import io.mockk.mockk
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.tenant.TenantResolver
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.UUID

@TestConfiguration
class WebRenderTestMocks {

    companion object {
        // Matches V201__dev_seed.sql: organization '00000000-...-0001', member 'local-dev-user'.
        val DEV_ORG_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

        val billing: BillingService = mockk(relaxed = true)
    }

    @Bean
    @Primary
    fun billingService(): BillingService = billing

    // The 'test' profile's ProbeConfig (WebMvcAutoConfigSmokeTest) exposes a relaxed
    // TenantResolver mock named 'mockTenantResolver' that returns null, making
    // TenantInterceptor send 403 for every authenticated render-test request.
    // Override the same bean name (requires spring.main.allow-bean-definition-overriding=true
    // in @TestPropertySource) with a stub that returns the dev-org UUID so
    // OrganizationService.current() can resolve the seed org from the DB.
    @Bean("mockTenantResolver")
    @Primary
    fun tenantResolver(): TenantResolver = mockk {
        every { resolveTenantId(any()) } returns DEV_ORG_ID
    }
}
