package com.an.llm.connector.gateway.service.stats;

import com.an.llm.connector.gateway.dto.AgentConfigurationDto;
import com.an.llm.connector.gateway.entity.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.enums.Source;
import com.an.llm.connector.gateway.exception.NotAllowedException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.AiRequest;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.repository.SystemConsumptionStatsRepo;
import com.an.llm.connector.gateway.service.agent.AgentConfigurationService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConsumptionStatsSvc {
    private final SystemConsumptionStatsRepo systemConsumptionStatsRepo;
    private final AgentConfigurationService agentConfigurationService;

    @Async
    public <T,T1> void add(@NonNull T consumption, @NonNull T1 request, long responseTime){
        switch ((Object) request) {
            case AiRequest aiRequest -> {
                handleAgentRequest(consumption, aiRequest,responseTime);
            }
            case LlmConnectorRequest llmConnectorRequest -> {
                handleAiRequest(consumption,llmConnectorRequest,responseTime);
            }
            default -> throw new NotFoundException("Invalid support type");
        }
    }

    public <T,T1> SystemConsumptionStatsEntity generateStatsEntityWithoutPersisting(@NonNull T consumption, @NonNull T1 request) {
        switch (request) {
            case AiRequest aiRequest -> {
               return generateStatsForAgentRequest(consumption, aiRequest);
            }
            case LlmConnectorRequest llmConnectorRequest -> {
                return generateStatsEntityForAiRequest(consumption,llmConnectorRequest);
            }
            default -> throw new NotFoundException("Invalid support type");
        }
    }

    private <T> void handleAiRequest(@NonNull T consumption, @NonNull LlmConnectorRequest request, long responseTime) {
        //using the agent id we need to fetch the data.
        if (request.getModel()==null || request.getModel().isBlank()) throw new NullException("Model name was not provided. Failed to update the API stats.");
        if (request.getSource()==null || request.getSource().isBlank()) throw new NullException("Source was not provided. Failed to update the API stats.");
        if (request.getType()==null || request.getType().isBlank()) throw new NullException("Type was not provided. Failed to update the API stats.");

        SystemConsumptionStatsEntity stats = new SystemConsumptionStatsEntity();
        stats.setModelName(LlmModels.getFromValue(request.getModel()));
        stats.setSource(Source.getFromValue(request.getSource()));
        stats.setType(LlmCapability.getFromValue(request.getType()));
        stats.setAgentName(request.getAgentName());

        int promptTokens;
        int completionTokens;
        int totalTokens;

        switch (consumption) {
            case ChatResponse chatResponse -> {
                var metadata = chatResponse.getMetadata();
                var usage = metadata.getUsage();

                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
                totalTokens = usage.getTotalTokens();

            }
            case EmbeddingResponse embeddingResponse -> {
                var metadata = embeddingResponse.getMetadata();
                var usage = metadata.getUsage();

                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
                totalTokens = usage.getTotalTokens();
            }
            default -> throw new NotAllowedException("Unhandled consumption class type.");
        }

        stats.setPromptTokens(promptTokens);
        stats.setCompletionTokens(completionTokens);
        stats.setTotalTokens(totalTokens);
        stats.setResponseTimeInMs(responseTime);

        //to be changed for every branch eg -> mac-server, local, vast-ai, rtx-home
        stats.setServer("local");

        log.info("Token Consumption Stats: {}",stats);

        systemConsumptionStatsRepo.save(stats);
    }

    private <T> void handleAgentRequest(@NonNull T consumption, @NonNull AiRequest request, long responseTime) {
        //we have all the data with us such as the model name, source, type, etc
        if (request.getAgent() == null || request.getAgent().isBlank()) throw new NullException("Agent name was not provided. Failed to update the API stats.");
        AgentConfigurationDto agent = agentConfigurationService.get(request.getAgent());

        SystemConsumptionStatsEntity stats = new SystemConsumptionStatsEntity();
        stats.setAgentName(agent.getName());
        stats.setModelName(LlmModels.getFromValue(agent.getModel()));
        stats.setSource(Source.getFromValue(agent.getSource()));
        stats.setType(LlmCapability.getFromValue(agent.getType()));

        int promptTokens;
        int completionTokens;
        int totalTokens;

        switch (consumption) {
            case ChatResponse chatResponse -> {
                var metadata = chatResponse.getMetadata();
                var usage = metadata.getUsage();

                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
                totalTokens = usage.getTotalTokens();

            }
            case EmbeddingResponse embeddingResponse -> {
                var metadata = embeddingResponse.getMetadata();
                var usage = metadata.getUsage();

                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
                totalTokens = usage.getTotalTokens();
            }
            default -> throw new NotAllowedException("Unhandled consumption class type.");
        }

        stats.setPromptTokens(promptTokens);
        stats.setCompletionTokens(completionTokens);
        stats.setTotalTokens(totalTokens);
        stats.setResponseTimeInMs(responseTime);

        //to be changed for every branch eg -> mac-server, local, vast-ai, rtx-home
        stats.setServer("local");

        log.info("Token Consumption Stats: {}",stats);

        systemConsumptionStatsRepo.save(stats);
    }

    private <T> SystemConsumptionStatsEntity generateStatsEntityForAiRequest(@NonNull T consumption, @NonNull LlmConnectorRequest request) {
        //using the agent id we need to fetch the data.
        if (request.getModel()==null || request.getModel().isBlank()) throw new NullException("Model name was not provided. Failed to update the API stats.");
        if (request.getSource()==null || request.getSource().isBlank()) throw new NullException("Source was not provided. Failed to update the API stats.");
        if (request.getType()==null || request.getType().isBlank()) throw new NullException("Type was not provided. Failed to update the API stats.");

        SystemConsumptionStatsEntity stats = new SystemConsumptionStatsEntity();
        stats.setModelName(LlmModels.getFromValue(request.getModel()));
        stats.setSource(Source.getFromValue(request.getSource()));
        stats.setType(LlmCapability.getFromValue(request.getType()));
        stats.setAgentName(request.getAgentName());

        int promptTokens;
        int completionTokens;
        int totalTokens;

        switch (consumption) {
            case ChatResponse chatResponse -> {
                var metadata = chatResponse.getMetadata();
                var usage = metadata.getUsage();

                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
                totalTokens = usage.getTotalTokens();

            }
            case EmbeddingResponse embeddingResponse -> {
                var metadata = embeddingResponse.getMetadata();
                var usage = metadata.getUsage();

                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
                totalTokens = usage.getTotalTokens();
            }
            default -> throw new NotAllowedException("Unhandled consumption class type.");
        }

        stats.setPromptTokens(promptTokens);
        stats.setCompletionTokens(completionTokens);
        stats.setTotalTokens(totalTokens);

        return stats;
    }

    private <T> SystemConsumptionStatsEntity generateStatsForAgentRequest(@NonNull T consumption, @NonNull AiRequest request) {
        //we have all the data with us such as the model name, source, type, etc
        if (request.getAgent() == null || request.getAgent().isBlank()) throw new NullException("Agent name was not provided. Failed to update the API stats.");
        AgentConfigurationDto agent = agentConfigurationService.get(request.getAgent());

        SystemConsumptionStatsEntity stats = new SystemConsumptionStatsEntity();
        stats.setAgentName(agent.getName());
        stats.setModelName(LlmModels.getFromValue(agent.getModel()));
        stats.setSource(Source.getFromValue(agent.getSource()));
        stats.setType(LlmCapability.getFromValue(agent.getType()));

        int promptTokens;
        int completionTokens;
        int totalTokens;

        switch (consumption) {
            case ChatResponse chatResponse -> {
                var metadata = chatResponse.getMetadata();
                var usage = metadata.getUsage();

                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
                totalTokens = usage.getTotalTokens();

            }
            case EmbeddingResponse embeddingResponse -> {
                var metadata = embeddingResponse.getMetadata();
                var usage = metadata.getUsage();

                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getCompletionTokens();
                totalTokens = usage.getTotalTokens();
            }
            default -> throw new NotAllowedException("Unhandled consumption class type.");
        }

        stats.setPromptTokens(promptTokens);
        stats.setCompletionTokens(completionTokens);
        stats.setTotalTokens(totalTokens);

        //to be changed for every branch eg -> mac-server, local, vast-ai, rtx-home
        stats.setServer("local");

        return stats;
    }
}
