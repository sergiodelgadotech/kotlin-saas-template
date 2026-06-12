package tech.sergiodelgado.saastemplate.auth.zitadel

import com.zitadel.ApiException
import com.zitadel.api.UserServiceApi
import com.zitadel.model.UserServiceAddHumanUserRequest
import com.zitadel.model.UserServiceAddHumanUserResponse
import com.zitadel.model.UserServiceCreateInviteCodeRequest
import com.zitadel.model.UserServiceCreateInviteCodeResponse
import com.zitadel.model.UserServiceListUsersRequest
import com.zitadel.model.UserServiceListUsersResponse
import com.zitadel.model.UserServiceUpdateHumanUserRequest
import com.zitadel.model.UserServiceUpdateHumanUserResponse
import com.zitadel.model.UserServiceUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import strikt.assertions.one

class ZitadelUserDirectoryTest {

    private val properties = ZitadelManagementProperties(
        baseUrl = "https://zitadel.example.com",
        pat = "test-pat",
        organizationId = "org-123",
        applicationName = "Test App"
    )

    private val userService = mockk<UserServiceApi>()

    private val directory = ZitadelUserDirectory(properties, userService, "https://app.example.com")

    @Test
    fun `returns userId when user found by email in correct org`() {
        val existingUser = UserServiceUser().userId("user-abc")
        val listResponse = UserServiceListUsersResponse().result(listOf(existingUser))

        every { userService.listUsers(any()) } returns listResponse

        val result = directory.findOrInvite("alice@example.com")

        expectThat(result).isEqualTo("user-abc")
        verify(exactly = 0) { userService.addHumanUser(any<UserServiceAddHumanUserRequest>()) }
    }

    @Test
    fun `list request includes both email and org filters`() {
        val existingUser = UserServiceUser().userId("user-abc")
        val listResponse = UserServiceListUsersResponse().result(listOf(existingUser))
        val requestSlot = slot<UserServiceListUsersRequest>()

        every { userService.listUsers(capture(requestSlot)) } returns listResponse

        directory.findOrInvite("alice@example.com")

        val queries = requestSlot.captured.queries!!
        expectThat(queries) {
            one { get { emailQuery?.emailAddress }.isEqualTo("alice@example.com") }
            one { get { organizationIdQuery?.organizationId }.isEqualTo("org-123") }
        }
    }

    @Test
    fun `creates user and returns new userId when no user found`() {
        val emptyListResponse = UserServiceListUsersResponse().result(emptyList())
        val createResponse = UserServiceAddHumanUserResponse().userId("new-user-xyz")
        val inviteResponse = mockk<UserServiceCreateInviteCodeResponse>(relaxed = true)

        every { userService.listUsers(any()) } returns emptyListResponse
        every { userService.addHumanUser(any<UserServiceAddHumanUserRequest>()) } returns createResponse
        every { userService.createInviteCode(any<UserServiceCreateInviteCodeRequest>()) } returns inviteResponse

        val result = directory.findOrInvite("bob@example.com")

        expectThat(result).isEqualTo("new-user-xyz")
        verify(exactly = 1) { userService.addHumanUser(any<UserServiceAddHumanUserRequest>()) }
    }

    @Test
    fun `create request derives placeholder name from email and sets no verification email`() {
        val emptyListResponse = UserServiceListUsersResponse().result(emptyList())
        val createResponse = UserServiceAddHumanUserResponse().userId("new-user-xyz")
        val inviteResponse = mockk<UserServiceCreateInviteCodeResponse>(relaxed = true)
        val requestSlot = slot<UserServiceAddHumanUserRequest>()

        every { userService.listUsers(any()) } returns emptyListResponse
        every { userService.addHumanUser(capture(requestSlot)) } returns createResponse
        every { userService.createInviteCode(any<UserServiceCreateInviteCodeRequest>()) } returns inviteResponse

        directory.findOrInvite("bob@example.com")

        val profile = requestSlot.captured.profile
        // "bob" is a single token → given = family = "Bob"; invitee corrects during onboarding
        expectThat(profile?.givenName).isEqualTo("Bob")
        expectThat(profile?.familyName).isEqualTo("Bob")
        // pre-verified so Zitadel doesn't send a redundant verification email alongside the invite
        expectThat(requestSlot.captured.email?.isVerified).isEqualTo(true)
        expectThat(requestSlot.captured.email?.sendCode).isNull()
    }

