package com.an.llm.connector.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemConsumptionStatsDto {
    private Long id;
    private String modelName;
    private String agentName;
    private String type;
    private String source;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long responseTimeInMs;
    private String server;
    private Instant createdAt;
}
