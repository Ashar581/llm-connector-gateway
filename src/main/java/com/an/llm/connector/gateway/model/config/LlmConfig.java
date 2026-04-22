package com.an.llm.connector.gateway.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    private Map<String, SourceConfig> sources;
}