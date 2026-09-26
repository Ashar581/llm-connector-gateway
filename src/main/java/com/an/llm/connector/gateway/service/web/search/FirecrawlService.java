package com.an.llm.connector.gateway.service.web.search;

import com.firecrawl.client.FirecrawlClient;
import com.firecrawl.models.AgentOptions;
import com.firecrawl.models.AgentStatusResponse;
import com.firecrawl.models.BatchScrapeJob;
import com.firecrawl.models.BatchScrapeOptions;
import com.firecrawl.models.BrowserCreateResponse;
import com.firecrawl.models.BrowserDeleteResponse;
import com.firecrawl.models.BrowserExecuteResponse;
import com.firecrawl.models.BrowserListResponse;
import com.firecrawl.models.ConcurrencyCheck;
import com.firecrawl.models.CrawlJob;
import com.firecrawl.models.CrawlOptions;
import com.firecrawl.models.CrawlResponse;
import com.firecrawl.models.CreditUsage;
import com.firecrawl.models.Document;
import com.firecrawl.models.MapData;
import com.firecrawl.models.MapOptions;
import com.firecrawl.models.ScrapeOptions;
import com.firecrawl.models.SearchData;
import com.firecrawl.models.SearchOptions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class FirecrawlService {
    private final FirecrawlClient client;

    public Document scrape(String url) {
        return client.scrape(url);
    }


    public Document scrape(String url, ScrapeOptions options) {
        return client.scrape(url, options);
    }


    public CompletableFuture<Document> scrapeAsync(String url, ScrapeOptions options) {
        return client.scrapeAsync(url, options);
    }

    /**
     * Start a crawl and wait for completion.
     */
    public CrawlJob crawl(String url, CrawlOptions options) {
        return client.crawl(url, options);
    }

    public CrawlResponse startCrawl(String url, CrawlOptions options) {
        return client.startCrawl(url, options);
    }

    /**
     * Get the current status of a crawl.
     */
    public CrawlJob getCrawlStatus(String crawlId) {
        return client.getCrawlStatus(crawlId);
    }

    /**
     * Cancel a running crawl.
     */
    public Map<String, Object> cancelCrawl(String crawlId) {
        return client.cancelCrawl(crawlId);
    }

    public MapData map(String url) {
        return client.map(url);
    }


    public MapData map(String url, MapOptions options) {
        return client.map(url, options);
    }

    public SearchData search(String query) {
        return client.search(query);
    }


    public SearchData search(String query, SearchOptions options) {
        return client.search(query, options);
    }

    public BatchScrapeJob batchScrape(List<String> urls, BatchScrapeOptions options) {
        return client.batchScrape(urls, options);
    }

    public ConcurrencyCheck getConcurrency() {
        return client.getConcurrency();
    }


    public CreditUsage getCreditUsage() {
        return client.getCreditUsage();
    }

    public BrowserCreateResponse browser(int ttl, int activityTtl, boolean persist) {
        return client.browser(
                ttl,
                activityTtl,
                persist
        );
    }


    public BrowserExecuteResponse browserExecute(String sessionId, String code, String language, int timeout) {
        return client.browserExecute(
                sessionId,
                code,
                language,
                timeout
        );
    }


    public BrowserListResponse listBrowsers(String status) {
        return client.listBrowsers(status);
    }


    public BrowserDeleteResponse deleteBrowser(String sessionId) {
        return client.deleteBrowser(sessionId);
    }

    public BrowserExecuteResponse interact(String scrapeJobId, String code, String language, int timeout) {
        return client.interact(
                scrapeJobId,
                code,
                language,
                timeout
        );
    }

    public BrowserDeleteResponse stopInteraction(String scrapeJobId) {
        return client.stopInteractiveBrowser(scrapeJobId);
    }
}