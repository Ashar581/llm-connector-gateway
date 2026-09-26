package com.an.llm.connector.gateway.service.web.tool;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.web.search.FirecrawlService;
import com.an.llm.connector.gateway.util.AppUtils;
import com.firecrawl.models.CrawlJob;
import com.firecrawl.models.CrawlOptions;
import com.firecrawl.models.Document;
import com.firecrawl.models.MapData;
import com.firecrawl.models.MapOptions;
import com.firecrawl.models.SearchData;
import com.firecrawl.models.SearchOptions;
import com.firecrawl.models.ScrapeOptions;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Slf4j
@RequiredArgsConstructor
public class FirecrawlTools {
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int DEFAULT_WEBSITE_SEARCH_LIMIT = 10;
    private static final int MAX_CRAWL_PAGES = 20;
    private static final int MAX_TOOL_CALLS = 5;

    private final FirecrawlService firecrawlService;
    private final LlmConnectorRequest llmConnectorRequest;

    /**
     * Search the entire web.
     *
     * This is the primary tool the LLM should use when it needs
     * current or external information.
     */
    @Tool(description = """
        Search the web for information.

        Use this when you need current information, recent information,
        facts from websites, news, research, financial information,
        documentation, or information that may not be in your knowledge.

        Do not use this when you already have a specific URL to read.

        Input:
        - query: a clear description of what you are looking for

        Returns relevant web search results with URLs and page content.
        """)
    public List<Map<String, Object>> searchWeb(@NonNull String query) {
        log.info("Searching web with query {}",query);

        List<Map<String,Object>> search = firecrawlService.search(
                        query,
                        SearchOptions.builder()
                                .scrapeOptions(
                                        ScrapeOptions.builder()
                                                .formats(List.of("markdown"))
                                                .build()
                                )
                                .limit(DEFAULT_SEARCH_LIMIT)
                                .build()
                ).getWeb()
                .stream()
                .filter(result -> {
                    Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");

                    if (metadata == null) {
                        return true;
                    }

                    Object statusCode = metadata.get("statusCode");

                    return !(statusCode instanceof Number
                            && (((Number) statusCode).intValue() == 401 || ((Number) statusCode).intValue() == 403));
                })
                .limit(2)
                .toList();

        search.forEach(update -> {
                    if (update.containsKey("markdown")) {
                        update.put("markdown", AppUtils.markdownToCliString(String.valueOf(update.get("markdown"))));
                    }
                });

        return search;
    }


    /**
     * Search within a specific website.
     *
     * Uses Firecrawl's site mapping/search capability rather than
     * performing a general web search.
     */
    @Tool(description = """
        Search for information inside a specific website.

        Use this when the user wants information from a particular
        website or domain.

        Input:
        - website: the website URL to search
        - query: what information you are looking for

        Returns relevant URLs found within that website.

        After finding a relevant URL, use scrape_web_page to read
        the actual page contents.
        """)
    public MapData searchWebsite(@NonNull String website, @NonNull String query) {

        return firecrawlService.map(
                website,
                MapOptions.builder()
                        .search(query)
                        .limit(DEFAULT_WEBSITE_SEARCH_LIMIT)
                        .build()
        );
    }

    /**
     * Read a single web page.
     *
     * This should be used after search results provide a URL,
     * or when the user directly provides a URL.
     */
    @Tool(description = """
        Read and extract the main content from a web page.

        Use this when you have a specific URL and need the contents
        of that page.

        This is the preferred tool for reading:
        - articles
        - documentation
        - reports
        - company pages
        - financial pages
        - research pages
        - blog posts

        Input:
        - url: the complete URL of the page to read

        Returns the main page content as markdown.
        """)
    public Document scrapeWebPage(@NonNull String url) {

        return firecrawlService.scrape(
                url,
                ScrapeOptions.builder()
                        .formats(List.of((Object) "markdown"))
                        .onlyMainContent(true)
                        .build()
        );
    }

    /**
     * Crawl a website.
     *
     * The model does not control the page limit. This prevents
     * a small model from accidentally requesting an enormous crawl.
     */
    @Tool(description = """
        Read multiple pages from a website.

        Use this when the information you need is spread across
        several pages of the same website.

        Good use cases:
        - understanding a company's website
        - researching documentation
        - finding information across multiple related pages
        - researching a website when the exact page is unknown

        Input:
        - website: the starting URL of the website

        The crawl is automatically limited to a safe number of pages.

        Returns the scraped pages and their contents.
        """)
    public CrawlJob crawlWebsite(@NonNull String website) {

        return firecrawlService.crawl(
                website,
                CrawlOptions.builder()
                        .limit(MAX_CRAWL_PAGES)
                        .scrapeOptions(
                                ScrapeOptions.builder()
                                        .formats(List.of((Object) "markdown"))
                                        .onlyMainContent(true)
                                        .build()
                        )
                        .build()
        );
    }

    /**
     * Discover URLs on a website without scraping all their contents.
     */
    @Tool(description = """
        Discover pages and URLs belonging to a website.

        Use this when you need to find out what pages exist on a website
        before deciding which pages to read.

        This is useful for websites such as:
        - documentation sites
        - company websites
        - investor relations sites
        - knowledge bases
        - blogs

        Input:
        - website: the website URL

        Returns URLs discovered on the website.
        """)
    public MapData mapWebsite(@NonNull String website) {

        return firecrawlService.map(
                website,
                MapOptions.builder()
                        .limit(DEFAULT_WEBSITE_SEARCH_LIMIT)
                        .build()
        );
    }
}