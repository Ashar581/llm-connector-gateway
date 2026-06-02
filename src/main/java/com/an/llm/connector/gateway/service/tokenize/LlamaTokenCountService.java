package com.an.llm.connector.gateway.service.tokenize;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlamaTokenCountService {
    private final RestClient restClient;

    public int countTokens(String llamaBaseUrl, String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        TokenizeResponse response = restClient
                .post()
                .uri(llamaBaseUrl + "/tokenize")
                .body(Map.of(
                        "content", text,
                        "add_special", false
                ))
                .retrieve()
                .body(TokenizeResponse.class);

        return response == null || response.tokens() == null ? 0 : response.tokens().size();
    }

    public record TokenizeResponse(List<Integer> tokens) {
    }
}
