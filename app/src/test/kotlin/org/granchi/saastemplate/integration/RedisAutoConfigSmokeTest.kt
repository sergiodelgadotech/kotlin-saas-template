package org.granchi.saastemplate.integration

import org.granchi.saastemplate.SaasTemplateApplication
import org.granchi.saasstarter.autoconfigure.RedisAutoConfiguration
import org.granchi.saasstarter.autoconfigure.SaasStarterProperties
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsKey
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.time.Duration

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@ActiveProfiles("test")
class RedisAutoConfigSmokeTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired private lateinit var context: ApplicationContext
    @Autowired private lateinit var properties: SaasStarterProperties

    @Test
    fun `RedisAutoConfiguration is discovered and active`() {
        expectThat(context.getBeansOfType(RedisAutoConfiguration::class.java)).hasSize(1)
    }

    @Test
    fun `RedisTemplate and RedisCacheManager beans are wired`() {
        expectThat(context.getBeansOfType(RedisTemplate::class.java)).containsKey("jsonRedisTemplate")
        expectThat(context.getBeansOfType(RedisCacheManager::class.java).size).isEqualTo(1)
    }

    @Test
    fun `cache properties bind from application yml with the expected TTLs`() {
        val configurations = properties.cache.configurations
        expectThat(configurations).containsKey("tenant-by-user")
        expectThat(configurations["tenant-by-user"]!!.ttl).isEqualTo(Duration.ofMinutes(5))
        expectThat(configurations["organization"]!!.ttl).isEqualTo(Duration.ofMinutes(30))
        expectThat(configurations["subscription"]!!.ttl).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun `cache manager exposes the named caches`() {
        val cacheManager = context.getBean(RedisCacheManager::class.java)
        expectThat(cacheManager.cacheNames).contains("tenant-by-user", "organization", "subscription")
    }
}
