package com.an.llm.connector.gateway.service.web.extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class JsoupHtmlContentExtractor {

    public String extract(String html) {
        Document document = Jsoup.parse(html);
        document.select(
                "script, style, noscript, svg, canvas, iframe, " +
                        "form, nav, footer"
        ).remove();
        document.select(
                "[class*=advert], " +
                        "[id*=advert], " +
                        "[class*=ad-], " +
                        "[id*=ad-], " +
                        "[class*=cookie], " +
                        "[id*=cookie], " +
                        "[class*=popup], " +
                        "[id*=popup], " +
                        "[class*=modal], " +
                        "[id*=modal], " +
                        "[class*=newsletter], " +
                        "[class*=subscribe]"
        ).remove();

        Element content = document.selectFirst(
                "article"
        );
        if (content == null) {
            content = document.selectFirst(
                    "[role=main]"
            );
        }
        if (content == null) {
            content = document.selectFirst(
                    "main"
            );
        }
        if (content == null) {
            content = document.body();
        }

        String title = document.title().trim();

        StringBuilder result = new StringBuilder();

        if (!title.isEmpty()) {
            result.append("TITLE: ")
                    .append(title)
                    .append("\n\n");
        }

        for (Element heading : content.select(
                "h1, h2, h3, h4, h5, h6"
        )) {
            String headingText = heading.text().trim();

            if (!headingText.isEmpty()) {
                result.append(headingText)
                        .append("\n");
            }
        }

        result.append("\n")
                .append(content.text());

        return result.toString()
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
