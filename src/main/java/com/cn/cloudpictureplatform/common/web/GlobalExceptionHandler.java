package com.cn.cloudpictureplatform.common.web;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.SocketException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        ApiErrorCode errorCode = exception.getErrorCode();
        HttpStatus status = toStatus(errorCode);
        return ResponseEntity.status(status)
                .body(ApiResponse.error(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(SocketException.class)
    public ResponseEntity<ApiResponse<Void>> handleSocketException(SocketException exception) {
        String message = exception.getMessage();
        if ("Connection reset".equals(message) ||
            "Connection reset by peer".equals(message) ||
            "Broken pipe".equals(message)) {
            log.warn("客户端连接中断 (可能意外关闭): {}", message);
        } else {
            log.error("Socket 异常：{}", message, exception);
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(ApiErrorCode.SERVER_ERROR, "连接中断"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("未处理的异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ApiErrorCode.SERVER_ERROR, "unexpected error"));
    }

    private HttpStatus toStatus(ApiErrorCode errorCode) {
        return switch (errorCode) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
