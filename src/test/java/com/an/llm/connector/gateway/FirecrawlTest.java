package com.an.llm.connector.gateway;

import com.an.llm.connector.gateway.service.web.search.FirecrawlService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firecrawl.models.Document;
import com.firecrawl.models.ScrapeOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class FirecrawlTest {
    @Autowired
    private FirecrawlService firecrawlService;

    @Test
    void testCrawl() {
        System.out.println("===========================");
        System.out.println("      Testing Scraping     ");
        System.out.println("===========================");
        Document document = firecrawlService.scrape(
                "https://economictimes.indiatimes.com/icici-bank-ltd/profitandlose/companyid-9194.cms",
                ScrapeOptions.builder()
                        .formats(List.of((Object) "markdown"))
                        .onlyMainContent(true)
                        .build()
        );

        System.out.println("Response:");
        System.out.println();
        System.out.println(document.getMarkdown());
    }

    @Test
    void testCrawlWtihActions() throws Exception {
        System.out.println("===========================");
        System.out.println("      Testing Scraping     ");
        System.out.println("===========================");

        String actionsJson = """
        [
          {
            "type": "click",
            "selector": "button[onclick*=\\"showSchedule\\"]",
            "all": true
          },
          {
            "type": "wait",
            "milliseconds": 2000
          }
        ]
        """;

        ObjectMapper objectMapper = new ObjectMapper();

        List<Map<String, Object>> actions = objectMapper.readValue(
                actionsJson,
                new TypeReference<List<Map<String, Object>>>() {}
        );

        Document document = firecrawlService.scrape(
                "https://www.screener.in/company/LT/consolidated",
                ScrapeOptions.builder()
                        .formats(List.of((Object) "markdown"))
                        .onlyMainContent(true)
                        .actions(actions)
                        .build()
        );

        System.out.println("Response:");
        System.out.println();
        System.out.println(document.getMarkdown());
    }

}
