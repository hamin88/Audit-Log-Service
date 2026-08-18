package com.example.auditlog.api;

import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.repository.AuditEventSearchCriteria;
import com.example.auditlog.service.AuditEventService;
import com.example.auditlog.service.AuditPayloadRedactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    public AuditEventResponse append(@Valid @RequestBody AuditEventRequest request) {
        return toResponse(auditEventService.append(request));
    }

    @GetMapping
    public Page<AuditEventResponse> search(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
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
    public Page<AuditEventResponse> searchRedacted(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
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
