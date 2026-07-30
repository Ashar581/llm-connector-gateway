package com.an.llm.connector.gateway.service.web;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.model.web.SearchRequest;
import com.an.llm.connector.gateway.model.web.SearchResponse;
import com.an.llm.connector.gateway.model.web.SearchResult;
import com.an.llm.connector.gateway.model.web.WebDocument;
import com.an.llm.connector.gateway.service.LlmConfigService;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import com.an.llm.connector.gateway.service.tokenize.TokenChunkService;
import com.an.llm.connector.gateway.service.web.downloader.JsoupWebPageDownloader;
import com.an.llm.connector.gateway.service.web.search.SearXNGSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchService {
    private final SearXNGSearch searXNGSearch;
    private final JsoupWebPageDownloader webPageDownloader;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;
    private final TokenChunkService tokenChunkService;
    private final LlmConfigService llmConfigService;
    private final AiBeanFactory aiBeanFactory;

    public List<WebDocument> search(SearchRequest request) {
        SearchResponse response = searXNGSearch.search(request);

        List<String> urls = response.results()
                .stream()
                .limit(request.maxResults()==null? 5 : request.maxResults())
                .map(SearchResult::url)
                .toList();

        return webPageDownloader.download(urls);
    }

    public String search(SearchRequest searchRequest, LlmConnectorRequest request) {
        SearchResponse response = searXNGSearch.search(searchRequest);

        List<String> urls = response.results()
                .stream()
                .limit(searchRequest.maxResults()==null? 5 : searchRequest.maxResults())
                .map(SearchResult::url)
                .toList();

         List<WebDocument> webDocuments = webPageDownloader.download(urls);

         if (webDocuments == null || webDocuments.isEmpty()) {
             return "";
         }

         ModelConfig modelConfig = llmConfigService.getModelConfig(
                 request.getSource(),
                 request.getType(),
                 request.getModel()
         );

         ChatClient client = aiBeanFactory.getChatClient(
                 request.getSource(),
                 request.getType(),
                 request.getModel()
         );

         int allowedTokensPerSlot = (int)(((double) modelConfig.getContext() / modelConfig.getParallelExecution()) * 0.85);

        List<String> chunkedTokens = tokenChunkService.chunk(
                webDocuments.getFirst().text(),
                modelConfig.getBaseUrl(),
                allowedTokensPerSlot
        );

        List<CompletableFuture<String>> futures = chunkedTokens.stream()
                .map(chunk -> CompletableFuture.supplyAsync(() -> {
                    long llmStart = System.currentTimeMillis();

                    ChatResponse llmResponse = client.prompt()
                            .system("""
                                    Summarize the following content while preserving
                                    all important facts.
                                    """)
                            .user("""
                                    WEBSITE SCRAPED DATA
                                    
                                    %s
                                    """.formatted(chunk))
                            .call()
                            .chatResponse();

                    long llmCompletionTimeMs = System.currentTimeMillis() - llmStart;

                    try {
                        assert llmResponse != null;
                        systemConsumptionStatsSvc.add(llmResponse, request, llmCompletionTimeMs);
                    } catch (Exception e) {
                        log.error("Error recording chunk consumption stats.", e);
                    }

                    return Objects.requireNonNull(llmResponse.getResult()).getOutput().getText();

                })).toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.joining("\n\n"));
    }

    public String search(String internetQuery,SearchRequest searchRequest, LlmConnectorRequest request) {
        if (request.getEnablePrivateMode()) return "Internet access permission was switched off.";

        SearchResponse response = searXNGSearch.search(internetQuery);

        List<String> urls = response.results()
                .stream()
                .limit(searchRequest.maxResults()==null? 5 : searchRequest.maxResults())
                .map(SearchResult::url)
                .toList();

        List<WebDocument> webDocuments = webPageDownloader.download(urls);

        if (webDocuments == null || webDocuments.isEmpty()) {
            return "";
        }

        ModelConfig modelConfig = llmConfigService.getModelConfig(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        ChatClient client = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        int allowedTokensPerSlot = (int)(((double) modelConfig.getContext() / modelConfig.getParallelExecution()) * 0.85);

        List<String> chunkedTokens = tokenChunkService.chunk(
                webDocuments.getFirst().text(),
                modelConfig.getBaseUrl(),
                allowedTokensPerSlot
        );

        List<CompletableFuture<String>> futures = chunkedTokens.stream()
                .map(chunk -> CompletableFuture.supplyAsync(() -> {
                    long llmStart = System.currentTimeMillis();

                    ChatResponse llmResponse = client.prompt()
                            .system("""
                                    Summarize the following content while preserving
                                    all important facts.
                                    """)
                            .user("""
                                    WEBSITE SCRAPED DATA
                                    
                                    %s
                                    """.formatted(chunk))
                            .call()
                            .chatResponse();

                    long llmCompletionTimeMs = System.currentTimeMillis() - llmStart;

                    try {
                        assert llmResponse != null;
                        systemConsumptionStatsSvc.add(llmResponse, request, llmCompletionTimeMs);
                    } catch (Exception e) {
                        log.error("Error recording chunk consumption stats.", e);
                    }

                    return Objects.requireNonNull(llmResponse.getResult()).getOutput().getText();

                })).toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.joining("\n\n"));
    }
}
