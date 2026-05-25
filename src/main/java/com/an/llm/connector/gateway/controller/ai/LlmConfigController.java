package com.an.llm.connector.gateway.controller.ai;

import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.service.LlmConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/config")
public class LlmConfigController extends BaseApiDelegate {
    private final LlmConfigService llmConfigService;

    @GetMapping("model")
    public ResponseEntity<?> getAvailableModels(){
        return sendSuccessfulApiResponse(llmConfigService.getAvailableModels(),"Successfully retrieved available models.");
    }
}
