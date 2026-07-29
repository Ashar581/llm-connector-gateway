package com.an.llm.connector.gateway.service.web.tool;

import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.web.SearchRequest;
import com.an.llm.connector.gateway.service.web.WebSearchService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.time.Duration;

@Data
@Slf4j
@RequiredArgsConstructor
public class WebSearchTool {
    private final WebSearchService webSearchService;
    private final LlmConnectorRequest llmConnectorRequest;

    @Tool(description = """
        Search the public internet for external information.

        The query must contain only the information that requires internet access.
        Do not include internal knowledge-base questions, company policies,
        or private information in the query.
        """)
    public String search(String query) {
        log.info("Internet query {}",query);
        SearchRequest request = new SearchRequest(
                query,
                3,
                Duration.ofSeconds(10)
        );

        return webSearchService.search(query,request, llmConnectorRequest);
    }
}
