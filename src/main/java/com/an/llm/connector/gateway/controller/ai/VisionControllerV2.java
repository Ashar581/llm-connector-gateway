package com.an.llm.connector.gateway.controller.ai;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.classification.ClassificationResponse;
import com.an.llm.connector.gateway.service.ai.VisionServiceV2;
import com.an.llm.connector.gateway.service.classification.ClassificationOrchestrator;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v2/vl")
public class VisionControllerV2 extends BaseApiDelegate {
    private final VisionServiceV2 visionServiceV2;
    private final ClassificationOrchestrator classificationOrchestrator;

    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<String>> vison(@Valid @ModelAttribute LlmConnectorRequest request){
        return sendSuccessfulApiResponse(visionServiceV2.visionPrompt(request));
    }
    //this is still having v1 since no changes are needed yet.
    @PostMapping("classify")
    public ResponseEntity<@NonNull ApiResponseBody< @NonNull ClassificationResponse>> classify(@ModelAttribute @Valid LlmConnectorRequest request) {
        try {
            return sendSuccessfulApiResponse(classificationOrchestrator.process(request));
        } catch (Exception e) {
            throw new ApiFallbackException(e.getMessage());
        }
    }
}
