package com.example.auditlog.api;

import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.repository.AuditEventSearchCriteria;
import com.example.auditlog.service.AuditEventService;
import com.example.auditlog.service.AuditPayloadRedactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/audit/events")
public class AuditEventController {

    private static final Logger log = LoggerFactory.getLogger(AuditEventController.class);

    private final AuditEventService auditEventService;
    private final AuditPayloadRedactionService redactionService;
    private final ObjectMapper objectMapper;

    public AuditEventController(
            AuditEventService auditEventService,
            AuditPayloadRedactionService redactionService,
            ObjectMapper objectMapper
    ) {
        this.auditEventService = auditEventService;
        this.redactionService = redactionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AuditEventResponse append(@Valid @RequestBody AuditEventRequest request) {
        log.info("Appending audit event eventType={} actorId={} resourceId={}", request.eventType(), request.actorId(), request.resourceId());
        AuditEventResponse response = toResponse(auditEventService.append(request));
        log.debug("Audit event appended eventId={} currentHash={}", response.eventId(), response.currentHash());
        return response;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','READER')")
    public Page<AuditEventResponse> search(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.info("Querying audit events actorId={} resourceType={} resourceId={} eventType={} from={} to={}",
                actorId, resourceType, resourceId, eventType, from, to);
        AuditEventSearchCriteria criteria = new AuditEventSearchCriteria(
                actorId,
                resourceType,
                resourceId,
                eventType,
                from,
                to
        );
        return auditEventService.search(criteria, pageable).map(this::toResponse);
    }

    @GetMapping("/redacted")
    @PreAuthorize("hasAnyRole('ADMIN','READER')")
    public Page<AuditEventResponse> searchRedacted(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.info("Querying redacted audit events actorId={} resourceType={} resourceId={} eventType={} from={} to={}",
                actorId, resourceType, resourceId, eventType, from, to);
        AuditEventSearchCriteria criteria = new AuditEventSearchCriteria(
                actorId,
                resourceType,
                resourceId,
                eventType,
                from,
                to
        );
        return auditEventService.search(criteria, pageable).map(this::toRedactedResponse);
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return AuditEventResponse.from(event, parsePayload(event));
    }

    private AuditEventResponse toRedactedResponse(AuditEvent event) {
        return AuditEventResponse.from(event, redactionService.redact(parsePayload(event)));
    }

    private JsonNode parsePayload(AuditEvent event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored audit payload is not valid JSON", exception);
        }
    }
}
