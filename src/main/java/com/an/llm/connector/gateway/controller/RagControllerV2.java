package com.an.llm.connector.gateway.controller;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.DocumentIngestionService;
import com.an.llm.connector.gateway.service.SimpleRagService;
import com.an.llm.connector.gateway.service.SimpleRagServiceV2;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v2/rag")
public class RagControllerV2 extends BaseApiDelegate {
    private final DocumentIngestionService documentIngestionService;
    private final SimpleRagServiceV2 simpleRagServiceV2;

    @PostMapping("ingest/file")
    public ResponseEntity<@NonNull ApiResponseBody<String>> ingestDocument(@RequestParam("file") MultipartFile file){
        return sendCreatedApiResponse(documentIngestionService.ingest(file), "File ingestion completed.");
    }

    @PostMapping("simple/ask")
    public ResponseEntity<@NonNull ApiResponseBody<String>> simpleRagAsk(@RequestBody LlmConnectorRequest request){
        return sendSuccessfulApiResponse(simpleRagServiceV2.ask(request),"Data retrieved successfully.");

    }
}
