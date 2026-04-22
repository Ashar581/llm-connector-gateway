package com.an.llm.connector.gateway.service;

import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.AiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Deprecated
@Service
//@RequiredArgsConstructor
public class EmbeddingService {
    @Autowired
    @Qualifier("bge-large-embed")
    private  EmbeddingModel embeddingModel;

    public float[] embed(AiRequest request){
        if (request==null) throw new NullException("Api request body is missing.");
        if (request.getQuery()==null || request.getQuery().isBlank()) return new float[0];
        return embeddingModel
                .embed(request.getQuery());
    }
}
