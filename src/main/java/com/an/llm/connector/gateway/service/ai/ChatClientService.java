package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.ContextBudget;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.service.LlmConfigService;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.tokenize.ContextBudgetService;
import com.an.llm.connector.gateway.service.tokenize.HistoryTokenTrimmer;
import com.an.llm.connector.gateway.util.ChatMessageContextUtils;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatClientService {
    private final AiBeanFactory aiBeanFactory;
    private final ContextBudgetService contextBudgetService;
    private final HistoryTokenTrimmer historyTokenTrimmer;
    private final LlmConfigService llmConfigService;

    public String ask(LlmConnectorRequest request) {
        validateAllowedType(request);

        String instructions = request.getInstructions() != null && !request.getInstructions().isBlank()
                ? request.getInstructions()
                : LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL;

        if (!request.isChatHistoryEnabled()) {
            return aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .system(instructions)
                    .options(buildChatOptions(request))
                    .user(request.getQuery())
                    .call()
                    .content();
        }

        Prompt prompt = buildPromptWithTokenBudget(request, instructions);

        return aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                .prompt(prompt)
                .call()
                .content();
    }

    public Flux<@NonNull String> askStream(LlmConnectorRequest request) {
        validateAllowedType(request);

        String instructions = request.getInstructions() != null && !request.getInstructions().isBlank()
                ? request.getInstructions()
                : LlmInstructions.TEST_CHAT_INSTRUCTION;

        if (!request.isChatHistoryEnabled()) {
            return aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .system(instructions)
                    .options(buildChatOptions(request))
                    .user(request.getQuery())
                    .stream()
                    .content();
        }

        Prompt prompt = buildPromptWithTokenBudget(request, instructions);

        return aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                .prompt(prompt)
                .stream()
                .content();
    }

    private Prompt buildPromptWithTokenBudget(LlmConnectorRequest request, String instructions) {
        SystemMessage systemMessage = ChatMessageContextUtils.buildSystemMessage(instructions);
        UserMessage currentUserMessage = ChatMessageContextUtils.buildCurrentUserMessage(request);
        List<Message> historyMessages = ChatMessageContextUtils.buildHistoryMessages(request);

        ModelConfig modelConfig = llmConfigService.getModelConfig(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        ContextBudget budget = contextBudgetService.calculate(
                modelConfig,
                modelConfig.getBaseUrl(),
                systemMessage.getText(),
                currentUserMessage.getText()
        );

        List<Message> trimmedHistory = historyTokenTrimmer.trimHistory(
                modelConfig.getBaseUrl(),
                historyMessages,
                budget.getHistoryBudget()
        );

        List<Message> finalMessages = ChatMessageContextUtils.merge(
                systemMessage,
                trimmedHistory,
                currentUserMessage
        );

        return new Prompt(finalMessages, buildChatOptions(request, budget));
    }

    private ChatOptions buildChatOptions(LlmConnectorRequest request) {
        ChatOptions.Builder<?> builder = ChatOptions.builder();

        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }

        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }

        return builder.build();
    }

    private ChatOptions buildChatOptions(LlmConnectorRequest request, ContextBudget budget) {
        ChatOptions.Builder<?> builder = ChatOptions.builder();

        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }

        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        } else {
            builder.maxTokens(budget.getResponseReserve());
        }

        return builder.build();
    }

    private void validateAllowedType(LlmConnectorRequest request) {
        LlmCapability type = LlmCapability.getFromValue(request.getType());
        Set<LlmCapability> notAllowedTypes = Set.of(
                LlmCapability.CLASSIFICATION,
                LlmCapability.EMBEDDING,
                LlmCapability.VISION
        );

        if (notAllowedTypes.contains(type)) {
            throw new ApiFallbackException("The requested type is not supported by this endpoint.");
        }
    }
}
