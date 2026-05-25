package com.an.llm.connector.gateway.controller.agent;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.model.AiRequest;
import com.an.llm.connector.gateway.service.agent.AgentService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("api/llm/v1/agent")
@RequiredArgsConstructor
public class AgentController extends BaseApiDelegate {
    private final AgentService agentService;

    @PostMapping("")
    public ResponseEntity<@NonNull ApiResponseBody<String>> generate(@RequestBody @Valid AiRequest aiRequest) {
        return sendSuccessfulApiResponse(agentService.generate(aiRequest),"Ai response generated successfully.");
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<@NonNull Flux<@NonNull String>> stream(@RequestBody @Valid AiRequest aiRequest){
        return new ResponseEntity<>(agentService.stream(aiRequest), HttpStatus.OK);
    }
}
