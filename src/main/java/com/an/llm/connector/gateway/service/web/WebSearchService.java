package com.an.llm.connector.gateway.service.web;

import com.an.llm.connector.gateway.model.web.SearchRequest;
import com.an.llm.connector.gateway.model.web.SearchResponse;
import com.an.llm.connector.gateway.model.web.SearchResult;
import com.an.llm.connector.gateway.model.web.WebDocument;
import com.an.llm.connector.gateway.service.web.downloader.JsoupWebPageDownloader;
import com.an.llm.connector.gateway.service.web.search.SearXNGSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchService {
    private final SearXNGSearch searXNGSearch;
    private final JsoupWebPageDownloader webPageDownloader;

    public List<WebDocument> search(SearchRequest request) {
        SearchResponse response = searXNGSearch.search(request);

        List<String> urls = response.results()
                .stream()
                .map(SearchResult::url)
                .toList();

        return webPageDownloader.download(urls);
    }
}
