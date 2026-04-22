package com.an.llm.connector.gateway.controller;

import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.service.AiService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;


@Deprecated
@RestController
@RequestMapping("api/llm/v1")
@RequiredArgsConstructor
public class AiController extends BaseApiDelegate {
    private final AiService aiService;

    @GetMapping("/ask")
    public ResponseEntity<@NonNull ApiResponseBody<String>>generate(@RequestParam("query")String query){
        return sendSuccessfulApiResponse(aiService.ask(query));
    }

    @GetMapping(value = "/stream/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<@NonNull Flux<@NonNull String>> generateStream(@RequestParam("query")String query){
        return new ResponseEntity<>(aiService.askStream(query), HttpStatus.OK);
    }

}
