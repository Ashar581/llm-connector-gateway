package com.an.llm.connector.gateway.controller.web;

import com.an.llm.connector.gateway.base.ApiExceptionBody;
import com.an.llm.connector.gateway.base.ApiResponseBody;
import com.an.llm.connector.gateway.base.BaseApiDelegate;
import com.an.llm.connector.gateway.enums.ScrapingResponseChoice;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.AiRequest;
import com.an.llm.connector.gateway.model.web.SearchRequest;
import com.an.llm.connector.gateway.service.web.WebSearchService;
import com.an.llm.connector.gateway.service.web.search.FirecrawlService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firecrawl.models.Document;
import com.firecrawl.models.ScrapeOptions;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/llm/v1/web")
@RequiredArgsConstructor
public class WebController extends BaseApiDelegate {
    private final WebSearchService webSearchService;
    private final FirecrawlService firecrawlService;

    @PostMapping("search")
    public ResponseEntity<@NonNull ApiResponseBody<Object>> search(@Valid @RequestBody SearchRequest request) {
        return sendSuccessfulApiResponse(webSearchService.search(request),"Ai response generated successfully.");
    }

    @GetMapping("/scrape")
    public ResponseEntity<@NonNull ApiResponseBody<Object>> scrape(@RequestParam("url")String url, @RequestParam("response") ScrapingResponseChoice choice) {
        Document document = firecrawlService.scrape(url, ScrapeOptions.builder()
                .formats(List.of((Object) choice))
                .onlyMainContent(true)
                .build());

        switch (choice) {
            case html -> {
                return sendSuccessfulApiResponse(document.getHtml(),"Website scraped response.");
            }
            case markdown -> {
                return sendSuccessfulApiResponse(document.getMarkdown(),"Website scraped response.");
            }
        }
        throw new ApiFallbackException("Invalid scraping choice.");
    }
}
