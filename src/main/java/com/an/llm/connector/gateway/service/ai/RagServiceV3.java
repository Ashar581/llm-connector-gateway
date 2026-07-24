package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.enums.IngestionMode;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.LlmModels;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.exception.NotAllowedException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.exception.OperationFailedException;
import com.an.llm.connector.gateway.model.ContextBudget;
import com.an.llm.connector.gateway.model.ConversationIntelligence;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.model.web.SearchRequest;
import com.an.llm.connector.gateway.model.web.WebDocument;
import com.an.llm.connector.gateway.service.LlmConfigService;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.factory.VectorStoreBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import com.an.llm.connector.gateway.service.tokenize.ContextBudgetService;
import com.an.llm.connector.gateway.service.tokenize.HistoryTokenTrimmer;
import com.an.llm.connector.gateway.service.web.WebSearchService;
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
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceV3 {
    private final VectorStoreBeanFactory vectorStoreBeanFactory;
    private final DocumentIngestionServiceV2 documentIngestionServiceV2;
    private final RetrievalServiceV2 retrievalServiceV2;
    private final ConfidenceService confidenceService;
    private final AiBeanFactory aiBeanFactory;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;
    private final LlmConfigService llmConfigService;
    private final ContextBudgetService contextBudgetService;
    private final HistoryTokenTrimmer historyTokenTrimmer;
    private final ConversationIntelligenceService conversationIntelligenceService;
    private final WebSearchService webSearchService;
    
    public String chat(@NonNull LlmConnectorRequest request, IngestionMode mode){
        validateAllowedType(request, mode);

        VectorStore vectorStore = vectorStoreBeanFactory.getVectorStore(request.getVectorStore());
        TokenTextSplitter tokenTextSplitter = tokenTextSplitterBuilder(request);

        //ingest the file if available.
        //only ingest if the file is non-existing in that vector store.
        switch (mode) {
            case AGENT -> documentIngestionServiceV2.ingest(
                    mode,
                    null,
                    vectorStore,
                    tokenTextSplitter,
                    request.getAgentName()
            );
            case CHAT -> {
                if (request.getFiles() != null && !request.getFiles().isEmpty()) {
                    for (MultipartFile file : request.getFiles()) {
                        documentIngestionServiceV2.ingest(
                                mode,
                                file,
                                vectorStore,
                                tokenTextSplitter,
                                request.getAgentName()
                        );
                    }
                }
            }
        }

        //give it to the LLM to generate structured answers.
        String instructions = (request.getInstructions() == null || request.getInstructions().isBlank()) ? LlmInstructions.DEFAULT_RAG_INSTRUCTION : request.getInstructions();

        //testing
        ConversationIntelligence intelligence = conversationIntelligenceService.analyse(request);
        log.info("Conversation Intelligence: {}", intelligence);

        StringBuilder contextBuilder = new StringBuilder();

        // RAG Retrieval
        List<Document> retrievedChunks = List.of();

        if (Boolean.TRUE.equals(intelligence.getRequiresRag())) {
            LlmConnectorRequest retrievalRequest = new LlmConnectorRequest();
            BeanUtils.copyProperties(request, retrievalRequest);
            retrievalRequest.setQuery(intelligence.getRagQuery());
            log.info("Retrieving RAG context using query: {}", intelligence.getRagQuery());
            retrievedChunks = retrievalServiceV2.retrieve(vectorStore, retrievalRequest);
            log.info("Retrieved {} chunks.", retrievedChunks.size());

            if (!retrievedChunks.isEmpty()) {
                contextBuilder.append("PRIVATE KNOWLEDGE BASE:\n")
                        .append(
                                retrievedChunks.stream()
                                        .map(Document::getText)
                                        .collect(Collectors.joining("\n\n"))
                        )
                        .append("\n\n");
            }
        }

        // Internet Search
        boolean internetAllowed = Boolean.FALSE.equals(request.getEnablePrivateMode());

        if (internetAllowed && Boolean.TRUE.equals(intelligence.getRequiresInternet())) {
            log.info("Searching Internet using query: {}", intelligence.getInternetQuery());
            String webResults = webSearchService.search(
                    new SearchRequest(
                            intelligence.getInternetQuery(),
                            3,
                            Duration.ofSeconds(10)
                    ),
                    request
            );
            if (!webResults.isBlank()) {
                instructions += """
                        INTERNET CONTEXT:
                        - Use this only when relevant.
                        - If both private knowledge and Internet provide information, prefer the private knowledge base when they conflict unless the user explicitly requests current/public information.
                        """;

                contextBuilder.append("INTERNET:\n")
                        .append(webResults)
                        .append("\n\n");
            }
        }

        String context = contextBuilder.toString();

        long start = System.currentTimeMillis();

        ChatResponse response;
        if (!request.isChatHistoryEnabled()) {
            response = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .options(buildChatOptions(request))
                    .system(instructions)
                    .user("""
                        QUESTION:
                        %s
                        
                        CONTEXT:
                        %s
                        """.formatted(request.getQuery(),context))
                    .call()
                    .chatResponse();
        } else {
            String userMessage = """
                        QUESTION:
                        %s

                        CONTEXT:
                        %s
                        """.formatted(request.getQuery(), context);

            Prompt prompt = buildPromptWithTokenBudget(request, userMessage);

            response = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt(prompt)
                    .call()
                    .chatResponse();
        }

        //return the response.
        long completionTimeMs = System.currentTimeMillis() - start;

        assert response != null;
        //async service for generating the stats.
        try {
            systemConsumptionStatsSvc.add(response, request, completionTimeMs);
        } catch (Exception e){
            log.error("Error recording non-stream consumption tokens stats.",e);
        }

        String serialisedResponse = Objects.requireNonNull(response.getResult()).getOutput().getText();
        if (confidenceService.isAnswerUnknown(serialisedResponse)) {
            return "I am afraid I don't know how to answer that.";
        }

        return serialisedResponse;
    }

    public Flux<@NonNull String> chatStream(@NonNull LlmConnectorRequest request, IngestionMode mode) {
        validateAllowedType(request, mode);

        VectorStore vectorStore = vectorStoreBeanFactory.getVectorStore(request.getVectorStore());
        TokenTextSplitter tokenTextSplitter = tokenTextSplitterBuilder(request);

        switch (mode) {
            case AGENT -> documentIngestionServiceV2.ingest(
                    mode,
                    null,
                    vectorStore,
                    tokenTextSplitter,
                    request.getAgentName()
            );
            case CHAT -> {
                if (request.getFiles() != null && !request.getFiles().isEmpty()) {
                    for (MultipartFile file : request.getFiles()) {
                        documentIngestionServiceV2.ingest(
                                mode,
                                file,
                                vectorStore,
                                tokenTextSplitter,
                                request.getAgentName()
                        );
                    }
                }
            }
        }

        String instructions = (request.getInstructions() == null || request.getInstructions().isBlank())
                ? LlmInstructions.DEFAULT_RAG_INSTRUCTION
                : request.getInstructions();

        ConversationIntelligence intelligence = conversationIntelligenceService.analyse(request);
        log.info("Conversation Intelligence (stream): {}", intelligence);

        StringBuilder contextBuilder = new StringBuilder();

        // RAG Retrieval
        List<Document> retrievedChunks = List.of();

        if (Boolean.TRUE.equals(intelligence.getRequiresRag())) {
            LlmConnectorRequest retrievalRequest = new LlmConnectorRequest();
            BeanUtils.copyProperties(request, retrievalRequest);
            retrievalRequest.setQuery(intelligence.getRagQuery());
            log.info("Retrieving RAG context using query (stream): {}", intelligence.getRagQuery());
            retrievedChunks = retrievalServiceV2.retrieve(vectorStore, retrievalRequest);
            log.info("Retrieved {} chunks.", retrievedChunks.size());

            if (!retrievedChunks.isEmpty()) {
                contextBuilder.append("PRIVATE KNOWLEDGE BASE:\n")
                        .append(
                                retrievedChunks.stream()
                                        .map(Document::getText)
                                        .collect(Collectors.joining("\n\n"))
                        )
                        .append("\n\n");
            }
        }

        // Internet Search
        boolean internetAllowed = Boolean.FALSE.equals(request.getEnablePrivateMode());

        if (internetAllowed && Boolean.TRUE.equals(intelligence.getRequiresInternet())) {
            log.info("Searching Internet using query (stream): {}", intelligence.getInternetQuery());
            String webResults = webSearchService.search(
                    new SearchRequest(
                            intelligence.getInternetQuery(),
                            3,
                            Duration.ofSeconds(10)
                    ),
                    request
            );
            if (!webResults.isBlank()) {
                instructions += """
                        INTERNET CONTEXT:
                        - Use this only when relevant.
                        - If both private knowledge and Internet provide information, prefer the private knowledge base when they conflict unless the user explicitly requests current/public information.
                        """;

                contextBuilder.append("INTERNET:\n")
                        .append(webResults)
                        .append("\n\n");
            }
        }
        String context = contextBuilder.toString();

        long start = System.currentTimeMillis();

        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
        StringBuilder fullResponse = new StringBuilder();

        Flux<@NonNull ChatResponse> responseFlux;

        if (!request.isChatHistoryEnabled()) {

            responseFlux = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt()
                    .options(buildChatOptions(request))
                    .system(instructions)
                    .user("""
                        QUESTION:
                        %s

                        CONTEXT:
                        %s
                        """.formatted(request.getQuery(), context))
                    .stream()
                    .chatResponse();

        } else {

            String userMessage = """
                QUESTION:
                %s

                CONTEXT:
                %s
                """.formatted(request.getQuery(), context);

            Prompt prompt = buildPromptWithTokenBudget(request, userMessage);

            responseFlux = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                    .prompt(prompt)
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

                    String text = generation.getOutput().getText();
                    fullResponse.append(text);
                    return text;
                })
                .concatWith(Flux.defer(() -> {
                    if (confidenceService.isAnswerUnknown(fullResponse.toString())) {
                        return Flux.just("I am afraid I don't know how to answer that.");
                    }
                    return Flux.empty();
                }))
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

        List<Message> trimmedHistory =
                historyTokenTrimmer.trimHistory(
                        modelConfig.getBaseUrl(),
                        historyMessages,
                        budget.getHistoryBudget());

        List<Message> finalMessages =
                ChatMessageContextUtils.merge(
                        systemMessage,
                        trimmedHistory,
                        currentUserMessage);

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
