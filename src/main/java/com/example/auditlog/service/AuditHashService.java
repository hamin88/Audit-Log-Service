package com.example.auditlog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class AuditHashService {

    private static final Logger log = LoggerFactory.getLogger(AuditHashService.class);

    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] signingKey;

    public AuditHashService(@Value("${audit.hash.secret:change-me-in-production}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("audit.hash.secret must be configured");
        }
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String currentHash(
            UUID eventId,
            Instant timestamp,
            String eventType,
            String actorId,
            String resourceType,
            String resourceId,
            String payload,
            String previousHash
    ) {
        String canonicalInput = canonicalize(
                eventId,
                timestamp,
                eventType,
                actorId,
                resourceType,
                resourceId,
                payload,
                previousHash
        );
        String hash = hmacSha256Hex(canonicalInput);
        log.debug("Computed hash for eventId={} previousHash={} hash={}", eventId, previousHash, hash);
        return hash;
    }

    public String sha256Hex(String input) {
        return hmacSha256Hex(input);
    }

    private String hmacSha256Hex(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(signingKey, HMAC_ALGORITHM);
            mac.init(keySpec);
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA256 is not available in this JVM", exception);
        }
    }

    private String canonicalize(
            UUID eventId,
            Instant timestamp,
            String eventType,
            String actorId,
            String resourceType,
            String resourceId,
            String payload,
            String previousHash
    ) {
        StringBuilder builder = new StringBuilder();
        appendField(builder, "eventId", eventId == null ? "" : eventId.toString());
        appendField(builder, "timestamp", timestamp == null ? "" : timestamp.toString());
        appendField(builder, "eventType", eventType);
        appendField(builder, "actorId", actorId);
        appendField(builder, "resourceType", resourceType);
        appendField(builder, "resourceId", resourceId);
        appendField(builder, "payload", payload);
        appendField(builder, "previousHash", previousHash);
        return builder.toString();
    }

    private void appendField(StringBuilder builder, String fieldName, String value) {
        String normalized = value == null ? "" : value;
        if (builder.length() > 0) {
            builder.append('|');
        }
        builder.append(fieldName)
                .append('=')
                .append(normalized.length())
                .append(':')
                .append(normalized);
    }
}
