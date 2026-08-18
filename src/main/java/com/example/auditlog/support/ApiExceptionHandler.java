package com.example.auditlog.support;

import com.example.auditlog.service.InvalidAuditPayloadException;
import com.example.auditlog.service.InvalidAuditQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
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
            InvalidAuditPayloadException.class,
            InvalidAuditQueryException.class
    })
    ProblemDetail handleBadRequest(Exception exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Bad request");
        problemDetail.setDetail(exception.getMessage());
        return problemDetail;
    }
}
