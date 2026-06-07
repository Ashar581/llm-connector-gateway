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
import com.an.llm.connector.gateway.service.ai.EmbeddingServiceV2;
import com.an.llm.connector.gateway.service.classification.ClassificationOrchestrator;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.ai.VisionServiceV2;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import com.an.llm.connector.gateway.util.JsonUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentConfigurationRepository agentConfigurationRepository;
    private final VisionServiceV2 visionServiceV2;
    private final ClassificationOrchestrator classificationOrchestrator;
    private final EmbeddingServiceV2 embeddingServiceV2;
    private final AiBeanFactory aiBeanFactory;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;

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
            case EMBEDDING -> {
                return generateEmbeddingResponse(agentConfiguration,aiRequest);
            }
            default -> {
                return generateChatClientResponse(agentConfiguration,aiRequest);
            }
        }
    }

    //currently it does not have the history context feature.
    //another observation is, it better to use the ChatClientService.java's stream
    //here as well to make it less complex
    public Flux<@NonNull String> stream(AiRequest aiRequest) {

        AgentConfigurationEntity agentConfiguration = agentConfigurationRepository.findByName(aiRequest.getAgent())
                .orElseThrow(() -> new NotFoundException("Agent does not exist."));

        verifyAgentRequest(agentConfiguration);

        long start = System.currentTimeMillis();

        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
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
                    .chatResponse()
                    .doOnNext(lastResponse::set)
                    .map(chatResponse -> {
                        Generation generation = chatResponse.getResult();
                        if (generation == null || generation.getOutput().getText() == null) {
                            return "";
                        }
                        return generation.getOutput().getText();
                    })
                    .doOnComplete(() -> {
                        ChatResponse streamEnd = lastResponse.get();
                        if (streamEnd == null) {
                            return;
                        }
                        long completionTimeMs = System.currentTimeMillis() - start;
                        try {
                            systemConsumptionStatsSvc.add(streamEnd, aiRequest, completionTimeMs);
                        } catch (Exception e) {
                            log.error("Failed to record stream consumption stats", e);
                        }
                    });

        } catch (Exception e) {
            log.error("Error while calling LLM.", e);
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
            long start = System.currentTimeMillis();

            ChatResponse response =  chatClient
                    .prompt()
                    .system(agentConfiguration.getInstructions())
                    .options(buildChatOptions(agentConfiguration))
                    .user(aiRequest.getQuery())
                    .call()
                    .chatResponse();

            long completionTimeMs = System.currentTimeMillis() - start;

            assert response != null;
            //async service for generating the stats.
            try {
                systemConsumptionStatsSvc.add(response, aiRequest, completionTimeMs);
            } catch (Exception e){
                log.error("Error recording non-stream consumption tokens stats.",e);
            }

            return Objects.requireNonNull(response.getResult()).getOutput().getText();

        }catch (Exception e){
            log.error("Error while calling LLM.",e);
            throw new ApiFallbackException("Error communicating with AI.");
        }
    }

    private float [] generateEmbeddingResponse(AgentConfigurationEntity agentConfiguration, AiRequest aiRequest) {
        LlmConnectorRequest request = new LlmConnectorRequest();
        request.setFiles(aiRequest.getFiles());
        request.setQuery(aiRequest.getQuery());
        request.setModel(agentConfiguration.getModel().getValue());
        request.setSource(agentConfiguration.getSource().getValue());
        request.setType(agentConfiguration.getType().getValue());
        request.setTemperature(agentConfiguration.getTemperature());
        request.setMaxTokens(agentConfiguration.getMaxTokens());
        request.setInstructions(agentConfiguration.getInstructions());
        //setting agent name for stats
        request.setAgentName(aiRequest.getAgent());

        return embeddingServiceV2.embed(request);
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
        //setting agent name for stats
        request.setAgentName(aiRequest.getAgent());

        return visionServiceV2.visionPrompt(request);
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
        //setting agent name for token stats.
        request.setAgentName(aiRequest.getAgent());
        try {
            return classificationOrchestrator.process(request);
        } catch (Exception e){
            throw new ApiFallbackException(e.getMessage());
        }
    }


    // make a dynamic configuration
    private ChatOptions buildChatOptions(AgentConfigurationEntity agentConfiguration){
        OpenAiChatOptions.Builder openAiOptions = OpenAiChatOptions.builder()
                .streamUsage(true);

        if (agentConfiguration.getTemperature() != null) {
            openAiOptions.temperature(agentConfiguration.getTemperature());
        }

        if (agentConfiguration.getMaxTokens() != null) {
            openAiOptions.maxTokens(agentConfiguration.getMaxTokens());
        }
        return openAiOptions.build();
    }

    private void verifyAgentRequest(AgentConfigurationEntity agentConfiguration){
        if (!agentConfiguration.getActive()) throw new NotAllowedException("Agent is not active.");
        if (agentConfiguration.getInstructions() == null || agentConfiguration.getInstructions().isBlank()) throw new NullException("Agent does not have instructions.");
        if (agentConfiguration.getModel() == null) throw new NullException("Agent does not have model provided.");
        if (agentConfiguration.getType() == null) throw new NullException("Agent does not have type provided.");
        //check for public/private
    }

}
