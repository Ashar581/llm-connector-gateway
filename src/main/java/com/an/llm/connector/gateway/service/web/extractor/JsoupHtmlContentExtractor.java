package com.an.llm.connector.gateway.service.web.extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class JsoupHtmlContentExtractor {

    public String extract(String html) {
        Document document = Jsoup.parse(html);

        document.select("script").remove();
        document.select("style").remove();
        document.select("noscript").remove();
        document.select("svg").remove();
        document.select("footer").remove();
        document.select("nav").remove();
        document.select("header").remove();

        return document.text();
    }

}
