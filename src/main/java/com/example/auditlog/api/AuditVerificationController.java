package com.example.auditlog.api;

import com.example.auditlog.service.AuditVerificationResult;
import com.example.auditlog.service.AuditVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
public class AuditVerificationController {

    private static final Logger log = LoggerFactory.getLogger(AuditVerificationController.class);

    private final AuditVerificationService auditVerificationService;

    public AuditVerificationController(AuditVerificationService auditVerificationService) {
        this.auditVerificationService = auditVerificationService;
    }

    @GetMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN','EXPORTER')")
    public AuditVerificationResponse verify() {
        log.info("Running cryptographic audit verification");
        AuditVerificationResult result = auditVerificationService.verify();
        if (!result.valid()) {
            log.warn("Audit verification failed violationType={} brokenAtEventId={} message={}",
                    result.violationType(), result.brokenAtEventId(), result.message());
        } else {
            log.info("Audit verification succeeded checkedRecords={}", result.checkedRecords());
        }
        return new AuditVerificationResponse(
                result.valid(),
                result.brokenAtEventId(),
                result.violationType(),
                result.message()
        );
    }
}
