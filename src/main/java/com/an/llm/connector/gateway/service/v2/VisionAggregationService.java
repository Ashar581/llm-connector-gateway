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

        StringBuilder aggregationPrompt = new StringBuilder();

        aggregationPrompt.append("""
                You are given responses generated from different chunks of the same document.

                Original user instruction:
                """);

        aggregationPrompt.append(originalPrompt);

        aggregationPrompt.append("\n\n");

        for (int i = 0; i < chunkResponses.size(); i++) {

            aggregationPrompt.append("""
                    
                    CHUNK RESPONSE %d:
                    """
                    .formatted(i + 1));

            aggregationPrompt.append(chunkResponses.get(i));
            aggregationPrompt.append("\n");
        }

        aggregationPrompt.append("""

                Merge all chunk responses.

                - Remove duplicates.
                - Preserve information.
                - Follow the original instruction.
                - Return a single final answer.
                """);

        ChatClient chatClient = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        return chatClient.prompt()
                .user(aggregationPrompt.toString())
                .call()
                .content();
    }
}