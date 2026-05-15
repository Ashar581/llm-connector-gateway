package com.an.llm.connector.gateway.model.config;

import lombok.Data;

import java.util.Set;

@Data
public class ModelConfig {
    private String id;
    private Set<String> type;
    private String baseUrl;
    private String apiPath;
    private String apiKey;
    private Integer port;
    private String provider;
    private String modelName;
    private String mmProj;
    private Integer context;
    private Integer parallelExecution;
    private Boolean active;
}
