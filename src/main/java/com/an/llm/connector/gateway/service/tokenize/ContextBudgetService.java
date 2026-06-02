package com.an.llm.connector.gateway.service.tokenize;

import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.ContextBudget;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContextBudgetService {

    private static final int ALIGNMENT = 256;
    private static final int SAFETY_MARGIN = 128;

    private final LlamaTokenCountService tokenCountService;

    public ContextBudget calculate(ModelConfig config, String llamaBaseUrl, String systemPrompt, String currentUserQuery) {
        int effectiveContext = calculateEffectiveContext(
                config.getContext(),
                config.getParallelExecution()
        );

        int systemTokens = tokenCountService.countTokens(llamaBaseUrl, systemPrompt);
        int currentUserTokens = tokenCountService.countTokens(llamaBaseUrl, currentUserQuery);

        int availableContext = effectiveContext - systemTokens - currentUserTokens - SAFETY_MARGIN;

        if (availableContext <= 0) {
            throw new ApiFallbackException("System prompt and user query exceed available context for selected model.");
        }

        int historyBudget = availableContext / 2;
        int responseReserve = availableContext - historyBudget;

        return ContextBudget.builder()
                .effectiveContext(effectiveContext)
                .systemTokens(systemTokens)
                .currentUserTokens(currentUserTokens)
                .safetyMargin(SAFETY_MARGIN)
                .availableContext(availableContext)
                .historyBudget(historyBudget)
                .responseReserve(responseReserve)
                .build();
    }

    private int calculateEffectiveContext(int totalContext, int parallelExecution) {
        int parallel = Math.max(parallelExecution, 1);
        int raw = totalContext / parallel;
        return (raw / ALIGNMENT) * ALIGNMENT;
    }
}
