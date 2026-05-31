package com.an.llm.connector.gateway.service.vision;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VisionAggregationService {

    private final AiBeanFactory aiBeanFactory;

    public String aggregate(String originalPrompt, List<String> chunkResponses, LlmConnectorRequest request) {
        StringBuilder aggregationPrompt = new StringBuilder();

        aggregationPrompt.append("""
                The following responses were generated
                from different page groups of the same
                document.

                Original Instruction:
                """);

        aggregationPrompt.append(originalPrompt);
        aggregationPrompt.append("\n\n");

        for (int i = 0; i < chunkResponses.size(); i++) {
            aggregationPrompt.append("\nCHUNK ").append(i + 1).append(":\n");
            aggregationPrompt.append(chunkResponses.get(i));
            aggregationPrompt.append("\n");
        }

        aggregationPrompt.append("""
        You are consolidating responses generated from different page groups of the SAME document.

        Rules:

        - Preserve the format requested by the original instruction.
        - Do not change the output format.
        - Do not introduce a new format.
        - Remove duplicate information.
        - Preserve all useful information.
        - Never replace a non-null value with null.
        - If multiple values exist for the same field, prefer the most complete non-null value.
        - Merge information from all chunk responses.
        - Do not lose information present in any chunk.
        - Return a single consolidated response that follows the original instruction exactly.

        Examples:

        - If the original instruction requests JSON, return JSON.
        - If the original instruction requests a summary, return a summary.
        - If the original instruction requests plain text, return plain text.
        - If the original instruction requests a list, return a list.

        Return only the final consolidated result.
        """);

        ChatClient chatClient = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        return chatClient.prompt()
                .user(aggregationPrompt.toString())
                .options(ChatOptions.builder().temperature(0D).build())
                .call()
                .content();
    }
}