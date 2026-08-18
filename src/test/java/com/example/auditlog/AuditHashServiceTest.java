package com.example.auditlog;

import com.example.auditlog.service.AuditHashService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHashServiceTest {

    private final AuditHashService auditHashService = new AuditHashService("audit-log-test-secret");

    @Test
    void currentHash_isStableForCanonicalInputAndPreviousHash() {
        UUID eventId = UUID.fromString("11111111-2222-4333-8444-555566667777");
        Instant timestamp = Instant.parse("2026-08-18T10:15:30Z");
        String payload = "{\"reason\":\"regulatory-review\"}";

        String hash = auditHashService.currentHash(
                eventId,
                timestamp,
                "RECORD_READ",
                "auditor-1",
                "CLIENT_ACCOUNT",
                "acct-100",
                payload,
                AuditHashService.GENESIS_HASH
        );

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(hash).isNotEqualTo(AuditHashService.GENESIS_HASH);

        String differentHash = auditHashService.currentHash(
                eventId,
                timestamp,
                "RECORD_READ",
                "auditor-1",
                "CLIENT_ACCOUNT",
                "acct-100",
                payload,
                "1111111111111111111111111111111111111111111111111111111111111111"
        );

        assertThat(differentHash).isNotEqualTo(hash);
    }

    @Test
    void currentHash_usesCanonicalFieldEncodingAndSecretBinding() {
        UUID eventId = UUID.fromString("22222222-3333-4444-8555-666677778888");
        Instant timestamp = Instant.parse("2026-08-18T10:15:30Z");
        String payload = "{\"reason\":\"regulatory-review\"}";

        AuditHashService serviceWithPrimarySecret = new AuditHashService("audit-log-secret");
        AuditHashService serviceWithDifferentSecret = new AuditHashService("different-audit-log-secret");

        String hashWithPrimarySecret = serviceWithPrimarySecret.currentHash(
                eventId,
                timestamp,
                "RECORD_READ",
                "auditor-1",
                "CLIENT_ACCOUNT",
                "acct-100",
                payload,
                AuditHashService.GENESIS_HASH
        );

        String hashWithDifferentSecret = serviceWithDifferentSecret.currentHash(
                eventId,
                timestamp,
                "RECORD_READ",
                "auditor-1",
                "CLIENT_ACCOUNT",
                "acct-100",
                payload,
                AuditHashService.GENESIS_HASH
        );

        assertThat(hashWithPrimarySecret).isNotEqualTo(hashWithDifferentSecret);
        assertThat(serviceWithPrimarySecret.currentHash(
                eventId,
                timestamp,
                "RECORD_READ",
                "auditor-1",
                "CLIENT_ACCOUNT",
                "acct-100",
                payload,
                AuditHashService.GENESIS_HASH
        )).isEqualTo(hashWithPrimarySecret);
    }
}
