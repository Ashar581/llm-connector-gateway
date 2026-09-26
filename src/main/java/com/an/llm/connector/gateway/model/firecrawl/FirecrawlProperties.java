package com.an.llm.connector.gateway.model.firecrawl;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "firecrawl")
public class FirecrawlProperties {
    private final String apiKey;
    private final String apiUrl;
    private final long timeoutMs;
    private final int maxRetries;
    private final double backoffFactor;
}