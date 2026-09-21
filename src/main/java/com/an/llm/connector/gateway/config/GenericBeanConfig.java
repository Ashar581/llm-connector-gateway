package com.an.llm.connector.gateway.config;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.enums.Source;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.model.config.SourceConfig;
import com.an.llm.connector.gateway.service.LlmConfigService;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

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
        initializeFreeLlmBeans();
        initializePaidLlmBeans();
    }

    private void initializeFreeLlmBeans() {
        SourceConfig freeSourceConfig = llmConfigService.getModelConfigBySource(Source.FREE);
        if (freeSourceConfig==null) {
            log.error("No free LLM source found for initializing bean creation.");
            return;
        }
        List<ModelConfig> modelConfigs = freeSourceConfig.getModels();
        if (modelConfigs==null || modelConfigs.isEmpty()) {
            log.error("No free LLM models available for bean creation.");
            return;
        }
        for (ModelConfig config : modelConfigs) {
            if (config.getType().contains(LlmCapability.EMBEDDING.getValue())){
                log.info("Creating bean for free OpenAiEmbeddingModel: {} ",config.getId());
                //embedding bean.
                //for attaching the bean to vector store, either make a logic for dynamic bean creation or make
                //a bean of embedding defaulted to oen fo the running embedding model. -> bge-large-embed
                genericApplicationContext.registerBean(
                        config.getId(),
                        OpenAiEmbeddingModel.class,
                        () -> new OpenAiEmbeddingModel(buildEmbeddingOpenAiApi(config))
                );

                //since its embedding also create a vector store bean
                genericApplicationContext.registerBean(
                        "vector-"+config.getId(),
                        VectorStore.class,
                        () -> SimpleVectorStore.builder(
                                genericApplicationContext.getBean(
                                        config.getId(),
                                        OpenAiEmbeddingModel.class
                                )
                        ).build()
                );
            } else {
                log.info("Creating bean for free ChatClient with name: {}",config.getId());
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

    private void initializePaidLlmBeans() {
        SourceConfig paidSourceConfig = llmConfigService.getModelConfigBySource(Source.PAID);
        if (paidSourceConfig==null) {
            log.error("No paid LLM source found for initializing bean creation.");
            return;
        }
        List<ModelConfig> modelConfigs = paidSourceConfig.getModels();
        if (modelConfigs==null || modelConfigs.isEmpty()) {
            log.error("No paid LLM models available for bean creation.");
            return;
        }
        for (ModelConfig config : modelConfigs) {
            switch (config.getProvider()) {
                case "openai" : {
                    log.info("OpenAi paid provider selected. Model {}",config.getModelName());
                    if (config.getType().contains(LlmCapability.EMBEDDING.getValue())){
                        log.info("Creating bean for paid OpenAiEmbeddingModel: {} ",config.getId());
                        genericApplicationContext.registerBean(
                                config.getId(),
                                OpenAiEmbeddingModel.class,
                                () -> new OpenAiEmbeddingModel(buildEmbeddingOpenAiApi(config))
                        );

                        genericApplicationContext.registerBean(
                                "vector-"+config.getId(),
                                VectorStore.class,
                                () -> SimpleVectorStore.builder(
                                        genericApplicationContext.getBean(
                                                config.getId(),
                                                OpenAiEmbeddingModel.class
                                        )
                                ).build()
                        );
                    } else {
                        log.info("Creating bean for paid ChatClient with name: {}",config.getId());
                        genericApplicationContext.registerBean(
                                config.getId(),
                                ChatClient.class,
                                () -> ChatClient.create(
                                        buildChatOpenAiModel(config)
                                )
                        );
                    }
                    break;
                }
                case "anthropic" : {
                    break;
                }
                default: log.info("Provider {} is currently not supported.",config.getProvider());
            }
        }
    }

    private OpenAiApi buildChatOpenAiApi(ModelConfig config){
        return OpenAiApi.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .completionsPath(config.getApiPath())
                .restClientBuilder(buildRestClientBuilder())
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

    private RestClient.Builder buildRestClientBuilder() {

        return RestClient.builder()
                .requestFactory(clientHttpRequestFactory());
    }

    private SimpleClientHttpRequestFactory clientHttpRequestFactory() {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(1000_000);

        return factory;
    }
}
