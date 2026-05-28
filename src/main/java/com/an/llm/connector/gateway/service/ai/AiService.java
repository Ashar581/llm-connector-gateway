package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Deprecated
@Service
public class AiService {
    //just for quick testing.
    @Qualifier("qwen-instruct")
    @Autowired
    private ChatClient chatClient;

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
