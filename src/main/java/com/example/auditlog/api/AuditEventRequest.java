package com.example.auditlog.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuditEventRequest(
        @NotBlank String eventType,
        @NotBlank String actorId,
        @NotBlank String resourceType,
        @NotBlank String resourceId,
        @NotNull JsonNode payload
) {

    @AssertTrue(message = "payload must be a JSON object")
    public boolean isPayloadObject() {
        return payload != null && payload.isObject();
    }
}
