package com.example.auditlog.service;

public class InvalidAuditPayloadException extends RuntimeException {

    public InvalidAuditPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
