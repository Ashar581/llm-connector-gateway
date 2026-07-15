package com.an.llm.connector.gateway.controller.ai;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.enums.IngestionMode;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.ai.RagServiceV3;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v3/rag")
public class RagControllerV3 extends BaseApiDelegate {
    private final RagServiceV3 ragServiceV3;

    @PostMapping("ask")
    public ResponseEntity<@NonNull ApiResponseBody<String>> ragChat(@ModelAttribute @Valid LlmConnectorRequest request) {
        return sendSuccessfulApiResponse(ragServiceV3.chat(request, IngestionMode.CHAT),"RAG communication completed.");
    }

    @PostMapping(value = "/stream/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<@NonNull ApiResponseBody<String>> ragStreamChat(@ModelAttribute @Valid LlmConnectorRequest request) {
        return ragServiceV3.chatStream(request, IngestionMode.CHAT).map(chunk -> {
            ApiResponseBody<String> response = new ApiResponseBody<>();

            response.setStatus(true);
            response.setCode(200);
            response.setMessage("RAG response streaming chunk.");
            response.setData(chunk);

            return response;
        });
    }
}
