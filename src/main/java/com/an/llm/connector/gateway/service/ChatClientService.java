package com.an.llm.connector.gateway.service;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatClientService {
    private final AiBeanFactory aiBeanFactory;

    public String ask(LlmConnectorRequest request){
        String instructions = (request.getInstructions() !=null &&  !request.getInstructions().isBlank())? request.getInstructions() : LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL;
        return aiBeanFactory.getChatClient(request.getSource(),  request.getType(),request.getModel())
                .prompt()
                .system(instructions)
//                .options(buildChatOptions())
                .user(request.getQuery())
                .call()
                .content();
    }

    public Flux<@NonNull String> askStream(LlmConnectorRequest request){
        String instructions = (request.getInstructions() !=null &&  !request.getInstructions().isBlank())? request.getInstructions() : LlmInstructions.TEST_CHAT_INSTRUCTION;

        return aiBeanFactory.getChatClient(request.getSource(),  request.getType(),request.getModel())
                .prompt()
                .system(instructions)
                .user(request.getQuery())
                .stream()
                .content();
    }

    // make a dynamic configuration
    private ChatOptions buildChatOptions(){
        ChatOptions.Builder<?> builder = ChatOptions.builder();

        builder.temperature(0.0);
        builder.maxTokens(1000);

        return builder.build();
    }
}
