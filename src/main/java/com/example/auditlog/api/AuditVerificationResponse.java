package com.example.auditlog.api;

import com.example.auditlog.service.AuditViolationType;

import java.util.UUID;

public record AuditVerificationResponse(
        boolean isValid,
        UUID brokenAtEventId,
        AuditViolationType violationType,
        String message
) {
}
