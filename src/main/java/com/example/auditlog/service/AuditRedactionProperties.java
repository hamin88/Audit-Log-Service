package com.example.auditlog.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "audit.redaction")
public record AuditRedactionProperties(
        List<String> sensitiveKeys,
        String replacement
) {

    public AuditRedactionProperties {
        if (sensitiveKeys == null || sensitiveKeys.isEmpty()) {
            sensitiveKeys = List.of(
                    "password",
                    "passcode",
                    "secret",
                    "token",
                    "authorization",
                    "ssn",
                    "socialSecurityNumber",
                    "creditCard",
                    "creditCardNumber",
                    "cardNumber",
                    "cvv",
                    "pan",
                    "accountNumber",
                    "email",
                    "phone",
                    "dob"
            );
        }
        if (replacement == null || replacement.isBlank()) {
            replacement = "[REDACTED]";
        }
    }
}
