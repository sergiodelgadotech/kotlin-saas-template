# Plan 7 — Move Billing Module to Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Stripe-backed billing module (`Subscription` entity, `SubscriptionRepository`, `BillingService`, `StripeWebhookHandler`) from the template to the starter. Rename `stripeCustomerId` / `stripeSubscriptionId` to `externalCustomerId` / `externalSubscriptionId`. Introduce `BillingPlan` interface with `DefaultBillingPlan` enum (`STARTER`/`PRO`/`ENTERPRISE`). Apps configure plan-to-Stripe-price mapping via `saasstarter.billing.plan-prices`. Template keeps only `BillingController` and `StripeWebhookController` (route bindings), plus the property values populated from environment variables.

**Architecture:** New `billing` package in starter, wired by `BillingAutoConfiguration`. `BillingService` and `StripeWebhookHandler` are declared as `@Bean`s (no `@Service`), backed by a `BillingProperties` class for Stripe API key, webhook secret, success/cancel URLs, portal return URL, and the plan-to-price map. The autoconfig sets `Stripe.apiKey` (a static field on the Stripe SDK) at construction — fixing a runtime gap in the existing template (which never set it). Starter ships `V101__starter_subscriptions.sql`; template's `V200__app_init.sql` is stripped of `subscriptions` (it's now empty, so the migration file is deleted entirely).

**Tech Stack:** Stripe Java SDK, Spring Data JDBC, Flyway.

**Spec reference:** `docs/superpowers/specs/2026-05-07-starter-template-split-design.md` § Plan 7 + § Architectural decisions 4 & 6.

**Prerequisites:** Plans 0-6 complete. Template depends on `kotlin-saas-starter:0.7.0`. Local Postgres volume can be reset.

**Cross-repo:** Tasks 1-9 in `kotlin-saas-starter`. Tasks 10-14 in `kotlin-saas-template`. Task 15 finalizes as `0.8.0`.

**Bug fixes (incidental):**
- The existing `BillingService` does not set `Stripe.apiKey`, so any actual Stripe call would 401. The new autoconfig sets it.
- Two TODO comments in `StripeWebhookHandler` (Resend email on payment failure, real plan-ID mapping) are preserved as-is — out of scope for this plan.

---

## File Structure

**Created in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/billing/BillingPlan.kt` — interface + `DefaultBillingPlan` enum
- `src/main/kotlin/org/granchi/saasstarter/billing/SubscriptionStatus.kt` — enum
- `src/main/kotlin/org/granchi/saasstarter/billing/Subscription.kt` — entity
- `src/main/kotlin/org/granchi/saasstarter/billing/SubscriptionRepository.kt`
- `src/main/kotlin/org/granchi/saasstarter/billing/BillingService.kt` — no `@Service`
- `src/main/kotlin/org/granchi/saasstarter/billing/StripeWebhookHandler.kt` — no `@Service`
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/BillingAutoConfiguration.kt`
- `src/main/resources/db/migration/saasstarter/V101__starter_subscriptions.sql`
- `src/test/kotlin/org/granchi/saasstarter/billing/BillingPlanTest.kt`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/BillingAutoConfigurationTest.kt`
- `src/test/kotlin/org/granchi/saasstarter/billing/StripeWebhookHandlerTest.kt`

**Modified in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt` — add `Billing` group
- `src/main/resources/META-INF/spring/...AutoConfiguration.imports`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`
- `gradle/libs.versions.toml` — add Stripe library
- `build.gradle.kts` — declare Stripe as `api`

**Deleted in `kotlin-saas-template`:**
- `app/src/main/kotlin/org/granchi/saastemplate/billing/Subscription.kt`
- `app/src/main/kotlin/org/granchi/saastemplate/billing/SubscriptionRepository.kt`
- `app/src/main/kotlin/org/granchi/saastemplate/billing/BillingService.kt`
- `app/src/main/kotlin/org/granchi/saastemplate/billing/StripeWebhookHandler.kt`
- `app/src/main/resources/db/migration/V200__app_init.sql` (becomes empty after subscriptions move out)

**Modified in `kotlin-saas-template`:**
- `app/src/main/kotlin/org/granchi/saastemplate/billing/BillingController.kt` — import `Subscription`, `BillingService` from starter (file currently also contains `StripeWebhookController`)
- `app/src/main/resources/application.yml` — replace `stripe.*` with `saasstarter.billing.*`
- `gradle/libs.versions.toml` — bump to `0.8.0-SNAPSHOT` then `0.8.0`

**Created in `kotlin-saas-template`:**
- `app/src/test/kotlin/org/granchi/saastemplate/integration/BillingAutoConfigSmokeTest.kt`

---

### Task 1: Add Stripe SDK to starter dependencies and create plan/status types

All starter tasks happen inside `/var/home/serandel/Projects/kotlin-saas-starter`.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/org/granchi/saasstarter/billing/BillingPlan.kt`
- Create: `src/main/kotlin/org/granchi/saasstarter/billing/SubscriptionStatus.kt`
- Create: `src/test/kotlin/org/granchi/saasstarter/billing/BillingPlanTest.kt`

