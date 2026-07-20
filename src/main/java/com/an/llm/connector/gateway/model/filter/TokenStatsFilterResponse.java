package com.an.llm.connector.gateway.model.filter;

import com.an.llm.connector.gateway.dto.agent.SystemConsumptionStatsDto;
import lombok.Data;

import java.util.List;

@Data
public class TokenStatsFilterResponse {
    private List<SystemConsumptionStatsDto> stats;
    //total
    private int totalAiRequests;
    private int totalTimeInMs;
    private int totalToken;
    private int totalPromptTokens;
    private int totalCompletionTokens;
    //average
    private double averageTimeInMs;
    private double averageTotalTokens;
    private double averagePromptTokens;
    private double averageCompletionTokens;
    //server
    private String server;
}
