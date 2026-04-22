package com.an.llm.connector.gateway.service.factory;

import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.service.LlmConfigService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiBeanFactory {
    private final Map<String, ChatClient> chatClientFactory;
    private final Map<String, OpenAiEmbeddingModel> embeddingModelFactory;
    private final LlmConfigService llmConfigService;

    public ChatClient getChatClient(@NotNull String source, @NotNull String type, @NotNull String model){
        //verify request.
        llmConfigService.isLlmSupported(source, type , model);

        if (!chatClientFactory.containsKey(model)) throw new NotFoundException(String.format("Model %s is not supported.",model));

        return chatClientFactory.get(model);
    }

    public OpenAiEmbeddingModel getOpenAiEmbeddingModel(@NotNull String source, @NotNull String type, @NotNull String model) {
        //verify request.
        llmConfigService.isLlmSupported(source, type , model);

        if (!embeddingModelFactory.containsKey(model)) throw new NotFoundException(String.format("Embedding Model %s is not supported.",model));

        return embeddingModelFactory.get(model);
    }

    //get vector store bean.
}
