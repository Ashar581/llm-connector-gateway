package com.an.llm.connector.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRequest {
    @NotNull(message = "Agent name is mandatory.")
    private String agent;
    private String model;
    @NotNull(message = "Query is mandatory.")
    private String query;
}
