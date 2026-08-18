package com.example.auditlog.api;

import com.example.auditlog.repository.AuditExportCriteria;
import com.example.auditlog.service.AuditExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/audit")
public class AuditExportController {

    private final AuditExportService auditExportService;

    public AuditExportController(AuditExportService auditExportService) {
        this.auditExportService = auditExportService;
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('EXPORTER')")
    public ResponseEntity<AuditExportBundleResponse> export(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        AuditExportBundleResponse bundle = auditExportService.export(
                new AuditExportCriteria(actorId, resourceId, from, to)
        );
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("audit-export-" + Instant.now().toEpochMilli() + ".json")
                        .build()
                        .toString())
                .body(bundle);
    }
}
