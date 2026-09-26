package com.an.llm.connector.gateway.config;

import com.an.llm.connector.gateway.model.firecrawl.FirecrawlProperties;
import com.firecrawl.client.FirecrawlClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FirecrawlProperties.class)
@RequiredArgsConstructor
public class FirecrawlConfiguration {
    private final FirecrawlProperties properties;

    @Bean
    public FirecrawlClient firecrawlClient() {

        return FirecrawlClient.builder()
                .apiKey(properties.getApiKey())
                .apiUrl(properties.getApiUrl())
                .timeoutMs(properties.getTimeoutMs())
                .maxRetries(properties.getMaxRetries())
                .backoffFactor(properties.getBackoffFactor())
                .build();
    }
}