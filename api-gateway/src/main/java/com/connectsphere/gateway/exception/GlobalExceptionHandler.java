package com.connectsphere.gateway.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, Object>> handleWebInput(ServerWebInputException ex) {
        return buildError(ex.getStatusCode(), ex.getReason() != null ? ex.getReason() : "Invalid request payload");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "Request failed";
        return buildError(ex.getStatusCode(), reason);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return buildError(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected gateway error");
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatusCode statusCode, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", statusCode.value());
        body.put("error", statusCode.toString());
        body.put("message", message);
        return ResponseEntity.status(statusCode).body(body);
    }
}
