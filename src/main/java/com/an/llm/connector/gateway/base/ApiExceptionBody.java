package com.an.llm.connector.gateway.base;

import lombok.Data;

@Data
public class ApiExceptionBody {
    private Boolean status;
    private String timestamp;
    private Integer code;
    private String path;
    private String message;
}
