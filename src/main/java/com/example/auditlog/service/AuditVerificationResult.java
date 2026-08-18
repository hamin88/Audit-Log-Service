package com.example.auditlog.service;

import java.util.UUID;

public record AuditVerificationResult(
        boolean valid,
        UUID brokenAtEventId,
        AuditViolationType violationType,
        String message
) {

    public static AuditVerificationResult valid(int checkedRecords) {
        return new AuditVerificationResult(
                true,
                null,
                AuditViolationType.NONE,
                "Audit hash chain is valid across " + checkedRecords + " record(s)."
        );
    }

    public static AuditVerificationResult broken(
            UUID brokenAtEventId,
            AuditViolationType violationType,
            String message
    ) {
        return new AuditVerificationResult(false, brokenAtEventId, violationType, message);
    }
}
