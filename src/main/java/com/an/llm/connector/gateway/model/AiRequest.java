package com.an.llm.connector.gateway.model;

import lombok.Data;

@Data
public class AiRequest {
    private String model;
    private String query;
}
