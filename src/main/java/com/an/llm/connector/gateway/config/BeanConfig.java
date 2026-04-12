package com.an.llm.connector.gateway.config;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BeanConfig {
    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    @Bean
    public TokenTextSplitter tokenTextSplitter(){
        return TokenTextSplitter.builder()
                .withEncodingType(EncodingType.CL100K_BASE)
                .withChunkSize(200)
                .withMinChunkLengthToEmbed(100)
                .withMinChunkSizeChars(100)
                .withMaxNumChunks(100)
                .withKeepSeparator(true)
                .build();
    }

    /**
     * I will be removing it post dynamic configurations. As of now this stays.
     * @return {@link OpenAiEmbeddingModel} bean
     */
    @Primary
    @Bean(name = "bge-large-embedding")
    public OpenAiEmbeddingModel embeddingModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("http://localhost:8081")
                .embeddingsPath("/v1/embeddings")
                .apiKey("ashar581")
                .build();

        return new OpenAiEmbeddingModel(openAiApi);
    }

    /**
     * For paid open ai model
     * @return {@link OpenAiEmbeddingModel}
     */
    @Bean(name = "open_ai_embedding")
    public OpenAiEmbeddingModel openAiEmbeddingModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("http://localhost:8081")
                .embeddingsPath("/v1/embeddings")
                .apiKey("ashar581")
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model("text-embedding-model")
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }


    @Bean
    public VectorStore vectorStore(@Qualifier("bge-large-embedding") OpenAiEmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
