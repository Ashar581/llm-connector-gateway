package com.an.llm.connector.gateway.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
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
}
