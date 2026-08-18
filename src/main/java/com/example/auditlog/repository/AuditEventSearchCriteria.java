package com.example.auditlog.repository;

import java.time.Instant;

public record AuditEventSearchCriteria(
        String actorId,
        String resourceType,
        String resourceId,
        String eventType,
        Instant from,
        Instant to
) {
}