- [ ] **Step 1: Add Stripe to `libs.versions.toml`**

```toml
[versions]
stripe            = "29.1.0"

[libraries]
stripe                        = { module = "com.stripe:stripe-java", version.ref = "stripe" }
```

- [ ] **Step 2: Add `api(libs.stripe)` to `build.gradle.kts`**

In the `dependencies { ... }` block:

```kotlin
api(libs.stripe)
```

(`api` because `BillingService` and `StripeWebhookHandler` expose Stripe SDK classes in their public signatures.)

- [ ] **Step 3: Create the test directory and write the failing test**

```bash
mkdir -p src/test/kotlin/org/granchi/saasstarter/billing
mkdir -p src/main/kotlin/org/granchi/saasstarter/billing
```

`src/test/kotlin/org/granchi/saasstarter/billing/BillingPlanTest.kt`:

```kotlin
package org.granchi.saasstarter.billing

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isA

class BillingPlanTest {

    @Test
    fun `default billing plan enum has the three canonical values`() {
        expectThat(DefaultBillingPlan.values().toList())
            .containsExactly(DefaultBillingPlan.STARTER, DefaultBillingPlan.PRO, DefaultBillingPlan.ENTERPRISE)
    }

    @Test
    fun `default billing plan implements BillingPlan`() {
        expectThat(DefaultBillingPlan.STARTER as Any).isA<BillingPlan>()
    }
}
```

- [ ] **Step 4: Run the test — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.billing.BillingPlanTest"
```

Expected: `Unresolved reference`.

- [ ] **Step 5: Implement `BillingPlan.kt` and `SubscriptionStatus.kt`**

`BillingPlan.kt`:

```kotlin
package org.granchi.saasstarter.billing

/**
 * Marker interface for subscription plans. Apps replace [DefaultBillingPlan]
 * with their own enum (e.g. STARTER, PRO, ENTERPRISE, METERED) when needed.
 */
interface BillingPlan {
    val name: String
}

enum class DefaultBillingPlan : BillingPlan {
    STARTER, PRO, ENTERPRISE
}
```

`SubscriptionStatus.kt`:

```kotlin
package org.granchi.saasstarter.billing

/** Lifecycle states a subscription can be in. */
enum class SubscriptionStatus {
    TRIALING, ACTIVE, PAST_DUE, CANCELED
}
```

- [ ] **Step 6: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.billing.BillingPlanTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml \
        build.gradle.kts \
        src/main/kotlin/org/granchi/saasstarter/billing/BillingPlan.kt \
        src/main/kotlin/org/granchi/saasstarter/billing/SubscriptionStatus.kt \
        src/test/kotlin/org/granchi/saasstarter/billing/BillingPlanTest.kt
git commit -m "$(cat <<'EOF'
feat: add BillingPlan interface and SubscriptionStatus enum

DefaultBillingPlan provides STARTER/PRO/ENTERPRISE as the canonical set;
apps with custom plans implement BillingPlan. Stripe SDK is now an `api`
dependency since billing types appear in public signatures.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Create `Subscription` entity and `SubscriptionRepository`

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/billing/Subscription.kt`
- Create: `src/main/kotlin/org/granchi/saasstarter/billing/SubscriptionRepository.kt`

- [ ] **Step 1: Write the entity**

```kotlin
package org.granchi.saasstarter.billing

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("subscriptions")
data class Subscription(
    @Id val id: UUID = UUID.randomUUID(),
    val organizationId: UUID,
    /** Customer ID at the billing provider (Stripe `cus_*`, Paddle, etc.). */
    val externalCustomerId: String,
    /** Subscription ID at the billing provider; null until the customer completes checkout. */
    val externalSubscriptionId: String? = null,
    val plan: String = DefaultBillingPlan.STARTER.name,
    val status: SubscriptionStatus = SubscriptionStatus.TRIALING,
    val currentPeriodEnd: Instant? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val createdAt: Instant = Instant.now(),
) {
    fun isActive() = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING
}
```

`plan` is `String` so apps with custom `BillingPlan` enums aren't constrained.

- [ ] **Step 2: Write the repository**

