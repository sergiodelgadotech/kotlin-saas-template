package org.granchi.saastemplate.integration

import org.granchi.saastemplate.SaasTemplateApplication
import org.granchi.saasstarter.jobs.JobSchedulerService
import org.granchi.saasstarter.jobs.TenantJobFilter
import org.granchi.saasstarter.tenant.TenantContext
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// The test profile disables the JobRunr background server; override it here
// so that enqueued jobs actually execute within the test.
@Tag("integration")
@Testcontainers
@SpringBootTest(
    classes = [SaasTemplateApplication::class],
    properties = ["org.jobrunr.background-job-server.enabled=true"],
)
@ActiveProfiles("test")
class JobRunrAutoConfigSmokeTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired private lateinit var context: ApplicationContext
    @Autowired private lateinit var jobCapture: SmokeJobCapture

    @Test
    fun `JobRunrAutoConfiguration wires TenantJobFilter and JobSchedulerService`() {
        expectThat(context.getBeansOfType(TenantJobFilter::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(JobSchedulerService::class.java)).hasSize(1)
    }

    @Test
    fun `scheduled job runs and inherits tenant context from caller`() {
        val tenantId = UUID.randomUUID()
        val latch = CountDownLatch(1)
        jobCapture.prime(latch)

        TenantContext.set(tenantId)
        try {
            // scheduleCapture() is defined on the Spring bean so JobRunr can
            // serialize the lambda with the bean as its execution context.
            jobCapture.scheduleCapture()
        } finally {
            TenantContext.clear()
        }

        val ranWithinTimeout = latch.await(15, TimeUnit.SECONDS)
        expectThat(ranWithinTimeout).isEqualTo(true)
        expectThat(jobCapture.capturedTenantId.get()).isNotNull().isEqualTo(tenantId)
    }
}

// A Spring-managed job target so that JobRunr's JobActivator can resolve it
// from the ApplicationContext. Only active in the test profile to avoid
// polluting the production context.
@Component
@Profile("test")
class SmokeJobCapture(private val jobSchedulerService: JobSchedulerService) {

    val capturedTenantId = AtomicReference<UUID?>()
    private val latch = AtomicReference<CountDownLatch?>()

    fun prime(l: CountDownLatch) {
        capturedTenantId.set(null)
        latch.set(l)
    }

    // The lambda is defined here (inside a Spring bean), not in the test class,
    // so JobRunr can serialize it and resolve this bean from the ApplicationContext.
    fun scheduleCapture() {
        jobSchedulerService.enqueue { capture() }
    }

    fun capture() {
        capturedTenantId.set(if (TenantContext.isPresent()) TenantContext.get() else null)
        latch.get()?.countDown()
    }
}
