package com.an.llm.connector.gateway.controller;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.EmbeddingServiceV2;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/llm/v2/embed")
@RequiredArgsConstructor
public class EmbeddingControllerV2 extends BaseApiDelegate {
    private final EmbeddingServiceV2 embeddingServiceV2;

    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<float[]>> generateEmbed(@RequestBody LlmConnectorRequest request){
        return sendSuccessfulApiResponse(embeddingServiceV2.embed(request),"Generated embedding successfully.");
    }
}
