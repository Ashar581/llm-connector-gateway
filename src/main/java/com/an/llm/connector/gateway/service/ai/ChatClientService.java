package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.Source;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.OperationFailedException;
import com.an.llm.connector.gateway.model.ContextBudget;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.service.LlmConfigService;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import com.an.llm.connector.gateway.service.tokenize.ContextBudgetService;
import com.an.llm.connector.gateway.service.tokenize.HistoryTokenTrimmer;
import com.an.llm.connector.gateway.service.web.WebSearchService;
import com.an.llm.connector.gateway.service.web.tool.WebSearchPaidTool;
import com.an.llm.connector.gateway.service.web.tool.WebSearchTool;
import com.an.llm.connector.gateway.util.ChatMessageContextUtils;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatClientService {
    private final AiBeanFactory aiBeanFactory;
    private final ContextBudgetService contextBudgetService;
    private final HistoryTokenTrimmer historyTokenTrimmer;
    private final LlmConfigService llmConfigService;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;
    private final WebSearchService webSearchService;

    public String ask(LlmConnectorRequest request) {
        validateAllowedType(request);

        switch (Source.getFromValue(request.getSource())) {
            case FREE -> {
                return askFree(request);
            }
            case PAID -> {
                return askPaid(request);
            }
            default -> throw new OperationFailedException("Unable to determine the request Source.");
        }
    }

    public Flux<@NonNull String> askStream(LlmConnectorRequest request) {
        validateAllowedType(request);

        switch (Source.getFromValue(request.getSource())) {
            case FREE -> {
                return askFreeStream(request);
            }
            case PAID -> {
                return askPaidStream(request);
            }
            default -> throw new OperationFailedException("Unable to determine the request Source");
        }
    }

    private String askFree(LlmConnectorRequest request) {
        validateAllowedType(request);

        String instructions = request.getInstructions() != null && !request.getInstructions().isBlank()
                ? request.getInstructions()
                : LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL;

        if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
            instructions = instructions + "\n" + LlmInstructions.DEFAULT_WEB_INSTRUCTIONS;
        }

        ChatResponse response;

        long start = System.currentTimeMillis();

        if (!request.isChatHistoryEnabled()) {
            var prompt = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .system(instructions)
                    .options(buildChatOptions(request))
                    .user(request.getQuery());

            //newly added for web
            if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
                request.setEnablePrivateMode(false);
                prompt.tools(new WebSearchTool(webSearchService,request));
            }

            response = prompt.call()
                    .chatResponse();
        } else {

            Prompt prompt = buildPromptWithTokenBudget(request, instructions);

            //newly added for web
            var chatPrompt = aiBeanFactory
                    .getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt(prompt)
                    .options(buildChatOptions(request));

            if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
                request.setEnablePrivateMode(false);
                chatPrompt.tools(new WebSearchTool(webSearchService, request));
            }

            response = chatPrompt
                    .call()
                    .chatResponse();
        }

        long completionTimeMs = System.currentTimeMillis() - start;

        assert response != null;
        //async service for generating the stats.
        try {
            systemConsumptionStatsSvc.add(response, request, completionTimeMs);
        } catch (Exception e){
            log.error("Error recording non-stream consumption tokens stats.",e);
        }

        return Objects.requireNonNull(response.getResult()).getOutput().getText();
    }

    private String askPaid(LlmConnectorRequest request) {
        validateAllowedType(request);

        String provider = llmConfigService.getProvider(request.getSource(),request.getType(),request.getModel());

        return switch (provider) {
            case "openai" -> askOpenAi(request);
            case "anthropic" -> askAnthropic(request);
            default -> throw new NotFoundException("Provider type not supported");
        };
    }

    private Flux<@NonNull String> askFreeStream(LlmConnectorRequest request) {
        validateAllowedType(request);

        String instructions = request.getInstructions() != null
                && !request.getInstructions().isBlank()
                ? request.getInstructions()
                : LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL;

        if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
            instructions = instructions + "\n" + LlmInstructions.DEFAULT_WEB_INSTRUCTIONS;
        }

        long start = System.currentTimeMillis();

        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();

        Flux<@NonNull ChatResponse> responseFlux;

        if (!request.isChatHistoryEnabled()) {
            //newly added for web
            var prompt = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .system(instructions)
                    .options(buildChatOptions(request))
                    .user(request.getQuery());

            if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
                request.setEnablePrivateMode(false);
                prompt.tools(new WebSearchPaidTool(webSearchService,request));
            }

            responseFlux = prompt.stream()
                    .chatResponse();
        }
        else {
            Prompt prompt = buildPromptWithTokenBudget(request, instructions);

            //newly added for web
            var chatPrompt = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt(prompt);

            if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
                request.setEnablePrivateMode(false);
                chatPrompt.tools(new WebSearchPaidTool(webSearchService,request));
            }

            responseFlux = chatPrompt.stream()
                    .chatResponse();
        }

        return responseFlux
                .doOnNext(lastResponse::set)
                .map(chatResponse -> {
                    Generation generation = chatResponse.getResult();

                    if (generation == null || generation.getOutput().getText() == null) {
                        return "";
                    }

                    return generation.getOutput().getText();
                })
                .doOnComplete(() -> {
                    ChatResponse streamEnd = lastResponse.get();

                    if (streamEnd == null) {
                        return;
                    }

                    long completionTimeMs = System.currentTimeMillis() - start;

                    try {
                        systemConsumptionStatsSvc.add(streamEnd, request, completionTimeMs);
                    } catch (Exception e) {
                        log.error("Failed to record stream consumption stats", e);
                    }
                });
    }

    public Flux<@NonNull String> askPaidStream(@NonNull LlmConnectorRequest request) {
        validateAllowedType(request);

        String provider = llmConfigService.getProvider(request.getSource(),request.getType(),request.getModel());

        return switch (provider) {
            case "openai" -> askOpenAiStream(request);
            case "anthropic" -> askAnthropicStream(request);
            default -> throw new NotFoundException("Provider type not supported");
        };
    }

    private String askOpenAi(@NonNull LlmConnectorRequest request) {
        String instructions = request.getInstructions() != null && !request.getInstructions().isBlank()
                ? request.getInstructions()
                : LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL;

        if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
            instructions = instructions + "\n" + LlmInstructions.DEFAULT_WEB_INSTRUCTIONS;
        }

        ChatResponse response;

        long start = System.currentTimeMillis();

        if (!request.isChatHistoryEnabled()) {
            var prompt = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .system(instructions)
                    .options(buildChatOptions(request))
                    .user(request.getQuery());

            if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
                request.setEnablePrivateMode(false);
                prompt.tools(new WebSearchPaidTool(webSearchService,request));
            }

            response = prompt.call()
                    .chatResponse();
        } else {
            var chatPrompt = aiBeanFactory
                    .getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .system(instructions)
                    .options(buildChatOptions(request));

            // Add existing conversation history directly.
            if (request.getChatHistory() != null && !request.getChatHistory().isEmpty()) {
                chatPrompt.messages(ChatMessageContextUtils.buildHistoryMessages(request));
            }

            // Add the current user query.
            chatPrompt.user(request.getQuery());

            // Web search
            if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
                request.setEnablePrivateMode(false);
                chatPrompt.tools(new WebSearchTool(webSearchService, request));
            }

            response = chatPrompt
                    .call()
                    .chatResponse();
        }

        long completionTimeMs = System.currentTimeMillis() - start;

        assert response != null;
        //async service for generating the stats.
        try {
            systemConsumptionStatsSvc.add(response, request, completionTimeMs);
        } catch (Exception e){
            log.error("Error recording non-stream consumption tokens stats.",e);
        }

        return Objects.requireNonNull(response.getResult()).getOutput().getText();
    }

    private String askAnthropic(@NonNull LlmConnectorRequest request) {
        //implementation yet to be added.
        return "";
    }

    //for paid (using it in switch via provider) - stream only
    private Flux<@NonNull String> askOpenAiStream(@NonNull LlmConnectorRequest request){

        String instructions = request.getInstructions() != null
                && !request.getInstructions().isBlank()
                ? request.getInstructions()
                : LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL;

        if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
            instructions = instructions + "\n" + LlmInstructions.DEFAULT_WEB_INSTRUCTIONS;
        }

        long start = System.currentTimeMillis();

        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();

        Flux<@NonNull ChatResponse> responseFlux;

        if (!request.isChatHistoryEnabled()) {
            //newly added for web
            var prompt = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .system(instructions)
                    .options(buildChatOptions(request))
                    .user(request.getQuery());

            if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
                request.setEnablePrivateMode(false);
                prompt.tools(new WebSearchPaidTool(webSearchService,request));
            }

            responseFlux = prompt.stream()
                    .chatResponse();
        } else {
            var chatPrompt = aiBeanFactory
                    .getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .system(instructions)
                    .options(buildChatOptions(request));

            if (request.getChatHistory() != null && !request.getChatHistory().isEmpty()) {
                chatPrompt.messages(ChatMessageContextUtils.buildHistoryMessages(request));
            }

            chatPrompt.user(request.getQuery());

            if (request.getType().equalsIgnoreCase(LlmCapability.WEB.getValue())) {
                request.setEnablePrivateMode(false);
                chatPrompt.tools(new WebSearchPaidTool(webSearchService, request));
            }

            responseFlux = chatPrompt
                    .stream()
                    .chatResponse();
        }

        return responseFlux
                .doOnNext(lastResponse::set)
                .map(chatResponse -> {
                    Generation generation = chatResponse.getResult();

                    if (generation == null || generation.getOutput().getText() == null) {
                        return "";
                    }

                    return generation.getOutput().getText();
                })
                .doOnComplete(() -> {
                    ChatResponse streamEnd = lastResponse.get();

                    if (streamEnd == null) {
                        return;
                    }

                    long completionTimeMs = System.currentTimeMillis() - start;

                    try {
                        systemConsumptionStatsSvc.add(streamEnd, request, completionTimeMs);
                    } catch (Exception e) {
                        log.error("Failed to record stream consumption stats", e);
                    }
                });
    }

    //for paid (using it in switch via provider) - stream only
    private Flux<@NonNull String> askAnthropicStream(@NonNull LlmConnectorRequest request) {
        //Anthropic implementation yet to be added.
        return Flux.just("");
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
        OpenAiChatOptions.Builder openAiOptions = OpenAiChatOptions.builder()
                .model(request.getModel())
                .streamUsage(true);

        if (request.getTemperature() != null) {
            openAiOptions.temperature(request.getTemperature());
        }

        if (request.getMaxTokens() != null) {
            openAiOptions.maxTokens(request.getMaxTokens());
        }
        return openAiOptions.build();
    }

    private ChatOptions buildChatOptions(LlmConnectorRequest request, ContextBudget budget) {
        OpenAiChatOptions.Builder openAiOptions = OpenAiChatOptions.builder()
                .model(request.getModel())
                .streamUsage(true);

        if (request.getTemperature() != null) {
            openAiOptions.temperature(request.getTemperature());
        }

        if (request.getMaxTokens() != null) {
            openAiOptions.maxTokens(request.getMaxTokens());
        } else {
            openAiOptions.maxTokens(budget.getResponseReserve());
        }

        return openAiOptions.build();
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

        Set<LlmCapability> allowedHistoryTypes = Set.of(
                LlmCapability.CHAT,
                LlmCapability.AGENT,
                LlmCapability.RAG,
                LlmCapability.CODE,
                LlmCapability.WEB
        );

        if (request.isChatHistoryEnabled() && !allowedHistoryTypes.contains(type)) {
            throw new ApiFallbackException("Chat history is not available for "+type.getValue()+" type.");
        }
    }
}
