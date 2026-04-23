package com.an.llm.connector.gateway.controller;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.model.AiRequest;
import com.an.llm.connector.gateway.service.EmbeddingService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Deprecated
@RestController
@RequestMapping("api/llm/v1/embed")
@RequiredArgsConstructor
public class EmbeddingController extends BaseApiDelegate {
    private final EmbeddingService embeddingService;

    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<float[]>> generateEmbed(@RequestBody AiRequest request){
        return sendSuccessfulApiResponse(embeddingService.embed(request),"Generated embedding successfully.");
    }
}