```kotlin
package org.granchi.saasstarter.billing

import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface SubscriptionRepository : CrudRepository<Subscription, UUID> {
    fun findByOrganizationId(organizationId: UUID): Subscription?
    fun findByExternalCustomerId(externalCustomerId: String): Subscription?
    fun findByExternalSubscriptionId(externalSubscriptionId: String): Subscription?
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

---

### Task 3: Add `Billing` group to `SaasStarterProperties`

**Files:**
- Modify: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt`
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt`

- [ ] **Step 1: Add a failing test**

```kotlin
import strikt.assertions.containsKey

    @Test
    fun `billing properties bind from saasstarter billing`() {
        contextRunner
            .withPropertyValues(
                "saasstarter.billing.api-key=sk_test_xyz",
                "saasstarter.billing.webhook-secret=whsec_xyz",
                "saasstarter.billing.success-url=https://example.com/billing?ok=1",
                "saasstarter.billing.cancel-url=https://example.com/billing",
                "saasstarter.billing.portal-return-url=https://example.com/billing",
                "saasstarter.billing.plan-prices.STARTER=price_starter",
                "saasstarter.billing.plan-prices.PRO=price_pro",
            )
            .run { context ->
                val props = context.getBean(SaasStarterProperties::class.java)
                expectThat(props.billing.apiKey).isEqualTo("sk_test_xyz")
                expectThat(props.billing.webhookSecret).isEqualTo("whsec_xyz")
                expectThat(props.billing.planPrices).containsKey("STARTER")
                expectThat(props.billing.planPrices["PRO"]).isEqualTo("price_pro")
            }
    }
```

- [ ] **Step 2: Run — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: `Unresolved reference: billing`.

- [ ] **Step 3: Add the `Billing` data class**

Update `SaasStarterProperties.kt`:

```kotlin
@ConfigurationProperties(prefix = "saasstarter")
data class SaasStarterProperties(
    val enabled: Boolean = true,
    val session: Session = Session(),
    val jobs: Jobs = Jobs(),
    val cache: Cache = Cache(),
    val tenant: Tenant = Tenant(),
    val rateLimit: RateLimit = RateLimit(),
    val billing: Billing = Billing(),
) {
    // ... existing nested classes ...

    data class Billing(
        val enabled: Boolean = true,
        /** Stripe API key (`sk_*`). Set via STRIPE_API_KEY env var typically. */
        val apiKey: String = "",
        /** Stripe webhook signing secret (`whsec_*`). */
        val webhookSecret: String = "",
        /** URL to send the customer to after successful checkout. */
        val successUrl: String = "",
        /** URL to send the customer to if checkout is cancelled. */
        val cancelUrl: String = "",
        /** URL to send the customer to after closing the billing portal. */
        val portalReturnUrl: String = "",
        /**
         * Map of plan name (matching [BillingPlan.name]) to Stripe Price ID.
         * Apps populate this in application.yml; lookup is case-sensitive on
         * the plan name.
         */
        val planPrices: Map<String, String> = emptyMap(),
    )
}
```

- [ ] **Step 4: Run — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/billing/Subscription.kt \
        src/main/kotlin/org/granchi/saasstarter/billing/SubscriptionRepository.kt \
        src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt
git commit -m "$(cat <<'EOF'
feat: add Subscription entity, repository, and billing properties

Subscription uses externalCustomerId / externalSubscriptionId for
provider-agnostic naming; plan stored as String to allow custom BillingPlan
enums; status uses the SubscriptionStatus enum from the starter.
saasstarter.billing.* exposes Stripe API config plus a plan-to-price map.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Implement `BillingService`

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/billing/BillingService.kt`

- [ ] **Step 1: Write the service**

```kotlin
package org.granchi.saasstarter.billing

import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import org.granchi.saasstarter.autoconfigure.SaasStarterProperties
import org.granchi.saasstarter.tenant.TenantContext
import org.granchi.saasstarter.web.NotFoundException
import org.springframework.transaction.annotation.Transactional

@Transactional
open class BillingService(
    private val subscriptionRepository: SubscriptionRepository,
    private val properties: SaasStarterProperties,
) {

    fun currentSubscription(): Subscription =
        subscriptionRepository.findByOrganizationId(TenantContext.get())
            ?: throw NotFoundException("No subscription found for organization")

    fun createCheckoutSession(plan: BillingPlan): String {
        val sub = currentSubscription()
        val priceId = priceIdFor(plan)
        val session = Session.create(
            SessionCreateParams.builder()
                .setCustomer(sub.externalCustomerId)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1)
                        .build()
                )
                .setSuccessUrl(properties.billing.successUrl)
                .setCancelUrl(properties.billing.cancelUrl)
                .build()
        )
        return session.url
    }

    fun createPortalSession(): String {
        val sub = currentSubscription()
        return com.stripe.model.billingportal.Session.create(
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(sub.externalCustomerId)
                .setReturnUrl(properties.billing.portalReturnUrl)
                .build()
        ).url
    }

    private fun priceIdFor(plan: BillingPlan): String =
        properties.billing.planPrices[plan.name]
            ?: error("No Stripe price ID configured for plan ${plan.name}")
}
```

`open` so Spring's CGLIB proxy for `@Transactional` can subclass.

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

---

