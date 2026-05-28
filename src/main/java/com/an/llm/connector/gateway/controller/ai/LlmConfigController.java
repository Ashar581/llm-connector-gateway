package com.an.llm.connector.gateway.controller.ai;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.service.LlmConfigService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/config")
public class LlmConfigController extends BaseApiDelegate {
    private final LlmConfigService llmConfigService;

    @GetMapping("model")
    public ResponseEntity<?> getAvailableModels(){
        return sendSuccessfulApiResponse(llmConfigService.getAvailableModels(),"Successfully retrieved available models.");
    }

    @GetMapping("types")
    public ResponseEntity<@NonNull ApiResponseBody<List<String>>> getLlmTypes(){
        return sendSuccessfulApiResponse(llmConfigService.getTypes(),"Successfully retrieved available LLM types.");
    }
}
