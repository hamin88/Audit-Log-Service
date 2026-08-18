package com.example.auditlog.api;

import com.example.auditlog.service.AuditVerificationResult;
import com.example.auditlog.service.AuditVerificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
public class AuditVerificationController {

    private final AuditVerificationService auditVerificationService;

    public AuditVerificationController(AuditVerificationService auditVerificationService) {
        this.auditVerificationService = auditVerificationService;
    }

    @GetMapping("/verify")
    public AuditVerificationResponse verify() {
        AuditVerificationResult result = auditVerificationService.verify();
        return new AuditVerificationResponse(
                result.valid(),
                result.brokenAtEventId(),
                result.violationType(),
                result.message()
        );
    }
}