### Task 5: Implement `StripeWebhookHandler`

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/billing/StripeWebhookHandler.kt`
- Create: `src/test/kotlin/org/granchi/saasstarter/billing/StripeWebhookHandlerTest.kt`

- [ ] **Step 1: Write a failing test for the plan-mapping logic**

```kotlin
package org.granchi.saasstarter.billing

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class StripeWebhookHandlerTest {

    @Test
    fun `mapStatus maps Stripe status strings to SubscriptionStatus`() {
        val handler = StripeWebhookHandler(org.mockito.kotlin.mock())
        expectThat(handler.mapStatus("active")).isEqualTo(SubscriptionStatus.ACTIVE)
        expectThat(handler.mapStatus("trialing")).isEqualTo(SubscriptionStatus.TRIALING)
        expectThat(handler.mapStatus("past_due")).isEqualTo(SubscriptionStatus.PAST_DUE)
        expectThat(handler.mapStatus("incomplete")).isEqualTo(SubscriptionStatus.CANCELED)
    }
}
```

- [ ] **Step 2: Write the handler**

```kotlin
package org.granchi.saasstarter.billing

import com.stripe.model.Event
import com.stripe.model.Invoice
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
open class StripeWebhookHandler(
    private val subscriptionRepository: SubscriptionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(event: Event) {
        log.info("Processing Stripe event: ${event.type}")
        when (event.type) {
            "customer.subscription.created",
            "customer.subscription.updated"  -> handleSubscriptionUpdate(event)
            "customer.subscription.deleted"  -> handleSubscriptionCanceled(event)
            "invoice.payment_failed"         -> handlePaymentFailed(event)
            else -> log.debug("Ignoring Stripe event: ${event.type}")
        }
    }

    private fun handleSubscriptionUpdate(event: Event) {
        val stripeSub = event.dataObjectDeserializer
            .deserializeUnsafe() as com.stripe.model.Subscription

        val sub = subscriptionRepository.findByExternalCustomerId(stripeSub.customer) ?: run {
            log.warn("No subscription found for Stripe customer ${stripeSub.customer}")
            return
        }

        subscriptionRepository.save(
            sub.copy(
                externalSubscriptionId = stripeSub.id,
                plan                   = mapPlan(stripeSub),
                status                 = mapStatus(stripeSub.status),
                currentPeriodEnd       = Instant.ofEpochSecond(stripeSub.currentPeriodEnd),
                cancelAtPeriodEnd      = stripeSub.cancelAtPeriodEnd,
            )
        )
    }

    private fun handleSubscriptionCanceled(event: Event) {
        val stripeSub = event.dataObjectDeserializer
            .deserializeUnsafe() as com.stripe.model.Subscription

        val sub = subscriptionRepository.findByExternalSubscriptionId(stripeSub.id) ?: return
        subscriptionRepository.save(sub.copy(status = SubscriptionStatus.CANCELED))
    }

    private fun handlePaymentFailed(event: Event) {
        val invoice = event.dataObjectDeserializer.deserializeUnsafe() as Invoice
        val sub = subscriptionRepository.findByExternalCustomerId(invoice.customer) ?: return
        subscriptionRepository.save(sub.copy(status = SubscriptionStatus.PAST_DUE))
        // TODO: send notification email via Resend
    }

    internal fun mapStatus(status: String) = when (status) {
        "active"   -> SubscriptionStatus.ACTIVE
        "trialing" -> SubscriptionStatus.TRIALING
        "past_due" -> SubscriptionStatus.PAST_DUE
        else       -> SubscriptionStatus.CANCELED
    }

    private fun mapPlan(stripeSub: com.stripe.model.Subscription): String {
        val priceId = stripeSub.items.data.firstOrNull()?.price?.id
            ?: return DefaultBillingPlan.STARTER.name
        // TODO: derive plan name by looking up the priceId in saasstarter.billing.plan-prices
        return when {
            priceId.contains("pro")        -> DefaultBillingPlan.PRO.name
            priceId.contains("enterprise") -> DefaultBillingPlan.ENTERPRISE.name
            else                           -> DefaultBillingPlan.STARTER.name
        }
    }
}
```

`mapStatus` is `internal` so the test can call it directly without going through a real Stripe `Event`.

- [ ] **Step 3: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.billing.StripeWebhookHandlerTest"
```

Expected: BUILD SUCCESSFUL.

---

### Task 6: Implement `BillingAutoConfiguration`

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/BillingAutoConfiguration.kt`
- Create: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/BillingAutoConfigurationTest.kt`

- [ ] **Step 1: Write a failing test**

