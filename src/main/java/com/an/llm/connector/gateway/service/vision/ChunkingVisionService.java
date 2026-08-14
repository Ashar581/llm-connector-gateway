package com.an.llm.connector.gateway.service.vision;

import com.an.llm.connector.gateway.entity.system.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.VisionInternalStatsAndResponse;
import com.an.llm.connector.gateway.repository.SystemConsumptionStatsRepo;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkingVisionService {
    private final AiBeanFactory aiBeanFactory;
    private final SystemConsumptionStatsRepo systemConsumptionStatsRepo;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;

    //inclusive of token consumption stats.
    public VisionInternalStatsAndResponse executeChunk(List<byte[]> pages, String instructions, LlmConnectorRequest request) {
        List<Media> mediaList = pages.stream()
                .map(bytes -> Media.builder()
                        .mimeType(MediaType.IMAGE_PNG)
                        .data(new ByteArrayResource(bytes))
                        .build()
                )
                .toList();

        UserMessage message = UserMessage.builder()
                .text((request.getQuery() != null && !request.getQuery().isBlank()) ? request.getQuery() : "Follow the system instructions")
                .media(mediaList)
                .build();

        ChatClient chatClient = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        ChatResponse response = chatClient.prompt(new Prompt(message))
                .system(instructions)
                .options(buildChatOptions(request))
                .call()
                .chatResponse();

        //code block for computing the tokens stats.
        assert response != null;
        String serializedResponse = Objects.requireNonNull(response.getResult()).getOutput().getText();

        log.info("\nChunk: {}\n",serializedResponse);

        //block for retaining the token consumption stats.
        SystemConsumptionStatsEntity stats = null;
        try {
            stats = systemConsumptionStatsSvc.generateStatsEntityWithoutPersisting(response, request);
        } catch (Exception ignore) {}

        return new VisionInternalStatsAndResponse(serializedResponse,stats);
    }

    //specific to pdfs within single API call request.
    public String executeVisionWithinSingleRange(List<byte[]> pages, String instructions, LlmConnectorRequest request) {
        List<Media> mediaList = pages.stream()
                .map(bytes -> Media.builder()
                        .mimeType(MediaType.IMAGE_PNG)
                        .data(new ByteArrayResource(bytes))
                        .build()
                )
                .toList();

        UserMessage message = UserMessage.builder()
                .text((request.getQuery() != null && !request.getQuery().isBlank()) ? request.getQuery() : "Follow the system instructions")
                .media(mediaList)
                .build();

        ChatClient chatClient = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        long start = System.currentTimeMillis();

        ChatResponse response =  chatClient.prompt(new Prompt(message))
                .system(instructions)
                .options(buildChatOptions(request))
                .call()
                .chatResponse();

        //code block for computing the tokens stats.
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
