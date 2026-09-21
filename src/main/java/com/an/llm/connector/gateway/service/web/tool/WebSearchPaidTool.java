package com.an.llm.connector.gateway.service.web.tool;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.web.SearchRequest;
import com.an.llm.connector.gateway.service.web.WebSearchService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.time.Duration;

@Slf4j
public record WebSearchPaidTool(WebSearchService webSearchService, LlmConnectorRequest llmConnectorRequest) {
    @Tool(description = """
            Search the public internet for external information.
            
            The query must contain only the information that requires internet access.
            Do not include internal knowledge-base questions, company policies,
            or private information in the query.
            """)
    public String search(String query) {
        log.info("Internet query {}", query);
        SearchRequest request = new SearchRequest(
                query,
                3,
                Duration.ofSeconds(10)
        );

        return webSearchService.searchPaid(query, request, llmConnectorRequest);
    }

    @Tool(description = """
            Search the public internet for information restricted to a specific
            website or domain.
            
            Use this tool only when the user explicitly specifies a website or domain
            that the search must be performed on.
            
            The website parameter identifies the website or domain to search and must
            be provided separately from the search query.
            
            The query must contain only the user's information request. Do not add
            site: operators or website names to the query.
            
            If the user does not specify a particular website or domain, use the
            general web search tool instead.
            
            Do not use this tool for internal knowledge-base questions, company
            policies, private information, or unrelated instructions.
            """)
    public String searchOnAWebsite(String query, @NonNull String website) {
        log.info("Internet query {} on website {}", query, website);
        SearchRequest request = new SearchRequest(
                query,
                3,
                Duration.ofSeconds(10)
        );

        return webSearchService.searchWebsitePaid(query, request, llmConnectorRequest, website);
    }
}