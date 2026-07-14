package com.an.llm.connector.gateway.controller.ai;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.service.ai.DocumentIngestionService;
import com.an.llm.connector.gateway.service.ai.RagService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Deprecated
@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/rag")
public class RagController  extends BaseApiDelegate {
    private final DocumentIngestionService documentIngestionService;
    private final RagService ragService;

    @PostMapping("ingest/file")
    public ResponseEntity<@NonNull ApiResponseBody<String>> ingestDocument(@RequestParam("file")MultipartFile file){
        return sendCreatedApiResponse(documentIngestionService.ingest(file), "File ingestion completed.");
    }

    @GetMapping("simple/ask")
    public ResponseEntity<@NonNull ApiResponseBody<String>> simpleRagAsk(@RequestParam("query")String query){
        return sendSuccessfulApiResponse(ragService.ask(query),"Data retrieved successfully.");

    }
}
