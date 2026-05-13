package com.example.recommend.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/**
 * 全局API异常处理器
 * 统一处理REST API中抛出的异常，返回标准化的错误响应
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 处理ResponseStatusException异常
     * 将Spring的状态码异常转换为统一的JSON错误响应格式
     *
     * @param ex ResponseStatusException异常对象，包含HTTP状态码和错误信息
     * @return ResponseEntity包含标准化的错误响应体，包含时间戳、状态码、错误类型和错误消息
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", ex.getReason() == null ? status.getReasonPhrase() : ex.getReason()
        ));
    }
}
