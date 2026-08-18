package com.example.auditlog.service;

import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AuditVerificationService.class);

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
        log.info("Starting full audit-chain verification");
        List<AuditEvent> events = auditEventRepository.findAllChronological();
        String expectedPreviousHash = AuditHashService.GENESIS_HASH;

        for (AuditEvent event : events) {
            if (!expectedPreviousHash.equals(event.getPreviousHash())) {
                log.warn("Tamper detection: previousHash mismatch eventId={} expectedPreviousHash={} actualPreviousHash={}",
                        event.getEventId(), expectedPreviousHash, event.getPreviousHash());
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
                log.warn("Tamper detection: currentHash mismatch eventId={} recalculatedHash={} storedHash={}",
                        event.getEventId(), recalculatedHash, event.getCurrentHash());
                return AuditVerificationResult.broken(
                        event.getEventId(),
                        AuditViolationType.HASH_MISMATCH,
                        "Audit chain is broken at event " + event.getEventId()
                                + ": recalculated SHA-256 hash does not match stored currentHash."
                );
            }

            expectedPreviousHash = event.getCurrentHash();
        }

        log.info("Audit-chain verification complete valid=true checkedRecords={}", events.size());
        return AuditVerificationResult.valid(events.size());
    }
}
