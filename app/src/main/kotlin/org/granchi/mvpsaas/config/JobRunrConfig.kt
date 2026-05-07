package org.granchi.mvpsaas.config

import org.granchi.saasstarter.jobs.TenantJobFilter
import org.jobrunr.configuration.JobRunr
import org.jobrunr.configuration.JobRunrConfiguration
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class JobRunrConfig(
    private val dataSource: DataSource,
    private val tenantJobFilter: TenantJobFilter
) {

    @Bean
    fun jobRunrConfiguration(): JobRunrConfiguration.JobRunrConfigurationResult =
        JobRunr.configure()
            .useStorageProvider(SqlStorageProviderFactory.using(dataSource))
            .useJobFilter(tenantJobFilter)
            .initialize()
}
