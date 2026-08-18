package com.example.auditlog.api;

import java.time.Instant;

public record AuditExportMetadataResponse(
        Instant exportedAt,
        long totalRecordCount,
        AuditExportFilterResponse filters,
        String exportRootHash,
        String ledgerAnchorPreviousHash,
        String ledgerAnchorCurrentHash,
        boolean subsetLinksToLedger,
        String hashAlgorithm,
        String hashFormula
) {
}
