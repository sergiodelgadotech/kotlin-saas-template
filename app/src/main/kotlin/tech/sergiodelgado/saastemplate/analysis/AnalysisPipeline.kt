package tech.sergiodelgado.saastemplate.analysis

import tech.sergiodelgado.saasstarter.tenant.TenantContext
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.readCSV
import org.jobrunr.jobs.annotations.Job
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import smile.regression.OLS
import smile.data.formula.Formula
import java.io.InputStream
import java.time.Instant
import java.util.UUID

// ── Result entity ─────────────────────────────────────────────────────────────

@Table("analysis_results")
data class AnalysisResult(
    @Id val id: UUID = UUID.randomUUID(),
    val organizationId: UUID,
    val runId: UUID,                   // groups all results from one run
    val client: String,
    val article: String,
    @Column("time_window") val window: Window,
    val slope: Double,
    val intercept: Double,
    val rSquared: Double,
    val sampleSize: Int,
    val hasPositiveSlopeWarning: Boolean,
    val createdAt: Instant = Instant.now()
) {
    enum class Window { LAST_PRICE_INCREASE, LAST_3_MONTHS, LAST_6_MONTHS }
}

interface AnalysisResultRepository : CrudRepository<AnalysisResult, UUID> {
    fun findByOrganizationIdAndRunId(organizationId: UUID, runId: UUID): List<AnalysisResult>
    fun findByOrganizationId(organizationId: UUID): List<AnalysisResult>
}

// ── Pipeline ──────────────────────────────────────────────────────────────────

/**
 * Full analysis pipeline:
 * 1. Read CSV with Kotlin DataFrame
 * 2. Validate and aggregate by (client, article, period)
 * 3. Run OLS with Smile for each window
 * 4. Persist results with full traceability
 *
 * Runs asynchronously via Jobrunr — see ImportProcessor for how to enqueue.
 */
@Service
class AnalysisPipeline(
    private val resultRepository: AnalysisResultRepository
) {

    @Job(name = "OLS analysis run %0", retries = 2)
    @Transactional
    fun run(importId: UUID, csvStream: InputStream) {
        val runId = UUID.randomUUID()
        val orgId = TenantContext.get()

        // ── Step 1: Load and validate ──────────────────────────────────────
        val raw = DataFrame.readCSV(csvStream)
            .rename("client" to "client", "article" to "article",
                    "period" to "period", "price" to "price")
            .filter { "price"<Double>() > 0.0 }   // basic quality filter
            .dropNulls()

        // ── Step 2: Aggregate by (client, article, period) ─────────────────
        val aggregated = raw
            .groupBy("client", "article", "period")
            .aggregate { mean("price") into "avg_price" }
            .sortBy("client", "article", "period")

        // ── Step 3: OLS per (client, article) for each window ─────────────
        val results = mutableListOf<AnalysisResult>()

        aggregated
            .groupBy("client", "article")
            .forEach { (key, group) ->
                val client  = key["client"] as String
                val article = key["article"] as String

                for (window in AnalysisResult.Window.entries) {
                    val windowed = applyWindow(group, window)
                    if (windowed.rowsCount() < MIN_SAMPLE_SIZE) continue

                    val ols = fitOls(windowed) ?: continue

                    results += AnalysisResult(
                        organizationId           = orgId,
                        runId                    = runId,
                        client                   = client,
                        article                  = article,
                        window                   = window,
                        slope                    = ols.coefficients()[1],
                        intercept                = ols.coefficients()[0],
                        rSquared                 = ols.RSquared(),
                        sampleSize               = windowed.rowsCount(),
                        hasPositiveSlopeWarning  = ols.coefficients()[1] > 0
                    )
                }
            }

        resultRepository.saveAll(results)
    }

    // ── Window logic ──────────────────────────────────────────────────────────

    private fun applyWindow(
        df: DataFrame<*>,
        window: AnalysisResult.Window
    ): DataFrame<*> = when (window) {
        AnalysisResult.Window.LAST_3_MONTHS -> df.takeLast(3)
        AnalysisResult.Window.LAST_6_MONTHS -> df.takeLast(6)
        AnalysisResult.Window.LAST_PRICE_INCREASE -> {
            // Find the last row where price went up and take everything after
            val prices = df["avg_price"].toList().map { it as Double }
            val lastIncreaseIdx = prices.indices
                .reversed()
                .drop(1)
                .firstOrNull { i -> prices[i + 1] > prices[i] }
                ?: return df
            df.drop(lastIncreaseIdx)
        }
    }

    // ── OLS fit ───────────────────────────────────────────────────────────────

    private fun fitOls(df: DataFrame<*>): smile.regression.LinearModel? = try {
        // X = time index (0, 1, 2, ...), Y = avg_price
        val n = df.rowsCount()
        val xData = DoubleArray(n) { i -> i.toDouble() }
        val y = df["avg_price"].toList().map { it as Double }.toDoubleArray()

        val smileDF = smile.data.DataFrame(
            smile.data.vector.DoubleVector("period", xData),
            smile.data.vector.DoubleVector("avg_price", y)
        )

        OLS.fit(Formula.lhs("avg_price"), smileDF)
    } catch (e: Exception) {
        null  // Not enough variance or singular matrix — skip this window
    }

    companion object {
        const val MIN_SAMPLE_SIZE = 30  // as per SOW requirement
    }
}

// ── Service facade ────────────────────────────────────────────────────────────

@Service
class AnalysisService(private val resultRepository: AnalysisResultRepository) {

    fun latestResults(): List<AnalysisResult> =
        resultRepository.findByOrganizationId(TenantContext.get())

    fun resultsForRun(runId: UUID): List<AnalysisResult> =
        resultRepository.findByOrganizationIdAndRunId(TenantContext.get(), runId)

    fun warnings(runId: UUID): List<AnalysisResult> =
        resultsForRun(runId).filter { it.hasPositiveSlopeWarning }
}
