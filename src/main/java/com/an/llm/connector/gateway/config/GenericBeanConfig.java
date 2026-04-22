package com.an.llm.connector.gateway.config;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.Source;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.model.config.SourceConfig;
import com.an.llm.connector.gateway.service.LlmConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;


@Slf4j
@Configuration
@RequiredArgsConstructor
public class GenericBeanConfig {
    private final GenericApplicationContext genericApplicationContext;
    private final LlmConfigService llmConfigService;

    //currently only creating beans for free endpoints.
    @PostConstruct
    public void initializeLlmBeans(){
        SourceConfig sourceConfig = llmConfigService.getModelConfigBySource(Source.FREE);
        if (sourceConfig==null) {
            log.error("No LLM source found for initializing bean creation.");
            return;
        }
        List<ModelConfig> modelConfigs = sourceConfig.getModels();
        if (modelConfigs==null || modelConfigs.isEmpty()) {
            log.error("No LLM models available for bean creation.");
            return;
        }
        for (ModelConfig config : modelConfigs) {
            if (config.getType().contains(LlmCapability.EMBEDDING.getValue())){
                log.info("Creating bean for OpenAiEmbeddingModel: {} ",config.getId());
                //embedding bean.
                //for attaching the bean to vector store, either make a logic for dynamic bean creation or make
                //a bean of embedding defaulted to oen fo the running embedding model. -> bge-large-embed
                genericApplicationContext.registerBean(
                        config.getId(),
                        OpenAiEmbeddingModel.class,
                        () -> new OpenAiEmbeddingModel(buildEmbeddingOpenAiApi(config))
                );
            } else {
                log.info("Creating bean for ChatClient with name: {}",config.getId());
                //register ChatClint with OpenAiChatModel as parameter
                genericApplicationContext.registerBean(
                        config.getId(),
                        ChatClient.class,
                        () -> ChatClient.create(
                                buildChatOpenAiModel(config)
                        )
                );
            }
        }
    }

    private OpenAiApi buildChatOpenAiApi(ModelConfig config){
        return OpenAiApi.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .completionsPath(config.getApiPath())
                .build();
    }

    private OpenAiApi buildEmbeddingOpenAiApi(ModelConfig config){
        return OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .embeddingsPath(config.getApiPath())
                .apiKey(config.getApiKey())
                .build();
    }

    private OpenAiChatModel buildChatOpenAiModel(ModelConfig config){
        return OpenAiChatModel.builder()
                .openAiApi(buildChatOpenAiApi(config))
                .build();
    }

}
