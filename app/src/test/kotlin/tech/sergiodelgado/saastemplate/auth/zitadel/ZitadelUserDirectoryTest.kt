package tech.sergiodelgado.saastemplate.auth.zitadel

import com.zitadel.api.BetaUserServiceApi
import com.zitadel.model.BetaUserServiceAddHumanUserRequest
import com.zitadel.model.BetaUserServiceAddHumanUserResponse
import com.zitadel.model.BetaUserServiceListUsersRequest
import com.zitadel.model.BetaUserServiceListUsersResponse
import com.zitadel.model.BetaUserServiceUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.one

class ZitadelUserDirectoryTest {

    private val properties = ZitadelManagementProperties(
        baseUrl = "https://zitadel.example.com",
        pat = "test-pat",
        organizationId = "org-123"
    )

    private val betaUsers = mockk<BetaUserServiceApi>()

    private val directory = ZitadelUserDirectory(properties, betaUsers)

    @Test
    fun `returns userId when user found by email in correct org`() {
        val existingUser = BetaUserServiceUser().userId("user-abc")
        val listResponse = BetaUserServiceListUsersResponse().result(listOf(existingUser))

        every { betaUsers.listUsers(any()) } returns listResponse

        val result = directory.findOrInvite("alice@example.com")

        expectThat(result).isEqualTo("user-abc")
        verify(exactly = 0) { betaUsers.addHumanUser(any<BetaUserServiceAddHumanUserRequest>()) }
    }

    @Test
    fun `list request includes both email and org filters`() {
        val existingUser = BetaUserServiceUser().userId("user-abc")
        val listResponse = BetaUserServiceListUsersResponse().result(listOf(existingUser))
        val requestSlot = slot<BetaUserServiceListUsersRequest>()

        every { betaUsers.listUsers(capture(requestSlot)) } returns listResponse

        directory.findOrInvite("alice@example.com")

        val queries = requestSlot.captured.queries!!
        expectThat(queries) {
            one { get { emailQuery?.emailAddress }.isEqualTo("alice@example.com") }
            one { get { organizationIdQuery?.organizationId }.isEqualTo("org-123") }
        }
    }

    @Test
    fun `creates user and returns new userId when no user found`() {
        val emptyListResponse = BetaUserServiceListUsersResponse().result(emptyList())
        val createResponse = BetaUserServiceAddHumanUserResponse().userId("new-user-xyz")

        every { betaUsers.listUsers(any()) } returns emptyListResponse
        every { betaUsers.addHumanUser(any<BetaUserServiceAddHumanUserRequest>()) } returns createResponse

        val result = directory.findOrInvite("bob@example.com")

        expectThat(result).isEqualTo("new-user-xyz")
        verify(exactly = 1) { betaUsers.addHumanUser(any<BetaUserServiceAddHumanUserRequest>()) }
    }

    @Test
    fun `create request is scoped to correct org`() {
        val emptyListResponse = BetaUserServiceListUsersResponse().result(emptyList())
        val createResponse = BetaUserServiceAddHumanUserResponse().userId("new-user-xyz")
        val requestSlot = slot<BetaUserServiceAddHumanUserRequest>()

        every { betaUsers.listUsers(any()) } returns emptyListResponse
        every { betaUsers.addHumanUser(capture(requestSlot)) } returns createResponse

        directory.findOrInvite("bob@example.com")

        expectThat(requestSlot.captured.organization?.orgId).isEqualTo("org-123")
        expectThat(requestSlot.captured.email?.email).isEqualTo("bob@example.com")
    }

    @Test
    fun `throws IllegalArgumentException when found user has null userId`() {
        val userWithNullId = BetaUserServiceUser().userId(null)
        val listResponse = BetaUserServiceListUsersResponse().result(listOf(userWithNullId))

        every { betaUsers.listUsers(any()) } returns listResponse

        assertThrows<IllegalArgumentException> {
            directory.findOrInvite("alice@example.com")
        }
    }

    @Test
    fun `throws IllegalArgumentException when created user response has null userId`() {
        val emptyListResponse = BetaUserServiceListUsersResponse().result(emptyList())
        val createResponseWithNullId = BetaUserServiceAddHumanUserResponse().userId(null)

        every { betaUsers.listUsers(any()) } returns emptyListResponse
        every { betaUsers.addHumanUser(any<BetaUserServiceAddHumanUserRequest>()) } returns createResponseWithNullId

        assertThrows<IllegalArgumentException> {
            directory.findOrInvite("bob@example.com")
        }
    }
}
