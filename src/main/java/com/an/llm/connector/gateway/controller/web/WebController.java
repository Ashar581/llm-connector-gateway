package com.an.llm.connector.gateway.controller.web;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.model.AiRequest;
import com.an.llm.connector.gateway.model.web.SearchRequest;
import com.an.llm.connector.gateway.service.web.WebSearchService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/llm/v1/web")
@RequiredArgsConstructor
public class WebController extends BaseApiDelegate {
    private final WebSearchService webSearchService;

    @PostMapping("search")
    public ResponseEntity<@NonNull ApiResponseBody<Object>> search(@Valid @RequestBody SearchRequest request) {
        return sendSuccessfulApiResponse(webSearchService.search(request),"Ai response generated successfully.");
    }
}
