package com.an.llm.connector.gateway.service;

import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionService {
    private final AiBeanFactory aiBeanFactory;
    private final DocumentVisionPreprocessor documentVisionPreprocessor;

    public String visionPrompt(LlmConnectorRequest request) {
        try {
            List<byte[]> pages = documentVisionPreprocessor.preprocess(request.getFiles().getFirst());

            int maxPages = Math.min(pages.size(), 15);
            pages = pages.subList(0, maxPages);

            List<Media> mediaList = pages.stream()
                    .map(bytes -> Media.builder()
                            .mimeType(MediaType.IMAGE_PNG)
                            .data(new ByteArrayResource(bytes))
                            .build()
                    )
                    .toList();

            String prompt = request.getInstructions() != null ? request.getInstructions() : LlmInstructions.INVOICE_OCR_INSTRUCTIONS;

            UserMessage userMessage = UserMessage.builder()
                    .text(prompt)
                    .media(mediaList)
                    .build();

            ChatClient chatClient = aiBeanFactory.getChatClient(
                    request.getSource(),
                    request.getType(),
                    request.getModel()
            );

            return chatClient.prompt(new Prompt(userMessage))
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("Error while communication with VL.",e);
            throw new ApiFallbackException("Error while communicating with VL model.");
        }
    }

}
