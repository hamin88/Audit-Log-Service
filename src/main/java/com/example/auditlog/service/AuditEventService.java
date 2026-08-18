package com.example.auditlog.service;

import com.example.auditlog.api.AuditEventRequest;
import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.repository.AuditEventRepository;
import com.example.auditlog.repository.AuditEventSearchCriteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuditEventService {

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

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AuditEvent append(AuditEventRequest request) {
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String payload = canonicalPayload(request);
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

        return auditEventRepository.append(new AuditEvent(
                eventId,
                timestamp,
                request.eventType(),
                request.actorId(),
                request.resourceType(),
                request.resourceId(),
                payload,
                previousHash,
                currentHash
        ));
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
