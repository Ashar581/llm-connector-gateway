package com.an.llm.connector.gateway.service.web.downloader;

import com.an.llm.connector.gateway.model.web.WebDocument;
import com.an.llm.connector.gateway.service.web.extractor.JsoupHtmlContentExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsoupWebPageDownloader {

    private final JsoupHtmlContentExtractor htmlContentExtractor;

    public List<WebDocument> download(List<String> urls) {

        List<WebDocument> webDocuments = new ArrayList<>();

        for (String url : urls) {
            try {
                log.info("Downloading URL: {}", url);
                Connection.Response response = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/137.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9," +
                                        "image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Cache-Control", "no-cache")
                        .header("Pragma", "no-cache")
                        .referrer("https://www.google.com/")
                        .followRedirects(true)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .timeout(15000)
                        .execute();

                int statusCode = response.statusCode();

                log.info("HTTP Status: {}", statusCode);

                if (statusCode == 403) {
                    log.warn(
                            "Access forbidden by remote server. Skipping URL: {}",
                            url
                    );
                    continue;
                }

                if (statusCode != 200) {
                    log.warn(
                            "Skipping URL: {} because server returned HTTP {}",
                            url,
                            statusCode
                    );
                    continue;
                }

                Document document = response.parse();

                String text = htmlContentExtractor.extract(
                        document.html()
                );

                if (text == null || text.isBlank()) {
                    log.warn(
                            "Skipping URL because no readable content was extracted: {}",
                            url
                    );
                    continue;
                }

                webDocuments.add(
                        new WebDocument(
                                document.title(),
                                url,
                                text
                        )
                );

            } catch (Exception e) {

                log.error(
                        "Failed to download URL: {}",
                        url,
                        e
                );
            }
        }

        return webDocuments;
    }
}