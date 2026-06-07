package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceV2 {
    private final AiBeanFactory aiBeanFactory;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;

    //single query not multiple. (not List.of("A","B") but "A B")
    public float[] embed(LlmConnectorRequest request){
        if (request==null) throw new NullException("Api request body is missing.");
        if (request.getQuery()==null || request.getQuery().isBlank()) return new float[0];

        validateAllowedType(request);

        long start = System.currentTimeMillis();

        EmbeddingResponse response = aiBeanFactory.getOpenAiEmbeddingModel(request.getSource(), request.getType(), request.getModel())
                .call(new EmbeddingRequest(
                        List.of(request.getQuery()),
                        EmbeddingOptions.builder().build()
                ));

        long completionTimeMs = System.currentTimeMillis() - start;

        //async service for generating the stats.
        try {
            systemConsumptionStatsSvc.add(response, request, completionTimeMs);
        } catch (Exception e){
            log.error("Error recording non-stream consumption tokens stats.",e);
        }

        return response.getResult().getOutput();
    }

    private void validateAllowedType(LlmConnectorRequest request){
        LlmCapability type = LlmCapability.getFromValue(request.getType());
        //not allowed list
        Set<LlmCapability> allowedTypes = Set.of(LlmCapability.EMBEDDING);

        if (!allowedTypes.contains(type)) throw new ApiFallbackException("The requested type is not supported by this endpoint.");
    }
}
