package com.example.auditlog.service;

public class InvalidAuditExportException extends RuntimeException {

    public InvalidAuditExportException(String message) {
        super(message);
    }
}
