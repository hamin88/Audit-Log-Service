package com.example.auditlog.service;

import com.example.auditlog.api.AuditEventRequest;
import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.domain.LedgerHead;
import com.example.auditlog.repository.AuditEventRepository;
import com.example.auditlog.repository.AuditEventSearchCriteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AuditEventService {

    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);
    private static final AtomicLong LAST_TIMESTAMP_MILLIS = new AtomicLong(System.currentTimeMillis());
    private static final int APPEND_LOCK_RETRIES = 20;

    private final AuditEventRepository auditEventRepository;
    private final AuditHashService auditHashService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AuditEventService(
            AuditEventRepository auditEventRepository,
            AuditHashService auditHashService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.auditEventRepository = auditEventRepository;
        this.auditHashService = auditHashService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    public AuditEvent append(AuditEventRequest request) {
        for (int attempt = 1; attempt <= APPEND_LOCK_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> appendWithinTransaction(request));
            } catch (CannotAcquireLockException exception) {
                if (attempt == APPEND_LOCK_RETRIES) {
                    throw exception;
                }
                sleepBriefly(attempt);
            }
        }
        throw new IllegalStateException("Unable to append audit event after retries");
    }

    private AuditEvent appendWithinTransaction(AuditEventRequest request) {
        UUID eventId = UUID.randomUUID();
        Instant timestamp = nextMonotonicTimestamp();
        String payload = canonicalPayload(request);
        log.info("Preparing append eventId={} eventType={} actorId={} resourceId={} timestamp={}", eventId, request.eventType(), request.actorId(), request.resourceId(), timestamp);

        LedgerHead ledgerHead = auditEventRepository.getLedgerHeadForUpdate();
        String previousHash = ledgerHead.getLatestEventId() == null
                ? AuditHashService.GENESIS_HASH
                : auditEventRepository.findById(ledgerHead.getLatestEventId())
                        .map(AuditEvent::getCurrentHash)
                        .orElse(AuditHashService.GENESIS_HASH);

        String currentHash = auditHashService.currentHash(
                eventId,
                timestamp,
                request.eventType(),
                request.actorId(),
                request.resourceType(),
                request.resourceId(),
                payload,
                previousHash
        );

        AuditEvent event = new AuditEvent(
                eventId,
                timestamp,
                request.eventType(),
                request.actorId(),
                request.resourceType(),
                request.resourceId(),
                payload,
                previousHash,
                currentHash
        );
        AuditEvent persisted = auditEventRepository.append(event);
        ledgerHead.setLatestEventId(persisted.getEventId());
        auditEventRepository.updateLedgerHead(ledgerHead);

        log.info("Audit event appended eventId={} previousHash={} currentHash={}", eventId, previousHash, currentHash);
        return persisted;
    }

    private void sleepBriefly(int attempt) {
        try {
            Thread.sleep(25L * attempt);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying audit append", interruptedException);
        }
    }

    private Instant nextMonotonicTimestamp() {
        long nowMillis = System.currentTimeMillis();
        long next = LAST_TIMESTAMP_MILLIS.updateAndGet(current -> Math.max(current + 1L, nowMillis));
        return Instant.ofEpochMilli(next);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(AuditEventSearchCriteria criteria, Pageable pageable) {
        if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
            throw new InvalidAuditQueryException("from timestamp must be before or equal to to timestamp");
        }
        return auditEventRepository.search(criteria, pageable);
    }

    public String canonicalPayload(AuditEventRequest request) {
        try {
            return objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException exception) {
            throw new InvalidAuditPayloadException("Payload must be a valid JSON object", exception);
        }
    }
}
