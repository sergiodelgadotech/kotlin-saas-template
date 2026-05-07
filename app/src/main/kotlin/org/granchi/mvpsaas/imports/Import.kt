package org.granchi.mvpsaas.imports

import org.granchi.saasstarter.jobs.JobSchedulerService
import org.granchi.saasstarter.tenant.TenantContext
import org.jobrunr.jobs.annotations.Job
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// ── Entity ────────────────────────────────────────────────────────────────────

@Table("imports")
data class Import(
    @Id val id: UUID = UUID.randomUUID(),
    val organizationId: UUID,
    val filename: String,
    val status: Status = Status.PENDING,
    val rowCount: Int? = null,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    val processedAt: Instant? = null
) {
    enum class Status { PENDING, PROCESSING, COMPLETED, FAILED }
}

// ── Repository ────────────────────────────────────────────────────────────────

interface ImportRepository : CrudRepository<Import, UUID> {
    fun findByOrganizationId(organizationId: UUID): List<Import>
    fun findByOrganizationIdAndId(organizationId: UUID, id: UUID): Import?
}

// ── Service ───────────────────────────────────────────────────────────────────

@Service
@Transactional
class ImportService(
    private val importRepository: ImportRepository,
    private val jobSchedulerService: JobSchedulerService,
    private val importProcessor: ImportProcessor
) {
    /**
     * Creates an import record and enqueues the processing job.
     * Both happen in the same transaction — if enqueueing fails,
     * the import record is not saved either.
     *
     * Jobrunr uses the same DataSource as the app, so the job
     * is saved atomically with the import record.
     */
    fun submit(filename: String): Import {
        val import = importRepository.save(
            Import(
                organizationId = TenantContext.get(),
                filename = filename
            )
        )
        jobSchedulerService.enqueue { importProcessor.process(import.id) }
        return import
    }

    fun list(): List<Import> =
        importRepository.findByOrganizationId(TenantContext.get())

    fun findById(id: UUID): Import? =
        importRepository.findByOrganizationIdAndId(TenantContext.get(), id)
}

// ── Processor (runs async in Jobrunr worker) ──────────────────────────────────

@Service
class ImportProcessor(private val importRepository: ImportRepository) {

    /**
     * This method runs asynchronously in a Jobrunr background worker.
     * TenantContext is already set by TenantJobFilter before this runs.
     *
     * @Job annotation configures retry behaviour.
     */
    @Job(name = "Process import %0", retries = 3)
    @Transactional
    fun process(importId: UUID) {
        val import = importRepository.findById(importId).orElseThrow {
            IllegalStateException("Import $importId not found")
        }

        importRepository.save(import.copy(status = Import.Status.PROCESSING))

        try {
            // TODO: implement actual file processing logic here
            // e.g. read CSV, validate rows, persist data
            val rowCount = processFile(import)

            importRepository.save(
                import.copy(
                    status = Import.Status.COMPLETED,
                    rowCount = rowCount,
                    processedAt = Instant.now()
                )
            )
        } catch (e: Exception) {
            importRepository.save(
                import.copy(
                    status = Import.Status.FAILED,
                    errorMessage = e.message,
                    processedAt = Instant.now()
                )
            )
            throw e  // Re-throw so Jobrunr retries
        }
    }

    private fun processFile(import: Import): Int {
        // TODO: implement file processing
        // Return number of rows processed
        return 0
    }
}