    @Test
    fun `create request splits dotted email into given and family name`() {
        val emptyListResponse = UserServiceListUsersResponse().result(emptyList())
        val createResponse = UserServiceAddHumanUserResponse().userId("new-user-xyz")
        val inviteResponse = mockk<UserServiceCreateInviteCodeResponse>(relaxed = true)
        val requestSlot = slot<UserServiceAddHumanUserRequest>()

        every { userService.listUsers(any()) } returns emptyListResponse
        every { userService.addHumanUser(capture(requestSlot)) } returns createResponse
        every { userService.createInviteCode(any<UserServiceCreateInviteCodeRequest>()) } returns inviteResponse

        directory.findOrInvite("alice.smith@example.com")

        val profile = requestSlot.captured.profile
        expectThat(profile?.givenName).isEqualTo("Alice")
        expectThat(profile?.familyName).isEqualTo("Smith")
    }

    @Test
    fun `create request is scoped to correct org`() {
        val emptyListResponse = UserServiceListUsersResponse().result(emptyList())
        val createResponse = UserServiceAddHumanUserResponse().userId("new-user-xyz")
        val inviteResponse = mockk<UserServiceCreateInviteCodeResponse>(relaxed = true)
        val requestSlot = slot<UserServiceAddHumanUserRequest>()

        every { userService.listUsers(any()) } returns emptyListResponse
        every { userService.addHumanUser(capture(requestSlot)) } returns createResponse
        every { userService.createInviteCode(any<UserServiceCreateInviteCodeRequest>()) } returns inviteResponse

        directory.findOrInvite("bob@example.com")

        expectThat(requestSlot.captured.organization?.orgId).isEqualTo("org-123")
        expectThat(requestSlot.captured.email?.email).isEqualTo("bob@example.com")
    }

    @Test
    fun `invite code is sent with url template and application name`() {
        val emptyListResponse = UserServiceListUsersResponse().result(emptyList())
        val createResponse = UserServiceAddHumanUserResponse().userId("new-user-xyz")
        val inviteResponse = mockk<UserServiceCreateInviteCodeResponse>(relaxed = true)
        val inviteSlot = slot<UserServiceCreateInviteCodeRequest>()

        every { userService.listUsers(any()) } returns emptyListResponse
        every { userService.addHumanUser(any<UserServiceAddHumanUserRequest>()) } returns createResponse
        every { userService.createInviteCode(capture(inviteSlot)) } returns inviteResponse

        directory.findOrInvite("bob@example.com")

        expectThat(inviteSlot.captured.userId).isEqualTo("new-user-xyz")
        expectThat(inviteSlot.captured.sendCode?.urlTemplate).isEqualTo("https://app.example.com/sign-in")
        expectThat(inviteSlot.captured.sendCode?.applicationName).isEqualTo("Test App")
    }

    @Test
    fun `throws IllegalArgumentException when found user has null userId`() {
        val userWithNullId = UserServiceUser().userId(null)
        val listResponse = UserServiceListUsersResponse().result(listOf(userWithNullId))

        every { userService.listUsers(any()) } returns listResponse

        assertThrows<IllegalArgumentException> {
            directory.findOrInvite("alice@example.com")
        }
    }

    @Test
    fun `throws IllegalArgumentException when created user response has null userId`() {
        val emptyListResponse = UserServiceListUsersResponse().result(emptyList())
        val createResponseWithNullId = UserServiceAddHumanUserResponse().userId(null)
        val inviteResponse = mockk<UserServiceCreateInviteCodeResponse>(relaxed = true)

        every { userService.listUsers(any()) } returns emptyListResponse
        every { userService.addHumanUser(any<UserServiceAddHumanUserRequest>()) } returns createResponseWithNullId
        every { userService.createInviteCode(any<UserServiceCreateInviteCodeRequest>()) } returns inviteResponse

        assertThrows<IllegalArgumentException> {
            directory.findOrInvite("bob@example.com")
        }
    }

    // ── updateProfile ────────────────────────────────────────────────────────

    @Test
    fun `updateProfile sends correct userId and names to Zitadel`() {
        val requestSlot = slot<UserServiceUpdateHumanUserRequest>()
        every { userService.updateHumanUser(capture(requestSlot)) } returns UserServiceUpdateHumanUserResponse()

        directory.updateProfile("user-123", "Alice", "Smith")

        expectThat(requestSlot.captured.userId).isEqualTo("user-123")
        expectThat(requestSlot.captured.profile?.givenName).isEqualTo("Alice")
        expectThat(requestSlot.captured.profile?.familyName).isEqualTo("Smith")
    }

    @Test
    fun `updateProfile wraps ApiException as IllegalStateException`() {
        every { userService.updateHumanUser(any<UserServiceUpdateHumanUserRequest>()) } throws
            ApiException(403, emptyMap(), "forbidden")

        assertThrows<IllegalStateException> {
            directory.updateProfile("user-123", "Alice", "Smith")
        }
    }
}
