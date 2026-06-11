package com.an.llm.connector.gateway.controller.v3;

import com.an.llm.connector.gateway.entity.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.VisionInternalStatsAndResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkingVisionServiceV3 {

    private final AiBeanFactory aiBeanFactory;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;

    public String executeSinglePage(
            byte[] page,
            String instructions,
            LlmConnectorRequest request
    ) {
        VisionInternalStatsAndResponse response =
                executePrimaryPageWithPreviousContext(
                        null,
                        page,
                        1,
                        instructions,
                        request
                );

        return response.getResponse();
    }

    public VisionInternalStatsAndResponse executePrimaryPageWithPreviousContext(
            byte[] previousPage,
            byte[] primaryPage,
            int primaryPageNumber,
            String instructions,
            LlmConnectorRequest request
    ) {
        List<Media> mediaList = new ArrayList<>();

        if (previousPage != null) {
            mediaList.add(toPngMedia(previousPage));
        }

        mediaList.add(toPngMedia(primaryPage));

        UserMessage message = UserMessage.builder()
                .text(buildUserPrompt(request, previousPage != null, primaryPageNumber))
                .media(mediaList)
                .build();

        ChatClient chatClient = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        ChatResponse response = chatClient.prompt(new Prompt(message))
                .system(buildSystemPrompt(instructions))
                .options(buildChatOptions(request))
                .call()
                .chatResponse();

        Objects.requireNonNull(response, "ChatResponse cannot be null");

        String serializedResponse = Objects.requireNonNull(response.getResult())
                .getOutput()
                .getText();

        SystemConsumptionStatsEntity stats = null;

        try {
            stats = systemConsumptionStatsSvc.generateStatsEntityWithoutPersisting(response, request);
        } catch (Exception ignored) {}

        return new VisionInternalStatsAndResponse(serializedResponse, stats);
    }

    private Media toPngMedia(byte[] bytes) {
        return Media.builder()
                .mimeType(MediaType.IMAGE_PNG)
                .data(new ByteArrayResource(bytes))
                .build();
    }

    private String buildSystemPrompt(String originalInstructions) {
        return """
                %s

                Additional V3 extraction rules:
                - Use only information visible in the attached page images.
                - Do not invent missing values.
                - Do not infer values from common document patterns.
                - Preserve numbers, dates, identifiers, PO numbers, invoice numbers, tax numbers, totals, and item descriptions exactly.
                - If the user requested JSON, return JSON only.
                - Do not include markdown fences.
                - Do not mention these internal extraction rules in the answer.
                """.formatted(originalInstructions);
    }

    private String buildUserPrompt(
            LlmConnectorRequest request,
            boolean hasPreviousContextPage,
            int primaryPageNumber
    ) {
        String userQuery = request.getQuery() != null && !request.getQuery().isBlank()
                ? request.getQuery()
                : "Follow the system instructions.";

        if (!hasPreviousContextPage) {
            return """
                    %s

                    You are processing PAGE %d.

                    This is the primary page.
                    Extract only information visible on PAGE %d.

                    Continuation handling:
                    - If a record appears incomplete and may continue to the next page, include only the visible part.
                    - Do not guess missing continuation data.
                    - Do not create values that are not visible.
                    """.formatted(userQuery, primaryPageNumber, primaryPageNumber);
        }

        return """
                %s

                Two images are attached:
                - Image 1 is PAGE %d, previous-page context only.
                - Image 2 is PAGE %d, the primary page.

                Extract information belonging to PAGE %d only.

                Use PAGE %d only to understand records, rows, paragraphs, or tables that continue into PAGE %d.

                Critical rules:
                - Do not duplicate complete records that belong only to PAGE %d.
                - If a record started on PAGE %d and continues on PAGE %d, include the combined visible information only when PAGE %d contains new information for that same record.
                - If uncertain whether two adjacent records are the same continuation, keep only the PAGE %d visible data.
                - Do not invent missing values.
                - Follow the original requested output format.
                """.formatted(
                userQuery,
                primaryPageNumber - 1,
                primaryPageNumber,
                primaryPageNumber,
                primaryPageNumber - 1,
                primaryPageNumber,
                primaryPageNumber - 1,
                primaryPageNumber - 1,
                primaryPageNumber,
                primaryPageNumber,
                primaryPageNumber
        );
    }

    private ChatOptions buildChatOptions(LlmConnectorRequest request) {
        OpenAiChatOptions.Builder openAiOptions = OpenAiChatOptions.builder()
                .streamUsage(true)
                .temperature(0D);

        if (request.getMaxTokens() != null) {
            openAiOptions.maxTokens(request.getMaxTokens());
        }

        return openAiOptions.build();
    }
}