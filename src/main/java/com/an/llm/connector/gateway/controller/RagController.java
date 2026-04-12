package com.an.llm.connector.gateway.controller;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.service.DocumentIngestionService;
import com.an.llm.connector.gateway.service.SimpleRagService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/rag")
public class RagController  extends BaseApiDelegate {
    private final DocumentIngestionService documentIngestionService;
    private final SimpleRagService simpleRagService;

    @PostMapping("ingest/file")
    public ResponseEntity<@NonNull ApiResponseBody<String>> ingestDocument(@RequestParam("file")MultipartFile file){
        return sendCreatedApiResponse(documentIngestionService.ingest(file), "File ingestion completed.");
    }

    @GetMapping("simple/ask")
    public ResponseEntity<@NonNull ApiResponseBody<String>> simpleRagAsk(@RequestParam("query")String query){
        return sendSuccessfulApiResponse(simpleRagService.ask(query),"Data retrieved successfully.");

    }
}
