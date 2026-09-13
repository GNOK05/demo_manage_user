package com.example.demo.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BussinessException.class)
    public ResponseEntity<Map<String,Object>> handleBusinessException(BussinessException exception){
        String message = exception.getMessage();
        return error(message != null && message.toLowerCase().contains("not found")
            ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST, message);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e){
        String message=e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).findFirst().orElse("Validation failed"); return error(HttpStatus.BAD_REQUEST,message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String,Object>> handleConstraintViolationException(ConstraintViolationException e){
        String message = e.getConstraintViolations().stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).findFirst().orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String,Object>> handleAuthenticationException(AuthenticationException e){
        return error(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String,Object>> denied(AccessDeniedException e){
        return error(HttpStatus.FORBIDDEN, e.getMessage() == null ? "Access denied" : e.getMessage());
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> conflict(DataIntegrityViolationException e){
        return error(HttpStatus.CONFLICT, "Không thể xóa hoặc thay đổi dữ liệu đang được tham chiếu.");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleException(Exception exception){return error(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại.");}
    private ResponseEntity<Map<String,Object>> error(HttpStatus status,String message){Map<String,Object> body=new LinkedHashMap<>();body.put("timestamp", Instant.now());body.put("status",status.value());body.put("message",message);return ResponseEntity.status(status).body(body);}
}
