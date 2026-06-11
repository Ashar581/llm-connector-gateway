package com.an.llm.connector.gateway.controller.v3;


import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v3/vl")
public class VisionControllerV3 extends BaseApiDelegate {
    private final VisionServiceV3 visionServiceV3;

    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<String>> vision(
            @Valid @ModelAttribute LlmConnectorRequest request
    ) {
        return sendSuccessfulApiResponse(visionServiceV3.visionPrompt(request));
    }
}