package com.an.llm.connector.gateway.service.agent;

import com.an.llm.connector.gateway.entity.AgentConfigurationEntity;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.exception.NotAllowedException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.AiRequest;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.classification.ClassificationResponse;
import com.an.llm.connector.gateway.repository.AgentConfigurationRepository;
import com.an.llm.connector.gateway.service.ai.VisionService;
import com.an.llm.connector.gateway.service.classification.ClassificationOrchestrator;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.util.JsonUtils;
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
    private final VisionService visionService;
    private final ClassificationOrchestrator classificationOrchestrator;
    private final AiBeanFactory aiBeanFactory;

    public Object generate(@NonNull AiRequest aiRequest){
        AgentConfigurationEntity agentConfiguration = agentConfigurationRepository.findByName(aiRequest.getAgent())
                .orElseThrow(()->new NotFoundException("Agent does not exist."));

        verifyAgentRequest(agentConfiguration);

        switch (agentConfiguration.getType()) {
            case CLASSIFICATION -> {
                return generateClassificationResponse(agentConfiguration,aiRequest);
            }
            case VISION -> {
                return generateVisionResponse(agentConfiguration,aiRequest);
            }
            default -> {
                return generateChatClientResponse(agentConfiguration,aiRequest);
            }
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

    private String generateChatClientResponse(AgentConfigurationEntity agentConfiguration, AiRequest aiRequest){
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

    private String generateVisionResponse(AgentConfigurationEntity agentConfiguration, AiRequest aiRequest){
        LlmConnectorRequest request = new LlmConnectorRequest();
        request.setFiles(aiRequest.getFiles());
        request.setQuery(aiRequest.getQuery());
        request.setModel(agentConfiguration.getModel().getValue());
        request.setSource(agentConfiguration.getSource().getValue());
        request.setType(agentConfiguration.getType().getValue());
        request.setTemperature(agentConfiguration.getTemperature());
        request.setMaxTokens(agentConfiguration.getMaxTokens());
        request.setInstructions(agentConfiguration.getInstructions());

        return visionService.visionPrompt(request);
    }

    private ClassificationResponse generateClassificationResponse(AgentConfigurationEntity agentConfiguration, AiRequest aiRequest) {
        LlmConnectorRequest request = new LlmConnectorRequest();

        request.setFiles(aiRequest.getFiles());
        request.setQuery(aiRequest.getQuery());
        request.setModel(agentConfiguration.getModel().getValue());
        request.setType(agentConfiguration.getType().getValue());
        request.setSource(agentConfiguration.getSource().getValue());
        request.setInstructions(agentConfiguration.getInstructions());
        request.setMode(agentConfiguration.getClassificationMode());
        request.setDocumentTypes(JsonUtils.serializeClass(agentConfiguration.getDocumentTypes()));
        try {
            return classificationOrchestrator.process(request);
        } catch (Exception e){
            throw new ApiFallbackException(e.getMessage());
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
