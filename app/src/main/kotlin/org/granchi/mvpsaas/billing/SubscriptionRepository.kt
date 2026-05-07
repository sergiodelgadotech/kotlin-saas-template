package org.granchi.mvpsaas.billing

import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface SubscriptionRepository : CrudRepository<Subscription, UUID> {
    fun findByOrganizationId(organizationId: UUID): Subscription?
    fun findByStripeCustomerId(stripeCustomerId: String): Subscription?
    fun findByStripeSubscriptionId(stripeSubscriptionId: String): Subscription?
}
