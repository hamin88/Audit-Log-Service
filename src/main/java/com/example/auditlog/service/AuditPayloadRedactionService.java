package com.example.auditlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditPayloadRedactionService {

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
        redactNode(copy);
        return copy;
    }

    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.properties().forEach(entry -> {
                if (sensitiveKeys.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    objectNode.put(entry.getKey(), replacement);
                } else {
                    redactNode(entry.getValue());
                }
            });
            return;
        }

        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redactNode);
        }
    }
}
