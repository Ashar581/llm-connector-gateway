package com.an.llm.connector.gateway.service.v2;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VisionAggregationService {

    private final AiBeanFactory aiBeanFactory;

    public String aggregate(
            String originalPrompt,
            List<String> chunkResponses,
            LlmConnectorRequest request
    ) {

        StringBuilder aggregationPrompt =
                new StringBuilder();

        aggregationPrompt.append("""
                The following responses were generated
                from different page groups of the same
                document.

                Original Instruction:
                """);

        aggregationPrompt.append(
                originalPrompt
        );

        aggregationPrompt.append("\n\n");

        for (
                int i = 0;
                i < chunkResponses.size();
                i++
        ) {

            aggregationPrompt.append("\nCHUNK ").append(i + 1).append(":\n");

            aggregationPrompt.append(
                    chunkResponses.get(i)
            );

            aggregationPrompt.append("\n");
        }

        aggregationPrompt.append("""

                You are merging partial extraction results from different pages of the same document.
                
                Rules:
                - Never replace a non-null value with null.
                - Prefer non-null values.
                - Merge all fields.
                - If a field exists in one response and is null in another, keep the non-null value.
                - Return only the final merged JSON.
                """);

        ChatClient chatClient =
                aiBeanFactory.getChatClient(
                        request.getSource(),
                        request.getType(),
                        request.getModel()
                );

        return chatClient.prompt()
                .user(
                        aggregationPrompt.toString()
                )
                .call()
                .content();
    }
}