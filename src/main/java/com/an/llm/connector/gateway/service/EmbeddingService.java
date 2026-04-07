package com.an.llm.connector.gateway.service;

import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.AiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;

    public float[] embed(AiRequest request){
        if (request==null) throw new NullException("Api request body is missing.");
        if (request.getQuery()==null || request.getQuery().isBlank()) return new float[0];
        return embeddingModel
                .embed(request.getQuery());
    }
}
