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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BeanConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    @Bean
    public RestClient restClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
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

    //changed the qualifier from bge-large-embedding to bge-large-embed
    //This is just a quick fix for testing -> will change it to strategy pattern.
    @Bean
    public VectorStore vectorStore(@Qualifier("bge-large-embed") OpenAiEmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
