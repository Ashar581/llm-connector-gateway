package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatClientService {
    private final AiBeanFactory aiBeanFactory;

    public String ask(LlmConnectorRequest request){
        validateAllowedType(request);
        String instructions = (request.getInstructions() !=null &&  !request.getInstructions().isBlank())? request.getInstructions() : LlmInstructions.CHAT_INSTRUCTIONS_UNIVERSAL;
        return aiBeanFactory.getChatClient(request.getSource(),  request.getType(),request.getModel())
                .prompt()
                .system(instructions)
                .options(buildChatOptions(request))
                .user(request.getQuery())
                .call()
                .content();
    }

    public Flux<@NonNull String> askStream(LlmConnectorRequest request){
        String instructions = (request.getInstructions() !=null &&  !request.getInstructions().isBlank())? request.getInstructions() : LlmInstructions.TEST_CHAT_INSTRUCTION;

        return aiBeanFactory.getChatClient(request.getSource(),  request.getType(),request.getModel())
                .prompt()
                .system(instructions)
                .options(buildChatOptions(request))
                .user(request.getQuery())
                .stream()
                .content();
    }

    // make a dynamic configuration
    private ChatOptions buildChatOptions(LlmConnectorRequest request){
        ChatOptions.Builder<?> builder = ChatOptions.builder();

        if (request.getTemperature()!=null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }

        return builder.build();
    }

    private void validateAllowedType(LlmConnectorRequest request){
        LlmCapability type = LlmCapability.getFromValue(request.getType());
        Set<LlmCapability> notAllowedTypes = Set.of(LlmCapability.CLASSIFICATION, LlmCapability.EMBEDDING, LlmCapability.VISION);

        if (notAllowedTypes.contains(type)) throw new ApiFallbackException("The requested type is not supported by this endpoint.");
    }
}
