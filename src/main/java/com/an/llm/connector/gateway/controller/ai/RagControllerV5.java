package com.an.llm.connector.gateway.controller.ai;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.enums.IngestionMode;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.ai.RagServiceV5;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v5/rag")
public class RagControllerV5 extends BaseApiDelegate {
    private final RagServiceV5 ragServiceV5;

    @PostMapping("ask")
    public ResponseEntity<@NonNull ApiResponseBody<String>> ragChat(@ModelAttribute @Valid LlmConnectorRequest request) {
        return sendSuccessfulApiResponse(ragServiceV5.chat(request, IngestionMode.CHAT), "RAG communication completed.");
    }

    @PostMapping(value = "/stream/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<@NonNull ApiResponseBody<String>> ragStreamChat(@ModelAttribute @Valid LlmConnectorRequest request) {

        return ragServiceV5.chatStream(request, IngestionMode.CHAT)
                .map(chunk -> {
                    ApiResponseBody<String> response = new ApiResponseBody<>();
                    response.setStatus(true);
                    response.setCode(200);
                    response.setMessage("RAG response streaming chunk.");
                    response.setData(chunk);
                    return response;
                });
    }
}