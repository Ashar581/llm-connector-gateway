package com.an.llm.connector.gateway.service.web.search;

import com.an.llm.connector.gateway.dto.web.SearXNGResponse;
import com.an.llm.connector.gateway.exception.WebSearchException;
import com.an.llm.connector.gateway.mapper.SearXNGMapper;
import com.an.llm.connector.gateway.model.web.SearchRequest;
import com.an.llm.connector.gateway.model.web.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearXNGSearch {
    private final RestClient restClient;
    private final SearXNGMapper searXNGMapper;

    public SearchResponse search(SearchRequest request) {
        try {
            log.info("Searching web browser");
            SearXNGResponse response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("localhost")
                            .port(8888)
                            .path("/search")
                            .queryParam("q", request.query())
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(SearXNGResponse.class);

            return searXNGMapper.toSearchResponse(response);

        } catch (Exception e) {
            log.error("Error while using SearXNG for web search.",e);
            throw new WebSearchException("Unable to search using SearXNG");
        }

    }

}
