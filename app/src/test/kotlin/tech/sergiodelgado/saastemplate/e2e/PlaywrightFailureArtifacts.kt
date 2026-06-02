package tech.sergiodelgado.saastemplate.e2e

import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Tracing
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.Paths

class PlaywrightFailureArtifacts(
    private val outputDir: String = "build/playwright-results"
) : AfterTestExecutionCallback {

    private var context: BrowserContext? = null
    private var page: Page? = null

    fun bind(context: BrowserContext, page: Page) {
        this.context = context
        this.page = page
        context.tracing().start(
            Tracing.StartOptions().setScreenshots(true).setSnapshots(true)
        )
    }

    override fun afterTestExecution(ctx: ExtensionContext) {
        val ctx0 = context ?: return
        val failed = ctx.executionException.isPresent
        if (failed) {
            val safeName = "${ctx.requiredTestClass.simpleName}-${ctx.requiredTestMethod.name}"
                .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val testDir = Paths.get(outputDir, safeName)
            Files.createDirectories(testDir)
            ctx0.tracing().stop(Tracing.StopOptions().setPath(testDir.resolve("trace.zip")))
            page?.screenshot(Page.ScreenshotOptions().setPath(testDir.resolve("failure.png")).setFullPage(true))
        } else {
            ctx0.tracing().stop()
        }
        context = null
        page = null
    }
}
