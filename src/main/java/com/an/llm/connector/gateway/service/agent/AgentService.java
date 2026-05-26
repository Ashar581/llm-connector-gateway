package com.an.llm.connector.gateway.service.agent;

import com.an.llm.connector.gateway.entity.AgentConfigurationEntity;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.exception.NotAllowedException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.AiRequest;
import com.an.llm.connector.gateway.model.config.SourceConfig;
import com.an.llm.connector.gateway.repository.AgentConfigurationRepository;
import com.an.llm.connector.gateway.service.LlmConfigService;
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

        verifyAgentRequest(agentConfiguration);

        ChatClient chatClient = aiBeanFactory.getChatClient(
                agentConfiguration.getSource().getValue(),
                agentConfiguration.getType().getValue(),
                agentConfiguration.getModel().getValue()
        );

        try {

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

        verifyAgentRequest(agentConfiguration);

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

    private void verifyAgentRequest(AgentConfigurationEntity agentConfiguration){
        if (!agentConfiguration.getActive()) throw new NotAllowedException("Agent is not active.");
        if (agentConfiguration.getInstructions() == null || agentConfiguration.getInstructions().isBlank()) throw new NullException("Agent does not have instructions.");
        if (agentConfiguration.getModel() == null) throw new NullException("Agent does not have model provided.");
        if (agentConfiguration.getType() == null) throw new NullException("Agent does not have type provided.");
        //check for public/private
    }

}
