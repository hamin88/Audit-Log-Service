package com.example.auditlog.service;

import com.example.auditlog.api.AuditExportBundleResponse;
import com.example.auditlog.api.AuditExportFilterResponse;
import com.example.auditlog.api.AuditExportMetadataResponse;
import com.example.auditlog.api.AuditExportRecordResponse;
import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.repository.AuditEventRepository;
import com.example.auditlog.repository.AuditExportCriteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditExportService {

    private static final int EXPORT_PAGE_SIZE = 500;
    private static final String HASH_FORMULA =
            "SHA256(eventId + timestamp + eventType + actorId + resourceType + resourceId + payload + previousHash)";

    private final AuditEventRepository auditEventRepository;
    private final AuditHashService auditHashService;
    private final ObjectMapper objectMapper;

    public AuditExportService(
            AuditEventRepository auditEventRepository,
            AuditHashService auditHashService,
            ObjectMapper objectMapper
    ) {
        this.auditEventRepository = auditEventRepository;
        this.auditHashService = auditHashService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuditExportBundleResponse export(AuditExportCriteria criteria) {
        validate(criteria);

        Instant exportedAt = Instant.now();
        List<AuditExportRecordResponse> records = new ArrayList<>();
        StringBuilder rootHashInput = new StringBuilder();
        String firstPreviousHash = null;
        String lastCurrentHash = null;

        int pageNumber = 0;
        Page<AuditEvent> page;
        do {
            page = auditEventRepository.export(criteria, PageRequest.of(pageNumber, EXPORT_PAGE_SIZE));
            for (AuditEvent event : page.getContent()) {
                if (firstPreviousHash == null) {
                    firstPreviousHash = event.getPreviousHash();
                }
                lastCurrentHash = event.getCurrentHash();
                appendRootHashInput(rootHashInput, event);
                records.add(AuditExportRecordResponse.from(event, parsePayload(event)));
            }
            pageNumber++;
        } while (page.hasNext());

        String exportRootHash = auditHashService.sha256Hex(rootHashInput.toString());
        AuditExportMetadataResponse metadata = new AuditExportMetadataResponse(
                exportedAt,
                page == null ? 0 : page.getTotalElements(),
                new AuditExportFilterResponse(criteria.actorId(), criteria.resourceId(), criteria.from(), criteria.to()),
                exportRootHash,
                firstPreviousHash,
                lastCurrentHash,
                !records.isEmpty(),
                "SHA-256",
                HASH_FORMULA
        );
        return new AuditExportBundleResponse(metadata, records);
    }

    private void validate(AuditExportCriteria criteria) {
        if ((criteria.actorId() == null || criteria.actorId().isBlank())
                && (criteria.resourceId() == null || criteria.resourceId().isBlank())) {
            throw new InvalidAuditExportException("Either actorId or resourceId is required for audit export");
        }
        if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
            throw new InvalidAuditExportException("from timestamp must be before or equal to to timestamp");
        }
    }

    private JsonNode parsePayload(AuditEvent event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored audit payload is not valid JSON", exception);
        }
    }

    private void appendRootHashInput(StringBuilder rootHashInput, AuditEvent event) {
        rootHashInput
                .append(event.getEventId())
                .append(event.getTimestamp())
                .append(event.getEventType())
                .append(event.getActorId())
                .append(event.getResourceType())
                .append(event.getResourceId())
                .append(event.getPayload())
                .append(event.getPreviousHash())
                .append(event.getCurrentHash());
    }
}
