package com.mobility.infrastructure.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

// infrastructure/health/RedisHealthIndicator.java
@Component
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public Health health() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            return Health.up()
                    .withDetail("ping", pong)
                    .build();
        } catch (Exception e) {
            // Kubernetes readiness probe uses /actuator/health
            // If Redis is down, mark as OUT_OF_SERVICE — not DOWN
            // This keeps pod running in degraded mode rather than restarting
            return Health.outOfService()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
