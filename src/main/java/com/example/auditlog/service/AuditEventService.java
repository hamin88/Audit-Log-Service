package com.example.auditlog.service;

import com.example.auditlog.api.AuditEventRequest;
import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.repository.AuditEventRepository;
import com.example.auditlog.repository.AuditEventSearchCriteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AuditEventService {

    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);
    private static final ReentrantLock APPEND_LOCK = new ReentrantLock();
    private static final AtomicLong LAST_TIMESTAMP_MILLIS = new AtomicLong(System.currentTimeMillis());

    private final AuditEventRepository auditEventRepository;
    private final AuditHashService auditHashService;
    private final ObjectMapper objectMapper;

    public AuditEventService(
            AuditEventRepository auditEventRepository,
            AuditHashService auditHashService,
            ObjectMapper objectMapper
    ) {
        this.auditEventRepository = auditEventRepository;
        this.auditHashService = auditHashService;
        this.objectMapper = objectMapper;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public AuditEvent append(AuditEventRequest request) {
        APPEND_LOCK.lock();
        try {
            UUID eventId = UUID.randomUUID();
            Instant timestamp = nextMonotonicTimestamp();
            String payload = canonicalPayload(request);
            log.info("Preparing append eventId={} eventType={} actorId={} resourceId={} timestamp={}", eventId, request.eventType(), request.actorId(), request.resourceId(), timestamp);

            String previousHash = auditEventRepository.findLatestForUpdate()
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

            log.info("Audit event appended eventId={} previousHash={} currentHash={}", eventId, previousHash, currentHash);
            return persisted;
        } finally {
            APPEND_LOCK.unlock();
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
