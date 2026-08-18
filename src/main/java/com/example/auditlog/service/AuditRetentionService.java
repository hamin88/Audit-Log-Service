package com.example.auditlog.service;

import com.example.auditlog.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

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
        log.info("Archiving expired audit events cutoff={} retentionPeriod={}", cutoff, retentionProperties.period());
        int archivedCount = auditEventRepository.archiveOlderThan(cutoff, archivedAt);
        log.info("Archived {} expired audit events", archivedCount);
        return archivedCount;
    }
}
