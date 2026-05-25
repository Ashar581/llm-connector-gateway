package com.an.llm.connector.gateway.service.agent;

import com.an.llm.connector.gateway.entity.AgentConfigurationEntity;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.model.AiRequest;
import com.an.llm.connector.gateway.repository.AgentConfigurationRepository;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentConfigurationRepository agentConfigurationRepository;
    private final AiBeanFactory aiBeanFactory;

    public String generate(@NonNull AiRequest aiRequest){
        AgentConfigurationEntity agentConfiguration = agentConfigurationRepository.findByName(aiRequest.getAgent())
                .orElseThrow(()->new NotFoundException("Agent does not exist."));

        try {
            ChatClient chatClient = aiBeanFactory.getChatClient(
                    agentConfiguration.getSource().getValue(),
                    agentConfiguration.getType().getValue(),
                    agentConfiguration.getModel().getValue()
            );

            ChatOptions chatOptions = buildChatOptions(agentConfiguration);

            return chatClient
                    .prompt()
                    .system(agentConfiguration.getInstructions())
                    .options(chatOptions)
                    .user(aiRequest.getQuery())
                    .call()
                    .content();
        }catch (Exception e){
            log.error("Error while calling LLM.",e);
            throw new ApiFallbackException("Error communicating with AI.");
        }
    }

    public Flux<@NonNull String> stream(AiRequest aiRequest){
        AgentConfigurationEntity agentConfiguration = agentConfigurationRepository.findByName(aiRequest.getAgent())
                .orElseThrow(()->new NotFoundException("Agent does not exist."));

        try {
            ChatClient chatClient = aiBeanFactory.getChatClient(
                    agentConfiguration.getSource().getValue(),
                    agentConfiguration.getType().getValue(),
                    agentConfiguration.getModel().getValue()
            );

            ChatOptions chatOptions = buildChatOptions(agentConfiguration);

            return chatClient
                    .prompt()
                    .system(agentConfiguration.getInstructions())
                    .options(chatOptions)
                    .user(aiRequest.getQuery())
                    .stream()
                    .content();
        }catch (Exception e){
            log.error("Error while calling LLM.",e);
            throw new ApiFallbackException("Error communicating with AI.");
        }
    }


    // make a dynamic configuration
    private ChatOptions buildChatOptions(AgentConfigurationEntity agentConfiguration){
        ChatOptions.Builder<?> builder = ChatOptions.builder();

        builder.temperature(agentConfiguration.getTemperature());
        if (agentConfiguration.getMaxTokens() != null) {
            builder.maxTokens(agentConfiguration.getMaxTokens());
        }

        return builder.build();
    }

    private void verifyAgentMandatoryFields(AgentConfigurationEntity entity){

    }
}
