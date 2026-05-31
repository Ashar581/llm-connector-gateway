package com.an.llm.connector.gateway.service.v2;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChunkingVisionService {

    private final AiBeanFactory aiBeanFactory;

    public String executeChunk(
            List<byte[]> pages,
            String prompt,
            LlmConnectorRequest request
    ) {

        List<Media> mediaList =
                pages.stream()
                        .map(bytes ->
                                Media.builder()
                                        .mimeType(
                                                MediaType.IMAGE_PNG
                                        )
                                        .data(
                                                new ByteArrayResource(
                                                        bytes
                                                )
                                        )
                                        .build()
                        )
                        .toList();

        UserMessage message =
                UserMessage.builder()
                        .text(prompt)
                        .media(mediaList)
                        .build();

        ChatClient chatClient =
                aiBeanFactory.getChatClient(
                        request.getSource(),
                        request.getType(),
                        request.getModel()
                );

        return chatClient.prompt(
                        new Prompt(message)
                )
                .options(
                        buildChatOptions(request)
                )
                .call()
                .content();
    }

    private ChatOptions buildChatOptions(LlmConnectorRequest request){
        ChatOptions.Builder<?> builder = ChatOptions.builder();

        builder.temperature(request.getTemperature());
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }

        return builder.build();
    }
}
