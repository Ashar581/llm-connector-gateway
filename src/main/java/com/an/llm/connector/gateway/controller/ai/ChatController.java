package com.an.llm.connector.gateway.controller.ai;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.ai.ChatClientService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/v2")
public class ChatController extends BaseApiDelegate {
    private final ChatClientService chatClientService;

    @PostMapping("/ask")
    public ResponseEntity<@NonNull ApiResponseBody<String>> generate(@RequestBody LlmConnectorRequest request){
        return sendSuccessfulApiResponse(chatClientService.ask(request));
    }

    @PostMapping(value = "/stream/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<@NonNull Flux<@NonNull String>> generateStream(@RequestBody LlmConnectorRequest request){
        return new ResponseEntity<>(chatClientService.askStream(request), HttpStatus.OK);
    }

}
