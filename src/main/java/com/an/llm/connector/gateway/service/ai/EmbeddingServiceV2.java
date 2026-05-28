package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmbeddingServiceV2 {
    private final AiBeanFactory aiBeanFactory;

    public float[] embed(LlmConnectorRequest request){
        if (request==null) throw new NullException("Api request body is missing.");
        if (request.getQuery()==null || request.getQuery().isBlank()) return new float[0];

        validateAllowedType(request);

        return aiBeanFactory.getOpenAiEmbeddingModel(request.getSource(), request.getType(), request.getModel())
                .embed(request.getQuery());
    }

    private void validateAllowedType(LlmConnectorRequest request){
        LlmCapability type = LlmCapability.getFromValue(request.getType());
        //not allowed list
        Set<LlmCapability> allowedTypes = Set.of(LlmCapability.EMBEDDING);

        if (!allowedTypes.contains(type)) throw new ApiFallbackException("The requested type is not supported by this endpoint.");
    }
}
