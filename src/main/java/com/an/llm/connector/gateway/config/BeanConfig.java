package com.an.llm.connector.gateway.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BeanConfig {
    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * I will be removing it post dynamic configurations. As of now this stays.
     * @return {@link OpenAiEmbeddingModel} bean
     */
    @Bean
    public OpenAiEmbeddingModel embeddingModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("http://localhost:8081")
                .embeddingsPath("/v1/embeddings")
                .apiKey("ashar581")
                .build();

        return new OpenAiEmbeddingModel(openAiApi);
    }
}
