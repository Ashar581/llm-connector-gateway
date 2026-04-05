package com.an.llm.connector.gateway.base;

import lombok.Data;

@Data
public class ApiResponseBody <T> {
    private Boolean status;
    private Integer code;
    private String message;
    private T data;
}
