package com.an.llm.connector.gateway.mapper;

import com.an.llm.connector.gateway.dto.web.SearXNGResponse;
import com.an.llm.connector.gateway.dto.web.SearXNGResult;
import com.an.llm.connector.gateway.model.web.SearchResponse;
import com.an.llm.connector.gateway.model.web.SearchResult;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class SearXNGMapper {

    public SearchResponse toSearchResponse(SearXNGResponse response) {
        if (response == null || response.results() == null) {
            return new SearchResponse(Collections.emptyList());
        }

        List<SearchResult> results = response.results()
                .stream()
                .map(this::toSearchResult)
                .toList();

        return new SearchResponse(results);
    }

    private SearchResult toSearchResult(SearXNGResult result) {
        return new SearchResult(
                result.title(),
                result.url(),
                result.content(),
                result.score()
        );
    }

}
