package com.example.demo.exception;

import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BussinessException.class)
    public ResponseEntity<Map<String,Object>> handleBusinessException(BussinessException exception){
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e){
        String message=e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).findFirst().orElse("Validation failed"); return error(HttpStatus.BAD_REQUEST,message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String,Object>> denied(AccessDeniedException e){return error(HttpStatus.FORBIDDEN,"Access denied");}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleException(Exception exception){return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");}
    private ResponseEntity<Map<String,Object>> error(HttpStatus status,String message){Map<String,Object> body=new LinkedHashMap<>();body.put("timestamp", Instant.now());body.put("status",status.value());body.put("message",message);return ResponseEntity.status(status).body(body);}
}
