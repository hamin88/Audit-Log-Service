package com.example.auditlog.api;

import java.time.Instant;

public record AuditExportFilterResponse(
        String actorId,
        String resourceId,
        Instant from,
        Instant to
) {
}