```kotlin
package org.granchi.saasstarter.autoconfigure

import com.stripe.Stripe
import org.granchi.saasstarter.billing.BillingService
import org.granchi.saasstarter.billing.StripeWebhookHandler
import org.granchi.saasstarter.billing.SubscriptionRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class BillingAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BillingAutoConfiguration::class.java))
        .withBean(SubscriptionRepository::class.java, { org.mockito.kotlin.mock() })

    @Test
    fun `BillingService and StripeWebhookHandler are registered`() {
        contextRunner.run { context ->
            expectThat(context.getBeansOfType(BillingService::class.java)).hasSize(1)
            expectThat(context.getBeansOfType(StripeWebhookHandler::class.java)).hasSize(1)
        }
    }

    @Test
    fun `Stripe apiKey static field is set from billing properties`() {
        contextRunner
            .withPropertyValues("saasstarter.billing.api-key=sk_test_runner_marker")
            .run {
                expectThat(Stripe.apiKey).isEqualTo("sk_test_runner_marker")
            }
    }

    @Test
    fun `autoconfig is skipped when billing enabled is false`() {
        contextRunner
            .withPropertyValues("saasstarter.billing.enabled=false")
            .run { context ->
                expectThat(context.getBeansOfType(BillingService::class.java)).hasSize(0)
            }
    }
}
```

- [ ] **Step 2: Run the test — fails to compile**

Expected: `Unresolved reference: BillingAutoConfiguration`.

- [ ] **Step 3: Implement the autoconfig**

```kotlin
package org.granchi.saasstarter.autoconfigure

import com.stripe.Stripe
import org.granchi.saasstarter.billing.BillingService
import org.granchi.saasstarter.billing.StripeWebhookHandler
import org.granchi.saasstarter.billing.SubscriptionRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import jakarta.annotation.PostConstruct

@AutoConfiguration
@ConditionalOnClass(name = ["com.stripe.Stripe"])
@ConditionalOnProperty(
    prefix = "saasstarter.billing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(SaasStarterProperties::class)
@EnableJdbcRepositories(basePackages = ["org.granchi.saasstarter.billing"])
class BillingAutoConfiguration(
    private val properties: SaasStarterProperties,
) {

    @PostConstruct
    fun configureStripe() {
        if (properties.billing.apiKey.isNotBlank()) {
            Stripe.apiKey = properties.billing.apiKey
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SubscriptionRepository::class)
    fun billingService(repo: SubscriptionRepository): BillingService =
        BillingService(repo, properties)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SubscriptionRepository::class)
    fun stripeWebhookHandler(repo: SubscriptionRepository): StripeWebhookHandler =
        StripeWebhookHandler(repo)
}
```

