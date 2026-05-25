package com.an.llm.connector.gateway.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class AgentFileDto {
    private Long id;
    private String fileName;
    private String contentType;
    private Map<String, Object> metadata;
//    private byte [] data;
    private AgentConfigurationDto agentConfiguration;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
