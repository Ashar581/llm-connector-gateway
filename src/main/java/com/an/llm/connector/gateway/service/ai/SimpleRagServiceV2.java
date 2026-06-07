package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimpleRagServiceV2 {
    private final RetrievalService retrievalService;
    private final ConfidenceService confidenceService;
    private final AiBeanFactory aiBeanFactory;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;

    public String ask(LlmConnectorRequest request) {

        List<Document> docs = retrievalService.retrieve(request.getQuery());

        if (!confidenceService.hasUsableContext(docs)) {
            return "I am afraid I don't know how to answer that.";
        }

        validateAllowedType(request);

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        System.out.println("Context: \n"+context);

        String instructions = (request.getInstructions() !=null &&  !request.getInstructions().isBlank())? request.getInstructions() : LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL;

        long start = System.currentTimeMillis();

        ChatResponse response = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                .prompt()
                .options(buildChatOptions(request))
                .system("""
                        You are a helpful RAG assistant.
                        
                        Rules:
                        - Use the provided context as your primary source of truth.
                        - The answer may not match the question wording exactly. Use semantic understanding.
                        - If the question refers to a general concept (e.g., "basic policies"), summarize the relevant sections from the context.
                        - Do not require exact keyword matches.
                        - If multiple relevant points exist, list them clearly.
                        - Keep the answer short and to the point.
                        - Only say UNKNOWN if absolutely no relevant information exists.
                        - Be confident when the context reasonably supports the answer.
                        """)
                .user("""
                        QUESTION:
                        %s
                        
                        CONTEXT:
                        %s
                        """.formatted(request.getQuery(),context))
                .call()
                .chatResponse();

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

    private void validateAllowedType(LlmConnectorRequest request){
        LlmCapability type = LlmCapability.getFromValue(request.getType());
        //not allowed list
        Set<LlmCapability> notAllowedTypes = Set.of(LlmCapability.CLASSIFICATION, LlmCapability.EMBEDDING, LlmCapability.VISION);

        if (notAllowedTypes.contains(type)) throw new ApiFallbackException("The requested type is not supported by this endpoint.");
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
}
