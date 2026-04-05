package com.an.llm.connector.gateway.service;

import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class AiService {
    private final ChatClient chatClient;

    public String ask(String prompt){
        return chatClient.prompt()
                .system(LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL)
                .user(prompt)
                .call()
                .content();
    }

    public Flux<@NonNull String> askStream(String prompt){
        return chatClient.prompt()
                .system(LlmInstructions.TEST_CHAT_INSTRUCTION)
                .user(prompt)
                .stream()
                .content();
    }
}
