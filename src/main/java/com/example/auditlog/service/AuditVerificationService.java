package com.example.auditlog.service;

import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditVerificationService {

    private final AuditEventRepository auditEventRepository;
    private final AuditHashService auditHashService;

    public AuditVerificationService(
            AuditEventRepository auditEventRepository,
            AuditHashService auditHashService
    ) {
        this.auditEventRepository = auditEventRepository;
        this.auditHashService = auditHashService;
    }

    @Transactional(readOnly = true)
    public AuditVerificationResult verify() {
        List<AuditEvent> events = auditEventRepository.findAllChronological();
        String expectedPreviousHash = AuditHashService.GENESIS_HASH;

        for (AuditEvent event : events) {
            if (!expectedPreviousHash.equals(event.getPreviousHash())) {
                return AuditVerificationResult.broken(
                        event.getEventId(),
                        AuditViolationType.PREVIOUS_HASH_MISMATCH,
                        "Audit chain is broken at event " + event.getEventId()
                                + ": previousHash does not match the preceding record's currentHash."
                );
            }

            String recalculatedHash = auditHashService.currentHash(
                    event.getEventId(),
                    event.getTimestamp(),
                    event.getEventType(),
                    event.getActorId(),
                    event.getResourceType(),
                    event.getResourceId(),
                    event.getPayload(),
                    event.getPreviousHash()
            );
            if (!recalculatedHash.equals(event.getCurrentHash())) {
                return AuditVerificationResult.broken(
                        event.getEventId(),
                        AuditViolationType.HASH_MISMATCH,
                        "Audit chain is broken at event " + event.getEventId()
                                + ": recalculated SHA-256 hash does not match stored currentHash."
                );
            }

            expectedPreviousHash = event.getCurrentHash();
        }

        return AuditVerificationResult.valid(events.size());
    }
}
