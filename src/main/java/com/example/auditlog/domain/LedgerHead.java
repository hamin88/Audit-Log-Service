package com.example.auditlog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/**
 * Singleton entity that tracks the head (latest) event in the audit chain.
 * Used to ensure serialized access to the chain during concurrent appends.
 * The @Version field provides optimistic locking to detect concurrent modifications.
 */
@Entity
@Table(name = "ledger_head")
public class LedgerHead {

    @Id
    private String id = "HEAD";

    private UUID latestEventId;

    @Version
    private Long version;

    public LedgerHead() {
    }

    public LedgerHead(UUID latestEventId) {
        this.latestEventId = latestEventId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getLatestEventId() {
        return latestEventId;
    }

    public void setLatestEventId(UUID latestEventId) {
        this.latestEventId = latestEventId;
    }

}
