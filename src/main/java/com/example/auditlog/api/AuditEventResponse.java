package com.example.auditlog.api;

import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.domain.AuditEventStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID eventId,
        Instant timestamp,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        JsonNode payload,
        String previousHash,
        String currentHash,
        AuditEventStatus status,
        Instant archivedAt
) {

    public static AuditEventResponse from(AuditEvent event, JsonNode payload) {
        return new AuditEventResponse(
                event.getEventId(),
                event.getTimestamp(),
                event.getEventType(),
                event.getActorId(),
                event.getResourceType(),
                event.getResourceId(),
                payload,
                event.getPreviousHash(),
                event.getCurrentHash(),
                event.getStatus(),
                event.getArchivedAt()
        );
    }
}
