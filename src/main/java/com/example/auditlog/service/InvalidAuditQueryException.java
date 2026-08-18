package com.example.auditlog.service;

public class InvalidAuditQueryException extends RuntimeException {

    public InvalidAuditQueryException(String message) {
        super(message);
    }
}
