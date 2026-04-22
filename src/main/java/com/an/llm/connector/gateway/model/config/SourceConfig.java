package com.an.llm.connector.gateway.model.config;

import lombok.Data;

import java.util.List;

@Data
public class SourceConfig {
    private List<ModelConfig> models;

}