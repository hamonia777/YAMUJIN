package com.example.the_greatest_project.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Every API failure comes back as JSON with a human-readable Korean message. */
@RestControllerAdvice(basePackages = "com.example.the_greatest_project.web")
public class ApiErrors {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage() == null ? f.getField() : f.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(", "));
        return body(HttpStatus.BAD_REQUEST, msg.isBlank() ? "입력값을 확인하세요" : msg);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException e) {
        HttpStatus s = HttpStatus.resolve(e.getStatusCode().value());
        return body(s == null ? HttpStatus.INTERNAL_SERVER_ERROR : s,
                e.getReason() == null ? "요청을 처리할 수 없습니다" : e.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception e) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "서버에서 문제가 발생했습니다: " + e.getClass().getSimpleName());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        m.put("status", status.value());
        m.put("at", Instant.now());
        return ResponseEntity.status(status).body(m);
    }
}
