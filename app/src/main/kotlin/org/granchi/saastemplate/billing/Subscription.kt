package org.granchi.saastemplate.billing

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("subscriptions")
data class Subscription(
    @Id val id: UUID = UUID.randomUUID(),
    val organizationId: UUID,
    val stripeCustomerId: String,
    val stripeSubscriptionId: String? = null,
    val plan: Plan = Plan.STARTER,
    val status: Status = Status.TRIALING,
    val currentPeriodEnd: Instant? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    enum class Plan { STARTER, PRO, ENTERPRISE }
    enum class Status { TRIALING, ACTIVE, PAST_DUE, CANCELED }

    fun isActive() = status == Status.ACTIVE || status == Status.TRIALING
}
