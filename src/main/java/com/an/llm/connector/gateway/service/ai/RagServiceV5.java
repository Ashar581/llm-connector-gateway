package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.enums.IngestionMode;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.exception.NotAllowedException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.exception.OperationFailedException;
import com.an.llm.connector.gateway.model.ContextBudget;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.service.LlmConfigService;
import com.an.llm.connector.gateway.service.ai.tools.RagTool;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.factory.VectorStoreBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import com.an.llm.connector.gateway.service.tokenize.ContextBudgetService;
import com.an.llm.connector.gateway.service.tokenize.HistoryTokenTrimmer;
import com.an.llm.connector.gateway.service.web.WebSearchService;
import com.an.llm.connector.gateway.service.web.tool.WebSearchTool;
import com.an.llm.connector.gateway.util.ChatMessageContextUtils;
import com.an.llm.connector.gateway.util.LlmInstructions;
import com.knuddels.jtokkit.api.EncodingType;
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
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceV5 {
    private final VectorStoreBeanFactory vectorStoreBeanFactory;
    private final DocumentIngestionServiceV2 documentIngestionServiceV2;
    private final RetrievalServiceV2 retrievalServiceV2;
    private final ConfidenceService confidenceService;
    private final AiBeanFactory aiBeanFactory;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;
    private final LlmConfigService llmConfigService;
    private final ContextBudgetService contextBudgetService;
    private final HistoryTokenTrimmer historyTokenTrimmer;
    private final WebSearchService webSearchService;

    public String chat(@NonNull LlmConnectorRequest request, IngestionMode mode) {
        prepareRequest(request, mode);

        String instructions = request.getInstructions() == null || request.getInstructions().isBlank()
                ? LlmInstructions.DEFAULT_RAG_INSTRUCTION : request.getInstructions();

        request.setInstructions(instructions);

        String userMessage = """
            QUESTION:
            %s

            Use the available tools when additional internal-document
            or web information is required.
            """.formatted(request.getQuery());

        Prompt prompt = buildPromptWithTokenBudget(request, userMessage);

        long start = System.currentTimeMillis();

        ChatResponse response = aiBeanFactory
                .getChatClient(
                        request.getSource(),
                        request.getType(),
                        request.getModel()
                )
                .prompt(prompt)
                .tools(
                        new RagTool(this, request),
                        new WebSearchTool(webSearchService, request)
                )
                .call()
                .chatResponse();

        long completionTimeMs = System.currentTimeMillis() - start;

        if (response == null || response.getResult() == null) {
            throw new OperationFailedException("The LLM returned an empty response.");
        } else {
            response.getResult();
        }

        try {
            systemConsumptionStatsSvc.add(response, request, completionTimeMs);
        } catch (Exception e) {
            log.error("Error recording non-stream consumption stats.", e);
        }

        String answer = response.getResult().getOutput().getText();

        if (answer == null || confidenceService.isAnswerUnknown(answer)) {
            return "I am afraid I don't know how to answer that.";
        }

        return answer;
    }


    public Flux<@NonNull String> chatStream(@NonNull LlmConnectorRequest request, IngestionMode mode) {
        prepareRequest(request, mode);

        String instructions = request.getInstructions() == null || request.getInstructions().isBlank()
                ? LlmInstructions.DEFAULT_RAG_INSTRUCTION
                : request.getInstructions();

        request.setInstructions(instructions);

        String userMessage = """
            QUESTION:
            %s

            Use the available tools when additional internal-document
            or web information is required.
            """.formatted(request.getQuery());

        Prompt prompt = buildPromptWithTokenBudget(request, userMessage);

        long start = System.currentTimeMillis();

        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
        StringBuilder fullResponse = new StringBuilder();

        return aiBeanFactory.getChatClient(
                        request.getSource(),
                        request.getType(),
                        request.getModel()
                )
                .prompt(prompt)
                .tools(
                        new RagTool(this, request),
                        new WebSearchTool(webSearchService, request)
                )
                .stream()
                .chatResponse()
                .doOnNext(lastResponse::set)
                .map(chatResponse -> {
                    Generation generation = chatResponse.getResult();

                    if (generation == null || generation.getOutput().getText() == null) {
                        return "";
                    }

                    String text = generation.getOutput().getText();
                    fullResponse.append(text);
                    return text;
                })
                .concatWith(Flux.defer(() -> {
                    if (confidenceService.isAnswerUnknown(fullResponse.toString())) {
                        return Flux.just(
                                "I am afraid I don't know how to answer that."
                        );
                    }

                    return Flux.empty();
                }))
                .doOnComplete(() -> {
                    ChatResponse last = lastResponse.get();

                    if (last == null) {
                        return;
                    }

                    try {
                        systemConsumptionStatsSvc.add(last, request, System.currentTimeMillis() - start);
                    } catch (Exception e) {
                        log.error("Failed to record stream consumption stats.", e);
                    }
                });
    }

    public String retrieveContext(@NonNull LlmConnectorRequest request) {
        LlmCapability type = LlmCapability.getFromValue(request.getType());

        if (!type.equals(LlmCapability.RAG)) {
            throw new ApiFallbackException("The requested type is not supported by this endpoint.");
        }

        if (request.getVectorStore() == null || request.getVectorStore().isBlank()) {
            throw new NullException("Selecting a vector storage is mandatory.");
        }

        LlmModels.getFromValue(request.getVectorStore());

        VectorStore vectorStore = vectorStoreBeanFactory.getVectorStore(request.getVectorStore());
        List<Document> documents = retrievalServiceV2.retrieve(vectorStore, request);

        if (documents.isEmpty()) {
            return "No relevant information was found in the internal knowledge base.";
        }

        log.info("Relevant docs found for RAG request: {}",documents.size());
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    private void prepareRequest(@NonNull LlmConnectorRequest request, IngestionMode mode) {
        validateAllowedType(request, mode);

        VectorStore vectorStore = vectorStoreBeanFactory.getVectorStore(request.getVectorStore());
        TokenTextSplitter splitter = tokenTextSplitterBuilder(request);

        switch (mode) {
            case AGENT -> documentIngestionServiceV2.ingest(
                    mode,
                    null,
                    vectorStore,
                    splitter,
                    request.getAgentName()
            );

            case CHAT -> {
                if (request.getFiles() != null && !request.getFiles().isEmpty()) {
                    for (MultipartFile file : request.getFiles()) {
                        documentIngestionServiceV2.ingest(
                                mode,
                                file,
                                vectorStore,
                                splitter,
                                request.getAgentName()
                        );
                    }
                }
            }
        }

    }

    private TokenTextSplitter tokenTextSplitterBuilder(LlmConnectorRequest request) {
        TokenTextSplitter.Builder splitter = TokenTextSplitter.builder();

        if (request.getEncodingType() !=null && !request.getEncodingType().isBlank()) {
            splitter.withEncodingType(EncodingType.fromName(request.getEncodingType()).orElseThrow(()-> new OperationFailedException("Invalid encoding type.")));
        } else {
            splitter.withEncodingType(EncodingType.CL100K_BASE);
        }
        if (request.getChunkSize() != null) {
            splitter.withChunkSize(request.getChunkSize());
        } else {
            splitter.withChunkSize(200);
        }
        if (request.getMinChunkLengthToEmbed() != null) {
            splitter.withMinChunkLengthToEmbed(request.getMinChunkLengthToEmbed());
        } else {
            splitter.withMinChunkLengthToEmbed(100);
        }
        if (request.getMinChunkSizeChars() != null) {
            splitter.withMinChunkSizeChars(request.getMinChunkSizeChars());
        } else {
            splitter.withMinChunkSizeChars(100);
        }
        if (request.getMaxNumChunks() != null) {
            splitter.withMaxNumChunks(request.getMaxNumChunks());
        } else {
            splitter.withMaxNumChunks(100);
        }
        if (request.getSeparator() != null) {
            splitter.withKeepSeparator(request.getSeparator());
        }
        return splitter.build();
    }

    private ChatOptions buildChatOptions(LlmConnectorRequest request) {
        OpenAiChatOptions.Builder openAiOptions = OpenAiChatOptions.builder()
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


    private Prompt buildPromptWithTokenBudget(LlmConnectorRequest request, String userMessage) {
        SystemMessage systemMessage = ChatMessageContextUtils.buildSystemMessage(request.getInstructions());

        UserMessage currentUserMessage = new UserMessage(userMessage);

        List<Message> historyMessages = request.isChatHistoryEnabled()
                ? ChatMessageContextUtils.buildHistoryMessages(request)
                : List.of();

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

        List<Message> trimmedHistory = historyMessages.isEmpty() ? List.of() : historyTokenTrimmer.trimHistory(
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

    private void validateAllowedType(LlmConnectorRequest request, IngestionMode mode){
        LlmCapability type = LlmCapability.getFromValue(request.getType());

        if (!type.equals(LlmCapability.RAG)) throw new ApiFallbackException("The requested type is not supported by this endpoint.");

        validateRequest(request, mode);
    }

    private void validateRequest(LlmConnectorRequest request, IngestionMode mode) {
        if (request.getVectorStore() == null || request.getVectorStore().isBlank()) throw new NullException("Selecting a vector storage is mandatory.");
        if (request.getEnablePrivateMode() == null) throw new NullException("Selecting a RAG mode is mandatory.");
        if (mode.equals(IngestionMode.CHAT) && request.getEnablePrivateMode() && (request.getFiles() == null || request.getFiles().isEmpty())) throw new NotAllowedException("Attaching a knowledge base with private mode enabled is mandatory.");
        if (request.getChunkSize() != null && request.getChunkSize() <= 0) throw new NotAllowedException("Chunk size cannot be less than 1.");
        if (request.getMinChunkLengthToEmbed() != null && request.getMinChunkLengthToEmbed() <= 0) throw new NotAllowedException("Minimum chunking length to embed cannot be less than 1.");
        if (request.getMinChunkSizeChars() !=null && request.getMinChunkSizeChars() <= 0) throw new NotAllowedException("Minimum character chunk size cannot be less than 1.");
        if (request.getMaxNumChunks() != null && request.getMaxNumChunks() <= 0) throw new NotAllowedException("Maximum number chunks cannot be less than 1.");

        //also verify the Embedding Model for vector store because vector store is attached with embedding model.
        LlmModels.getFromValue(request.getVectorStore());
    }
}
