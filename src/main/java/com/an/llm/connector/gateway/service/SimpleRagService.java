package com.an.llm.connector.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimpleRagService {

    private final RetrievalService retrievalService;
    private final ConfidenceService confidenceService;
    private final ChatClient chatClient;

    public String ask(String question) {

        List<Document> docs = retrievalService.retrieve(question);

        if (!confidenceService.hasUsableContext(docs)) {
            return "I am afraid I don't know how to answer that.";
        }

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        System.out.println("Context: \n"+context);

        String response = chatClient
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
                        """.formatted(question,context))
                .call()
                .content();

        if (confidenceService.isAnswerUnknown(response)) {
            return "I am afraid I don't know how to answer that.";
        }

        return response;
    }
}