`@PostConstruct` runs after the autoconfig bean is created, setting `Stripe.apiKey` (the SDK's static field) from the configured property. This fixes the runtime gap noted in the plan header.

- [ ] **Step 4: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.BillingAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL.

If the "Stripe apiKey static field is set" test sees a stale value from a previous test run, reset it before each test:

```kotlin
@org.junit.jupiter.api.BeforeEach
fun resetStripeKey() {
    Stripe.apiKey = ""
}
```

- [ ] **Step 5: Commit Tasks 4-6**

```bash
git add src/main/kotlin/org/granchi/saasstarter/billing/BillingService.kt \
        src/main/kotlin/org/granchi/saasstarter/billing/StripeWebhookHandler.kt \
        src/main/kotlin/org/granchi/saasstarter/autoconfigure/BillingAutoConfiguration.kt \
        src/test/kotlin/org/granchi/saasstarter/billing/StripeWebhookHandlerTest.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/BillingAutoConfigurationTest.kt
git commit -m "$(cat <<'EOF'
feat: add BillingAutoConfiguration with BillingService and StripeWebhookHandler

- BillingService constructs Stripe checkout/portal sessions; plan-to-price
  mapping comes from saasstarter.billing.plan-prices.
- StripeWebhookHandler updates Subscription state from incoming events.
- BillingAutoConfiguration sets Stripe.apiKey from properties at @PostConstruct
  (fixing a runtime gap in the previous template wiring).
- Both classes use @Transactional and are open for CGLIB proxying.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Add `V101__starter_subscriptions.sql` migration and register the autoconfig

**Files:**
- Create: `src/main/resources/db/migration/saasstarter/V101__starter_subscriptions.sql`
- Modify: `src/main/resources/META-INF/spring/...AutoConfiguration.imports`
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`

- [ ] **Step 1: Write the migration**

```sql
-- V101__starter_subscriptions.sql — kotlin-saas-starter
-- Subscription table for billing module.

CREATE TABLE subscriptions (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID         NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    external_customer_id     VARCHAR(255) NOT NULL UNIQUE,
    external_subscription_id VARCHAR(255),
    plan                     VARCHAR(50)  NOT NULL DEFAULT 'STARTER',
    status                   VARCHAR(50)  NOT NULL DEFAULT 'TRIALING',
    current_period_end       TIMESTAMPTZ,
    cancel_at_period_end     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_external_customer_id     ON subscriptions(external_customer_id);
CREATE INDEX idx_subscriptions_external_subscription_id ON subscriptions(external_subscription_id);
```

- [ ] **Step 2: Append `BillingAutoConfiguration` to imports file**

Final contents:

```
org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration
org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration
org.granchi.saasstarter.autoconfigure.JobRunrAutoConfiguration
org.granchi.saasstarter.autoconfigure.RedisAutoConfiguration
org.granchi.saasstarter.autoconfigure.WebMvcAutoConfiguration
org.granchi.saasstarter.autoconfigure.OrganizationAutoConfiguration
org.granchi.saasstarter.autoconfigure.BillingAutoConfiguration
```

- [ ] **Step 3: Add the corresponding test**

```kotlin
    @Test
    fun `imports file lists BillingAutoConfiguration`() {
        val resource = this::class.java.classLoader.getResource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
        expectThat(resource).isNotNull()
        expectThat(resource!!.readText())
            .contains("org.granchi.saasstarter.autoconfigure.BillingAutoConfiguration")
    }
```

- [ ] **Step 4: Run all tests**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/saasstarter/V101__starter_subscriptions.sql \
        src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt
git commit -m "$(cat <<'EOF'
feat: ship subscriptions schema and register BillingAutoConfiguration

V101__starter_subscriptions.sql adds the subscriptions table with
external_customer_id / external_subscription_id naming. BillingAutoConfiguration
is added to AutoConfiguration.imports.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Publish `0.8.0-SNAPSHOT` to mavenLocal

- [ ] **Step 1: Publish**

```bash
./gradlew publishToMavenLocal -Pversion=0.8.0-SNAPSHOT
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Verify the JAR includes V101**

```bash
unzip -l ~/.m2/repository/org/granchi/kotlin-saas-starter/0.8.0-SNAPSHOT/kotlin-saas-starter-0.8.0-SNAPSHOT.jar \
       | grep -E "V100|V101"
```

Expected: both `V100__starter_baseline.sql` and `V101__starter_subscriptions.sql`.

---

### Task 9: (Reserved) — starter side complete.

---

### Task 10: Bump starter version, replace `stripe.*` properties with `saasstarter.billing.*`

All template tasks happen inside `/var/home/serandel/Projects/kotlin-saas-template`.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/src/main/resources/application.yml`

- [ ] **Step 1: Bump starter version**

```toml
kotlin-saas-starter = "0.8.0-SNAPSHOT"
```

- [ ] **Step 2: Replace the `stripe:` block in `application.yml`**

Remove the existing block:

```yaml
stripe:
  api-key: ${STRIPE_API_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  price-id:
    starter: ${STRIPE_PRICE_STARTER}
    pro: ${STRIPE_PRICE_PRO}
```

Add to (or extend) the `saasstarter:` block:

```yaml
saasstarter:
  # ... existing cache, tenant, rate-limit blocks ...
  billing:
    api-key: ${STRIPE_API_KEY}
    webhook-secret: ${STRIPE_WEBHOOK_SECRET}
    success-url: ${APP_BASE_URL}/billing?success=true
    cancel-url: ${APP_BASE_URL}/billing
    portal-return-url: ${APP_BASE_URL}/billing
    plan-prices:
      STARTER: ${STRIPE_PRICE_STARTER}
      PRO: ${STRIPE_PRICE_PRO}
      # ENTERPRISE not currently sold; uncomment when ready
      # ENTERPRISE: ${STRIPE_PRICE_ENTERPRISE}
```

The keys `STARTER`, `PRO` match `DefaultBillingPlan.name`.

- [ ] **Step 3: Refresh Gradle dependencies**

```bash
./gradlew :app:dependencies --configuration runtimeClasspath --refresh-dependencies | grep kotlin-saas-starter
```

Expected: `org.granchi:kotlin-saas-starter:0.8.0-SNAPSHOT`.

---

### Task 11: Strip `subscriptions` from V200 (or delete it entirely) and reset DB

**Files:**
- Delete (or empty): `app/src/main/resources/db/migration/V200__app_init.sql`

After Plan 6, V200 contained only the `subscriptions` table. With Plan 7 moving that to the starter's V101, V200 has no app-specific content left. Delete the file.

- [ ] **Step 1: Delete V200**

```bash
git rm app/src/main/resources/db/migration/V200__app_init.sql
```

- [ ] **Step 2: Reset local Postgres volume**

```bash
docker compose down -v
docker compose up -d
```

Expected: clean containers.

The new migration order is: starter's V100 (organizations, members) → V101 (subscriptions) → template's V201 (jobs) → V202 (analysis).

---

### Task 12: Update template's billing controllers to use starter classes

**Files:**
- Modify: `app/src/main/kotlin/org/granchi/saastemplate/billing/BillingController.kt` (also contains `StripeWebhookController`)

- [ ] **Step 1: Update imports**

Replace the imports referencing template's billing types with starter equivalents:

```kotlin
package org.granchi.saastemplate.billing

import com.stripe.exception.SignatureVerificationException
import com.stripe.net.Webhook
import org.granchi.saasstarter.autoconfigure.SaasStarterProperties
import org.granchi.saasstarter.billing.BillingPlan
import org.granchi.saasstarter.billing.BillingService
import org.granchi.saasstarter.billing.DefaultBillingPlan
import org.granchi.saasstarter.billing.StripeWebhookHandler
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhooks/stripe")
class StripeWebhookController(
    private val webhookHandler: StripeWebhookHandler,
    private val properties: SaasStarterProperties,
) {

    @PostMapping
    fun handle(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String,
    ): ResponseEntity<Unit> {
        val event = try {
            Webhook.constructEvent(payload, signature, properties.billing.webhookSecret)
        } catch (e: SignatureVerificationException) {
            return ResponseEntity.badRequest().build()
        }
        webhookHandler.handle(event)
        return ResponseEntity.ok().build()
    }
}

@Controller
@RequestMapping("/billing")
class BillingController(private val billingService: BillingService) {

    @GetMapping
    fun index(model: Model): String {
        model.addAttribute("subscription", billingService.currentSubscription())
        return "billing/index"
    }

    @PostMapping("/checkout")
    fun checkout(@RequestParam plan: DefaultBillingPlan): String {
        val url = billingService.createCheckoutSession(plan)
        return "redirect:$url"
    }

    @PostMapping("/portal")
    fun portal(): String {
        val url = billingService.createPortalSession()
        return "redirect:$url"
    }
}
```

Notes on what changed:
- Import from `org.granchi.saasstarter.billing` instead of template's billing package.
- `StripeWebhookController` reads the webhook secret from the starter's properties bean rather than `@Value("\${stripe.webhook-secret}")`.
- `BillingController.checkout` accepts `DefaultBillingPlan` for the `@RequestParam` (Spring binds the plan name from the query string). Apps using a custom `BillingPlan` enum would change this to their own enum type.

- [ ] **Step 2: Update template's Thymeleaf templates if they reference renamed fields**

```bash
grep -rn "stripeCustomerId\|stripeSubscriptionId" app/src/main/resources/templates 2>/dev/null
```

If any matches, replace with `externalCustomerId` / `externalSubscriptionId`.

---

### Task 13: Delete template's billing source files

**Files:**
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/billing/Subscription.kt`
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/billing/SubscriptionRepository.kt`
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/billing/BillingService.kt`
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/billing/StripeWebhookHandler.kt`

- [ ] **Step 1: Delete the four files**

```bash
git rm app/src/main/kotlin/org/granchi/saastemplate/billing/Subscription.kt \
       app/src/main/kotlin/org/granchi/saastemplate/billing/SubscriptionRepository.kt \
       app/src/main/kotlin/org/granchi/saastemplate/billing/BillingService.kt \
       app/src/main/kotlin/org/granchi/saastemplate/billing/StripeWebhookHandler.kt
```

- [ ] **Step 2: Verify the project compiles**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL.

---

### Task 14: Add a smoke test confirming starter wiring + DB schema

**Files:**
- Create: `app/src/test/kotlin/org/granchi/saastemplate/integration/BillingAutoConfigSmokeTest.kt`

- [ ] **Step 1: Write the smoke test**

```kotlin
package org.granchi.saastemplate.integration

import com.stripe.Stripe
import org.granchi.saasstarter.billing.BillingService
import org.granchi.saasstarter.billing.StripeWebhookHandler
import org.granchi.saasstarter.billing.Subscription
import org.granchi.saasstarter.billing.SubscriptionRepository
import org.granchi.saasstarter.organization.Organization
import org.granchi.saasstarter.organization.OrganizationRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty

@Tag("integration")
@SpringBootTest
class BillingAutoConfigSmokeTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    @Autowired
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Test
    fun `BillingService and StripeWebhookHandler are wired from starter`() {
        expectThat(context.getBeansOfType(BillingService::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(StripeWebhookHandler::class.java)).hasSize(1)
    }

    @Test
    fun `Stripe apiKey is set from properties`() {
        // Provided in application-test.yml or env var; should be non-empty.
        expectThat(Stripe.apiKey).isNotEmpty()
    }

    @Test
    fun `subscription can be persisted with externalCustomerId`() {
        val org = organizationRepository.save(Organization(name = "Smoke Org", slug = "smoke-billing"))
        val sub = subscriptionRepository.save(
            Subscription(organizationId = org.id, externalCustomerId = "cus_test_smoke")
        )
        val reloaded = subscriptionRepository.findByExternalCustomerId("cus_test_smoke")
        expectThat(reloaded?.id).isEqualTo(sub.id)
    }
}
```

- [ ] **Step 2: Provide a Stripe test API key for tests**

Edit `app/src/test/resources/application-test.yml` to set:

```yaml
saasstarter:
  billing:
    api-key: sk_test_dummy_for_tests
```

(The test never actually calls Stripe — `currentSubscription()` and `findByExternalCustomerId` only hit the DB. We just need a non-empty `api-key` so `Stripe.apiKey` is set in the smoke test.)

- [ ] **Step 3: Run the smoke test**

```bash
./gradlew :app:integrationTest --tests "org.granchi.saastemplate.integration.BillingAutoConfigSmokeTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run full test suite**

```bash
./gradlew :app:test :app:integrationTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml \
        app/src/main/resources/application.yml \
        app/src/test/resources/application-test.yml \
        app/src/main/resources/db/migration/V200__app_init.sql \
        app/src/main/kotlin/org/granchi/saastemplate/billing/Subscription.kt \
        app/src/main/kotlin/org/granchi/saastemplate/billing/SubscriptionRepository.kt \
        app/src/main/kotlin/org/granchi/saastemplate/billing/BillingService.kt \
        app/src/main/kotlin/org/granchi/saastemplate/billing/StripeWebhookHandler.kt \
        app/src/main/kotlin/org/granchi/saastemplate/billing/BillingController.kt \
        app/src/test/kotlin/org/granchi/saastemplate/integration/BillingAutoConfigSmokeTest.kt
git commit -m "$(cat <<'EOF'
refactor: delegate billing module to kotlin-saas-starter 0.8.0

- Subscription, SubscriptionRepository, BillingService, StripeWebhookHandler
  now live in the starter (with externalCustomerId / externalSubscriptionId).
- application.yml: stripe.* replaced with saasstarter.billing.*.
- BillingController.checkout takes DefaultBillingPlan; StripeWebhookController
  reads webhook secret from starter properties.
- V200__app_init.sql is removed (subscriptions moved to starter's V101).
- Smoke test verifies the starter beans are wired and Stripe.apiKey is set.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 15: Open release PRs and finalize

- [ ] **Step 1: Push starter branch and open PR**

In `/var/home/serandel/Projects/kotlin-saas-starter`:

```bash
git push -u origin <branch-name>
gh pr create --title "feat: add billing module (Subscription, BillingService, StripeWebhookHandler)" \
             --body "$(cat <<'EOF'
## Summary

- Adds Subscription entity (externalCustomerId / externalSubscriptionId), SubscriptionRepository, BillingService, StripeWebhookHandler.
- BillingPlan interface with DefaultBillingPlan enum (STARTER/PRO/ENTERPRISE) for app extensibility.
- Stripe.apiKey is set from saasstarter.billing.api-key at @PostConstruct (fixing prior runtime gap in templates).
- Plan-to-Stripe-price mapping configured via saasstarter.billing.plan-prices.
- Ships V101__starter_subscriptions.sql under db/migration/saasstarter/.

Implements Plan 7 of docs/superpowers/specs/2026-05-07-starter-template-split-design.md.

## Test plan

- [x] BillingPlanTest: enum and interface
- [x] StripeWebhookHandlerTest: status mapping
- [x] BillingAutoConfigurationTest: bean wiring, Stripe.apiKey set, kill switch
- [x] AutoConfigurationImportsTest covers the new entry
- [x] Template BillingAutoConfigSmokeTest verifies wiring + DB persistence

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: Wait for CI, merge starter PR, merge release-please PR**

After release-please's PR merges, `0.8.0` is published.

- [ ] **Step 3: Bump template to released `0.8.0`**

```bash
sed -i 's/kotlin-saas-starter = "0.8.0-SNAPSHOT"/kotlin-saas-starter = "0.8.0"/' \
       gradle/libs.versions.toml
```

- [ ] **Step 4: Re-run tests**

```bash
./gradlew :app:test :app:integrationTest --refresh-dependencies
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit and push**

```bash
git add gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
build: bump kotlin-saas-starter to 0.8.0

Replaces 0.8.0-SNAPSHOT with the released version (Plan 7: billing module).
Completes the starter/template split.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git push
```

- [ ] **Step 6: (Optional) Tag template milestone**

The full split is now complete. Optionally tag the template:

```bash
git tag -a "v0.1.0-split-complete" -m "Starter/template split complete (kotlin-saas-starter 0.8.0)"
git push origin v0.1.0-split-complete
```

- [ ] **Step 7: (Optional) Promote starter to 1.0.0**

The starter's API surface is now stable (covers all of Plans 1-7). If desired, open a PR to release-please to promote `0.8.0` to `1.0.0`. This is purely a versioning signal — no code changes required.

---

## Done When

- `kotlin-saas-starter` 0.8.0 is published with the full billing module.
- Template depends on `0.8.0`; `app/src/main/kotlin/org/granchi/saastemplate/billing/` contains only `BillingController.kt` (which holds both `BillingController` and `StripeWebhookController`).
- `BillingAutoConfigSmokeTest`, `OrganizationAutoConfigSmokeTest`, and `TenantIsolationTest` all pass.
- Field rename complete: no `stripeCustomerId` / `stripe_customer_id` references in code or schema.
- Local DB applies all migrations cleanly: V100 (orgs+members) → V101 (subscriptions) → V201 (jobs) → V202 (analysis).
- `Stripe.apiKey` is set at startup (verified by smoke test).
- The template's `app/src/main/kotlin/org/granchi/saastemplate/` matches the spec's "Final state of the template" diagram in § 6.
- Both repos' commits are pushed.
