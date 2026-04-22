package com.an.llm.connector.gateway.service;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatClientService {
    private final AiBeanFactory aiBeanFactory;

    public String ask(LlmConnectorRequest request){
        return aiBeanFactory.getChatClient(request.getSource(),  request.getType(),request.getModel())
                .prompt()
                .system(LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL)
                .user(request.getQuery())
                .call()
                .content();
    }

    public Flux<@NonNull String> askStream(LlmConnectorRequest request){
        return aiBeanFactory.getChatClient(request.getSource(),  request.getType(),request.getModel())
                .prompt()
                .system(LlmInstructions.TEST_CHAT_INSTRUCTION)
                .user(request.getQuery())
                .stream()
                .content();
    }
}
