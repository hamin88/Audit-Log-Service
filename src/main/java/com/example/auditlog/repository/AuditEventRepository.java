package com.example.auditlog.repository;

import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.domain.LedgerHead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepository {

    AuditEvent append(AuditEvent auditEvent);

    Optional<AuditEvent> findById(UUID eventId);

    Optional<AuditEvent> findLatest();

    Optional<AuditEvent> findLatestForUpdate();

    Optional<AuditEvent> findPreviousEventBefore(Instant cutoff);

    List<AuditEvent> findAllChronological();

    int archiveOlderThan(Instant cutoff, Instant archivedAt);

    Page<AuditEvent> search(AuditEventSearchCriteria criteria, Pageable pageable);

    Page<AuditEvent> export(AuditExportCriteria criteria, Pageable pageable);

    /**
     * Gets the ledger head singleton. The row version is used to detect
     * concurrent updates to the tail record.
     */
    LedgerHead getLedgerHeadForUpdate();

    /**
     * Updates the ledger head with the new latest event ID.
     * Throws OptimisticLockingFailureException if version has changed.
     */
    void updateLedgerHead(LedgerHead ledgerHead);
}
