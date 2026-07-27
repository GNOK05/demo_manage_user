package com.example.demo.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class BaseResponse<T> {
    private T data;
    private String message;
    private Map<String,String> fieldErrors;
}
