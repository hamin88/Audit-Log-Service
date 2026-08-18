package com.example.auditlog.api;

import java.util.List;

public record AuditExportBundleResponse(
        AuditExportMetadataResponse metadata,
        List<AuditExportRecordResponse> records
) {
}
