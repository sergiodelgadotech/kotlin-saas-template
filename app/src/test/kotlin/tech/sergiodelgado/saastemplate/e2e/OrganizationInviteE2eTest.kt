package tech.sergiodelgado.saastemplate.e2e

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.endsWith
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory

@Import(OrganizationInviteE2eTest.IdpStubConfig::class)
class OrganizationInviteE2eTest : PlaywrightE2eTestBase() {

    @TestConfiguration
    class IdpStubConfig {
        @Bean
        @Primary
        fun idpUserDirectory(): IdpUserDirectory =
            IdpUserDirectory { email -> "e2e-sub-${email.replace("@", "-at-")}".take(50) }
    }

    @Test
    fun `members page has invite link`() {
        page.navigate(url("/organization/members"))

        val body = page.locator("body").innerText()
        expectThat(body).contains("Invite member")
    }

    @Test
    fun `clicking invite link navigates to invite form`() {
        page.navigate(url("/organization/members"))
        page.locator("a[href*='members/invite']").click()

        expectThat(page.url()).endsWith("/organization/members/invite")
    }

    @Test
    fun `valid invite redirects to members page with success flash`() {
        page.navigate(url("/organization/members/invite"))
        page.locator("input[name='email']").fill("invited@example.com")
        page.locator("select[name='role']").selectOption("MEMBER")
        page.waitForNavigation {
            page.locator("form[action*='members/invite'] button[type='submit']").click()
        }

        expectThat(page.url()).endsWith("/organization/members")
        val body = page.locator("body").innerText()
        expectThat(body).contains("Invitation sent to invited@example.com")
        expectThat(body).contains("invited@example.com")
    }

    @Test
    fun `invalid email shows validation error on form`() {
        page.navigate(url("/organization/members/invite"))
        // Fill in a syntactically invalid email and bypass browser validation.
        // Input values are HTML attributes, not text nodes — check the error message text instead.
        page.evaluate("document.querySelector('input[name=email]').type = 'text'")
        page.locator("input[name='email']").fill("not-an-email")
        page.locator("form[action*='members/invite'] button[type='submit']").click()

        val body = page.locator("body").innerText()
        expectThat(body).contains("Email must be a valid email address")
    }
}
