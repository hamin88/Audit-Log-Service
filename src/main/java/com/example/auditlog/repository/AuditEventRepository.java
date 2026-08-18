package com.example.auditlog.repository;

import com.example.auditlog.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuditEventRepository {

    AuditEvent append(AuditEvent auditEvent);

    Optional<AuditEvent> findLatest();

    List<AuditEvent> findAllChronological();

    int archiveOlderThan(Instant cutoff, Instant archivedAt);

    Page<AuditEvent> search(AuditEventSearchCriteria criteria, Pageable pageable);
}
