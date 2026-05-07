package org.granchi.mvpsaas.config

import org.springframework.context.annotation.Configuration
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession

// Stores HTTP sessions in Redis instead of in-memory.
// This allows horizontal scaling without sticky sessions —
// any instance can serve any request.
@Configuration
@EnableRedisHttpSession(
    maxInactiveIntervalInSeconds = 86400  // 24 hours
)
class SessionConfig
