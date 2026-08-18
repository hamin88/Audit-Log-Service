package com.example.auditlog.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class AuditHashService {

    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

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
        String input = eventId
                + timestamp.toString()
                + eventType
                + actorId
                + resourceType
                + resourceId
                + payload
                + previousHash;
        return sha256(input);
    }

    private String sha256(String input) {
        return sha256Hex(input);
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", exception);
        }
    }
}
