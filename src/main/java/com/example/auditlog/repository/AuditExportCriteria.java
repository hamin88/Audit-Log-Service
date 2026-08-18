package com.example.auditlog.repository;

import java.time.Instant;

public record AuditExportCriteria(
        String actorId,
        String resourceId,
        Instant from,
        Instant to
) {
}
