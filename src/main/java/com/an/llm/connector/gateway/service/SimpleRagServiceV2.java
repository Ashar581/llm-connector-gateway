package com.an.llm.connector.gateway.service;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimpleRagServiceV2 {
    private final RetrievalService retrievalService;
    private final ConfidenceService confidenceService;
    private final AiBeanFactory aiBeanFactory;

    public String ask(LlmConnectorRequest request) {

        List<Document> docs = retrievalService.retrieve(request.getQuery());

        if (!confidenceService.hasUsableContext(docs)) {
            return "I am afraid I don't know how to answer that.";
        }

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        System.out.println("Context: \n"+context);

        String instructions = (request.getInstructions() !=null &&  !request.getInstructions().isBlank())? request.getInstructions() : LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL;

        String response = aiBeanFactory.getChatClient(request.getSource(), request.getType(), request.getModel())
                .prompt()
                .options(ChatOptions.builder()
                        .temperature(0.0)
                        .build()
                )
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
                .content();

        if (confidenceService.isAnswerUnknown(response)) {
            return "I am afraid I don't know how to answer that.";
        }

        return response;
    }
}
