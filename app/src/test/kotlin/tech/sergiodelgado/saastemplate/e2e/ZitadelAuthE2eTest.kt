package tech.sergiodelgado.saastemplate.e2e

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.endsWith

class ZitadelAuthE2eTest : PlaywrightE2eTestBase() {

    @BeforeEach
    fun skipUnlessEnabled() {
        assumeTrue(
            System.getenv("ZITADEL_E2E_ENABLED") == "true",
            "Set ZITADEL_E2E_ENABLED=true to run Zitadel auth E2E tests",
        )
    }

    @Test
    fun `sign-in navigates to Zitadel hosted login`() {
        page.navigate(url("/sign-in"))
        // Redirect chain: /sign-in → /oauth2/authorization/zitadel → Zitadel hosted login
        expectThat(page.url()).contains("localhost:8089")
    }

    @Test
    fun `full sign-in flow with seeded test user lands on onboarding organization`() {
        page.navigate(url("/sign-in"))

        // Zitadel hosted login: fill login name, submit, fill password, submit
        page.locator("input[name='loginName'], input[type='email']").fill("test@example.com")
        page.locator("button[type='submit']").first().click()
        page.locator("input[name='password'], input[type='password']").fill("Test1234!")
        page.waitForNavigation { page.locator("button[type='submit']").first().click() }

        // test@example.com has no org row → ZitadelAuthenticationSuccessHandler redirects here
        expectThat(page.url()).endsWith("/onboarding/organization")
    }

    @Test
    fun `onboarding wizard - sign-in then create org then choose free trial then land on dashboard`() {
        page.navigate(url("/sign-in"))

        page.locator("input[name='loginName'], input[type='email']").fill("test@example.com")
        page.locator("button[type='submit']").first().click()
        page.locator("input[name='password'], input[type='password']").fill("Test1234!")
        page.waitForNavigation { page.locator("button[type='submit']").first().click() }

        // Step 1: create organization
        expectThat(page.url()).endsWith("/onboarding/organization")
        page.locator("input[name='name']").fill("Playwright Test Org")
        page.waitForNavigation { page.locator("button[type='submit']").click() }

        // Step 2: plan picker
        expectThat(page.url()).endsWith("/onboarding/plan")
        page.waitForNavigation {
            page.locator("form input[name='plan'][value='STARTER']")
                .evaluate("el => el.closest('form').submit()")
        }

        // Dashboard renders for the new tenant
        expectThat(page.url()).endsWith("/dashboard")
    }

    @Test
    fun `sign-out after OIDC login redirects to root`() {
        // Sign in first
        page.navigate(url("/sign-in"))
        page.locator("input[name='loginName'], input[type='email']").fill("test@example.com")
        page.locator("button[type='submit']").first().click()
        page.locator("input[name='password'], input[type='password']").fill("Test1234!")
        page.waitForNavigation { page.locator("button[type='submit']").first().click() }

        // Sign out via nav dropdown — form POST, not a link
        page.locator("[role='button'].avatar").click()
        page.locator("form[action='/sign-out'] button[type='submit']").click()
        page.waitForNavigation {}

        // OidcClientInitiatedLogoutSuccessHandler → Zitadel end_session → postLogoutRedirectUri (/)
        expectThat(page.url()).endsWith("/")
    }
}
