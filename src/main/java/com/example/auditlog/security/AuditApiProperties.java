package com.example.auditlog.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "audit.api")
public record AuditApiProperties(
        long maxRequestBytes,
        RateLimit rateLimit
) {
    public record RateLimit(long maxRequests, Duration window) {
    }

    public AuditApiProperties {
        if (maxRequestBytes <= 0) {
            maxRequestBytes = 1_048_576L;
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(60L, Duration.ofSeconds(60));
        } else {
            long maxRequests = rateLimit.maxRequests() <= 0 ? 60L : rateLimit.maxRequests();
            Duration window = rateLimit.window() == null || rateLimit.window().isZero() || rateLimit.window().isNegative()
                    ? Duration.ofSeconds(60)
                    : rateLimit.window();
            rateLimit = new RateLimit(maxRequests, window);
        }
    }
}
