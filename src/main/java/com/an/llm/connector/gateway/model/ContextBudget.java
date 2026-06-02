package com.an.llm.connector.gateway.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContextBudget {
    private int effectiveContext;
    private int systemTokens;
    private int currentUserTokens;
    private int safetyMargin;
    private int availableContext;
    private int historyBudget;
    private int responseReserve;
}
