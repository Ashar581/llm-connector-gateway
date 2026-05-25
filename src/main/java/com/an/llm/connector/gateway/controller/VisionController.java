package com.an.llm.connector.gateway.controller;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.VisionService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/vl")
public class VisionController extends BaseApiDelegate {
    private final VisionService visionService;

    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<String>> vison(@ModelAttribute LlmConnectorRequest request){
        return sendSuccessfulApiResponse(visionService.visionPrompt(request));
    }

}
