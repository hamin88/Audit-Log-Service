package com.example.auditlog.support;

import com.example.auditlog.service.InvalidAuditPayloadException;
import com.example.auditlog.service.InvalidAuditExportException;
import com.example.auditlog.service.InvalidAuditQueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        log.warn("Request validation failed: {}", exception.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Invalid audit event");
        problemDetail.setDetail(exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request body validation failed"));
        return problemDetail;
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            InvalidAuditExportException.class,
            InvalidAuditPayloadException.class,
            InvalidAuditQueryException.class
    })
    ProblemDetail handleBadRequest(Exception exception) {
        log.warn("Bad request rejected: {}", exception.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Bad request");
        problemDetail.setDetail(exception.getMessage());
        return problemDetail;
    }
}
