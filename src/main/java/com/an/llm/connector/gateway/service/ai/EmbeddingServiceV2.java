package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingServiceV2 {
    private final AiBeanFactory aiBeanFactory;

    public float[] embed(LlmConnectorRequest request){
        if (request==null) throw new NullException("Api request body is missing.");
        if (request.getQuery()==null || request.getQuery().isBlank()) return new float[0];

        return aiBeanFactory.getOpenAiEmbeddingModel(request.getSource(), request.getType(), request.getModel())
                .embed(request.getQuery());
    }
}
