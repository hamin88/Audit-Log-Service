package com.example.auditlog.service;

import com.example.auditlog.repository.AuditEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditRetentionService {

    private final AuditEventRepository auditEventRepository;
    private final AuditRetentionProperties retentionProperties;

    public AuditRetentionService(
            AuditEventRepository auditEventRepository,
            AuditRetentionProperties retentionProperties
    ) {
        this.auditEventRepository = auditEventRepository;
        this.retentionProperties = retentionProperties;
    }

    @Scheduled(cron = "${audit.retention.archive-cron}")
    @Transactional
    public int archiveExpiredEvents() {
        Instant archivedAt = Instant.now();
        Instant cutoff = archivedAt.minus(retentionProperties.period());
        return auditEventRepository.archiveOlderThan(cutoff, archivedAt);
    }
}
