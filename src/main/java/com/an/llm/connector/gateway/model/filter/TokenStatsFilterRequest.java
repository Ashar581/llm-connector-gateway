package com.an.llm.connector.gateway.model.filter;

import lombok.Data;

import java.time.Instant;

@Data
public class TokenStatsFilterRequest {
    private String agentName;
    private String modelName;
    private String server;
    private Instant startDate;
    private Instant endDate;
}
