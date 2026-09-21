package com.an.llm.connector.gateway.service.web.extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class JsoupHtmlContentExtractor {

    /*
     * Approximate budget.
     *
     * 120,000 characters is roughly 25k-35k tokens
     * depending on the content.
     *
     * Keep this comfortably below the model context limit
     * because the prompt, system instructions and response
     * also consume tokens.
     */
    private static final int MAX_CONTENT_CHARS = 120_000;

    /*
     * Prevent one enormous paragraph from consuming the
     * entire page budget.
     */
    private static final int MAX_PARAGRAPH_CHARS = 5_000;

    /*
     * Prevent giant cells from exploding the context.
     */
    private static final int MAX_CELL_CHARS = 1_000;

    /*
     * Prevent a massive table from consuming the entire page.
     */
    private static final int MAX_TABLE_ROWS = 300;

    /*
     * Prevent enormous lists.
     */
    private static final int MAX_LIST_ITEMS = 100;

    public String extract(String html) {

        if (html == null || html.isBlank()) {
            return "";
        }

        Document document = Jsoup.parse(html);

        removeNoise(document);

        Element content = findMainContent(document);

        StringBuilder result = new StringBuilder();

        appendTitle(document, result);

        extractContent(content, result);

        return normalize(result.toString());
    }

    private void removeNoise(Document document) {

        document.select(
                "script, style, noscript, svg, canvas, iframe, form, nav"
        ).remove();

        /*
         * Be careful about removing footer/aside.
         *
         * They can contain source information, methodology,
         * references or statistics.
         */
        document.select(
                "[class*=advert], " +
                        "[id*=advert], " +
                        "[class*=ad-], " +
                        "[id*=ad-], " +
                        "[class*=ads-], " +
                        "[id*=ads-], " +
                        "[class*=cookie], " +
                        "[id*=cookie], " +
                        "[class*=popup], " +
                        "[id*=popup], " +
                        "[class*=modal], " +
                        "[id*=modal], " +
                        "[class*=newsletter], " +
                        "[id*=newsletter], " +
                        "[class*=subscribe], " +
                        "[id*=subscribe], " +
                        "[class*=social-share], " +
                        "[class*=share-buttons]"
        ).remove();
    }

    private Element findMainContent(Document document) {

        Element content = document.selectFirst("article");

        if (content == null) {
            content = document.selectFirst("[role=main]");
        }

        if (content == null) {
            content = document.selectFirst("main");
        }

        if (content == null) {
            content = document.body();
        }

        return content;
    }

    private void appendTitle(
            Document document,
            StringBuilder result
    ) {

        String title = document.title().trim();

        if (!title.isEmpty()) {

            append(
                    result,
                    "TITLE: " + title + "\n\n"
            );
        }
    }

    private void extractContent(
            Element content,
            StringBuilder result
    ) {

        for (Node node : content.childNodes()) {

            if (budgetReached(result)) {
                break;
            }

            extractNode(node, result);
        }
    }

    private void extractNode(
            Node node,
            StringBuilder result
    ) {

        if (budgetReached(result)) {
            return;
        }

        if (node instanceof TextNode textNode) {

            String text = cleanText(textNode.text());

            if (!text.isEmpty()) {
                append(result, text + "\n");
            }

            return;
        }

        if (!(node instanceof Element element)) {
            return;
        }

        String tag = element.tagName().toLowerCase();

        switch (tag) {

            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                appendHeading(element, result);
                break;

            case "p":
                appendParagraph(element, result);
                break;

            case "ul":
            case "ol":
                appendList(element, result);
                break;

            case "table":
                appendTable(element, result);
                break;

            case "blockquote":
                appendBlockquote(element, result);
                break;

            case "figure":
                appendFigure(element, result);
                break;

            case "pre":
                appendPreformatted(element, result);
                break;

            case "dl":
                appendDefinitionList(element, result);
                break;

            case "hr":
                append(result, "\n---\n\n");
                break;

            default:

                /*
                 * Don't use element.text() here.
                 *
                 * Recursively process the structure so that
                 * tables/lists/headings retain their formatting.
                 */
                for (Node child : element.childNodes()) {

                    if (budgetReached(result)) {
                        break;
                    }

                    extractNode(child, result);
                }
        }
    }

    private void appendHeading(
            Element element,
            StringBuilder result
    ) {

        String text = cleanText(element.text());

        if (text.isEmpty()) {
            return;
        }

        append(
                result,
                "\n" +
                        text +
                        "\n\n"
        );
    }

    private void appendParagraph(
            Element element,
            StringBuilder result
    ) {

        String text = cleanText(element.text());

        if (text.isEmpty()) {
            return;
        }

        if (text.length() > MAX_PARAGRAPH_CHARS) {

            text = text.substring(
                    0,
                    MAX_PARAGRAPH_CHARS
            ) + " [paragraph truncated]";
        }

        append(
                result,
                text + "\n\n"
        );
    }

    private void appendList(
            Element element,
            StringBuilder result
    ) {

        boolean ordered =
                element.tagName().equalsIgnoreCase("ol");

        int index = 1;
        int count = 0;

        for (Element item : element.children()) {

            if (count >= MAX_LIST_ITEMS) {

                append(
                        result,
                        "[Additional list items omitted]\n\n"
                );

                break;
            }

            if (!item.tagName().equalsIgnoreCase("li")) {
                continue;
            }

            String text = cleanText(item.text());

            if (text.isEmpty()) {
                continue;
            }

            if (ordered) {
                append(
                        result,
                        index++ + ". " + text + "\n"
                );
            } else {
                append(
                        result,
                        "- " + text + "\n"
                );
            }

            count++;

            if (budgetReached(result)) {
                break;
            }
        }

        append(result, "\n");
    }

    private void appendTable(
            Element table,
            StringBuilder result
    ) {

        if (budgetReached(result)) {
            return;
        }

        append(result, "\nTABLE\n");

        Element caption = table.selectFirst("caption");

        if (caption != null) {

            String captionText =
                    cleanText(caption.text());

            if (!captionText.isEmpty()) {

                append(
                        result,
                        "Caption: " +
                                captionText +
                                "\n"
                );
            }
        }

        int rowCount = 0;

        for (Element row : table.select("tr")) {

            if (rowCount >= MAX_TABLE_ROWS) {

                append(
                        result,
                        "[Additional table rows omitted]\n"
                );

                break;
            }

            var cells =
                    row.select("> th, > td");

            if (cells.isEmpty()) {
                continue;
            }

            String rowText = cells.stream()
                    .map(this::extractCellText)
                    .collect(Collectors.joining(" | "));

            if (!rowText.isBlank()) {

                append(
                        result,
                        rowText + "\n"
                );
            }

            rowCount++;

            if (budgetReached(result)) {
                break;
            }
        }

        append(
                result,
                "END TABLE\n\n"
        );
    }

    private String extractCellText(
            Element cell
    ) {

        String text = cleanText(cell.text());

        if (text.length() > MAX_CELL_CHARS) {

            text = text.substring(
                    0,
                    MAX_CELL_CHARS
            ) + "…";
        }

        String scope = cell.attr("scope");

        if (!scope.isBlank()) {

            text =
                    "[" +
                            scope.toUpperCase() +
                            "] " +
                            text;
        }

        return text;
    }

    private void appendBlockquote(
            Element element,
            StringBuilder result
    ) {

        String text = cleanText(element.text());

        if (!text.isEmpty()) {

            append(
                    result,
                    "\nQUOTE: " +
                            text +
                            "\n\n"
            );
        }
    }

    private void appendFigure(
            Element element,
            StringBuilder result
    ) {

        Element image =
                element.selectFirst("img");

        Element caption =
                element.selectFirst("figcaption");

        if (image != null) {

            String alt =
                    cleanText(image.attr("alt"));

            if (!alt.isEmpty()) {

                append(
                        result,
                        "IMAGE DESCRIPTION: " +
                                alt +
                                "\n"
                );
            }
        }

        if (caption != null) {

            String text =
                    cleanText(caption.text());

            if (!text.isEmpty()) {

                append(
                        result,
                        "CAPTION: " +
                                text +
                                "\n"
                );
            }
        }

        append(result, "\n");
    }

    private void appendPreformatted(
            Element element,
            StringBuilder result
    ) {

        String text =
                element.wholeText().trim();

        if (text.isEmpty()) {
            return;
        }

        /*
         * Preformatted blocks can contain huge JSON,
         * code or datasets.
         */
        if (text.length() > 10_000) {

            text = text.substring(
                    0,
                    10_000
            ) + "\n[PRE DATA TRUNCATED]";
        }

        append(
                result,
                "\nPRE_FORMATTED_DATA\n" +
                        text +
                        "\nEND_PRE_FORMATTED_DATA\n\n"
        );
    }

    private void appendDefinitionList(
            Element element,
            StringBuilder result
    ) {

        for (Element child : element.children()) {

            if (budgetReached(result)) {
                break;
            }

            String tag =
                    child.tagName().toLowerCase();

            String text =
                    cleanText(child.text());

            if (text.isEmpty()) {
                continue;
            }

            if (tag.equals("dt")) {

                append(
                        result,
                        "TERM: " +
                                text +
                                "\n"
                );

            } else if (tag.equals("dd")) {

                append(
                        result,
                        "DEFINITION: " +
                                text +
                                "\n"
                );
            }
        }

        append(result, "\n");
    }

    private boolean budgetReached(
            StringBuilder result
    ) {

        return result.length() >= MAX_CONTENT_CHARS;
    }

    private void append(
            StringBuilder result,
            String text
    ) {

        if (text == null || text.isEmpty()) {
            return;
        }

        int remaining =
                MAX_CONTENT_CHARS - result.length();

        if (remaining <= 0) {
            return;
        }

        if (text.length() <= remaining) {

            result.append(text);

        } else {

            result.append(
                    text.substring(0, remaining)
            );

            result.append(
                    "\n\n[PAGE CONTENT TRUNCATED]\n"
            );
        }
    }

    private String cleanText(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalize(String text) {

        return text
                .replace("\u00A0", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *\\n *\\n+", "\n\n")
                .trim();
    }
}