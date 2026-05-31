package com.an.llm.connector.gateway.controller.ai;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.classification.ClassificationResponse;
import com.an.llm.connector.gateway.service.ai.VisionService;
import com.an.llm.connector.gateway.service.classification.ClassificationOrchestrator;
import com.an.llm.connector.gateway.service.v2.VisionServiceV2;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v1/vl")
public class VisionController extends BaseApiDelegate {
    private final VisionService visionService;
    private final VisionServiceV2 visionServiceV2;
    private final ClassificationOrchestrator classificationOrchestrator;

    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<String>> vison(@Valid @ModelAttribute LlmConnectorRequest request){
//        return sendSuccessfulApiResponse(visionService.visionPrompt(request));
        return sendSuccessfulApiResponse(visionServiceV2.visionPrompt(request));
    }
    @PostMapping("classify")
    public ResponseEntity<@NonNull ApiResponseBody< @NonNull ClassificationResponse>> classify(@ModelAttribute @Valid LlmConnectorRequest request) {
        try {
            return sendSuccessfulApiResponse(classificationOrchestrator.process(request));
        } catch (Exception e) {
            throw new ApiFallbackException(e.getMessage());
        }
    }

}
