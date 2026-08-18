package com.example.auditlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private String actorId;

    @Column(nullable = false, updatable = false)
    private String resourceType;

    @Column(nullable = false, updatable = false)
    private String resourceId;

    @Column(nullable = false, updatable = false, columnDefinition = "clob")
    private String payload;

    @Column(nullable = false, updatable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String currentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEventStatus status = AuditEventStatus.ACTIVE;

    @Column
    private Instant archivedAt;

    protected AuditEvent() {
    }

    public AuditEvent(
            UUID eventId,
            Instant timestamp,
            String eventType,
            String actorId,
            String resourceType,
            String resourceId,
            String payload,
            String previousHash,
            String currentHash
    ) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.previousHash = previousHash;
        this.currentHash = currentHash;
        this.status = AuditEventStatus.ACTIVE;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getPayload() {
        return payload;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public AuditEventStatus getStatus() {
        return status;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void archive(Instant archivedAt) {
        if (status == AuditEventStatus.ARCHIVED) {
            return;
        }
        this.status = AuditEventStatus.ARCHIVED;
        this.archivedAt = archivedAt;
    }
}
