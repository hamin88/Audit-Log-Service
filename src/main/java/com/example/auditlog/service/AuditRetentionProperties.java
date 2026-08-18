package com.example.auditlog.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "audit.retention")
public record AuditRetentionProperties(
        Duration period,
        String archiveCron
) {

    public AuditRetentionProperties {
        if (period == null) {
            period = Duration.ofDays(365);
        }
        if (archiveCron == null || archiveCron.isBlank()) {
            archiveCron = "0 0 2 * * *";
        }
    }
}
