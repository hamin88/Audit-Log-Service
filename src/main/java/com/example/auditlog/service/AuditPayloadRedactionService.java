package com.example.auditlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditPayloadRedactionService {

    private static final Logger log = LoggerFactory.getLogger(AuditPayloadRedactionService.class);

    private final Set<String> sensitiveKeys;
    private final String replacement;

    public AuditPayloadRedactionService(AuditRedactionProperties redactionProperties) {
        this.sensitiveKeys = redactionProperties.sensitiveKeys().stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.replacement = redactionProperties.replacement();
    }

    public JsonNode redact(JsonNode payload) {
        JsonNode copy = payload.deepCopy();
        int redactedFields = redactNode(copy);
        if (redactedFields > 0) {
            log.debug("Redacted {} sensitive payload field(s)", redactedFields);
        }
        return copy;
    }

    private int redactNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            int redactedCount = 0;
            for (var entry : objectNode.properties()) {
                if (sensitiveKeys.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    objectNode.put(entry.getKey(), replacement);
                    redactedCount++;
                } else {
                    redactedCount += redactNode(entry.getValue());
                }
            }
            return redactedCount;
        }

        if (node instanceof ArrayNode arrayNode) {
            int redactedCount = 0;
            for (JsonNode child : arrayNode) {
                redactedCount += redactNode(child);
            }
            return redactedCount;
        }

        return 0;
    }
}
