package com.example.demo.exception;

import com.example.demo.common.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BussinessException.class)
    public ResponseEntity<BaseResponse<Object>> handleBusinessException(BussinessException exception, ServletWebRequest servletWebRequest){
        return ResponseEntity.badRequest().body(new BaseResponse<>(null,exception.getMessage(),null));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,ServletWebRequest servletWebRequest){
        Map<String,String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
               fieldErrors.put(error.getField(),error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(new BaseResponse<>(null,"Validation Failed",fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleException(Exception exception, ServletWebRequest servletWebRequest){
        return ResponseEntity.badRequest().body(new BaseResponse<>(null,exception.getMessage(),null));
    }
}
