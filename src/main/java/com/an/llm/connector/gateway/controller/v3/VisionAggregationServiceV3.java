package com.an.llm.connector.gateway.controller.v3;

import com.an.llm.connector.gateway.entity.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.VisionInternalStatsAndResponse;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class VisionAggregationServiceV3 {

    private final AiBeanFactory aiBeanFactory;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;

    public VisionInternalStatsAndResponse aggregate(
            String originalPrompt,
            List<VisionChunkResponseV3> chunkResponses,
            LlmConnectorRequest request
    ) {
        StringBuilder aggregationPrompt = new StringBuilder();

        aggregationPrompt.append("""
                You are consolidating outputs generated from primary-page extraction calls.

                Original instruction:
                """);

        aggregationPrompt.append(originalPrompt);
        aggregationPrompt.append("\n\n");

        aggregationPrompt.append("""
                Extraction method:
                - Each chunk extracted one primary page.
                - Some chunks included the previous page only as context.
                - The chunk response should mostly represent the primary page.
                - Previous-page context may cause duplicate records, so deduplicate carefully.

                Chunk responses:
                """);

        for (VisionChunkResponseV3 chunk : chunkResponses) {
            aggregationPrompt
                    .append("\nCHUNK ")
                    .append(chunk.chunkNumber())
                    .append(" | PRIMARY PAGE ")
                    .append(chunk.primaryPage());

            if (chunk.previousContextPage() != null) {
                aggregationPrompt
                        .append(" | PREVIOUS CONTEXT PAGE ")
                        .append(chunk.previousContextPage());
            }

            aggregationPrompt.append(":\n");
            aggregationPrompt.append(chunk.response());
            aggregationPrompt.append("\n");
        }

        aggregationPrompt.append("""
                
                Consolidation rules:
                - Preserve the format requested by the original instruction.
                - Return only the final consolidated result.
                - Do not introduce a new format.
                - Do not invent missing values.
                - Use only information present in the chunk responses.
                - Treat chunk responses as candidate outputs, not guaranteed truth.

                Deduplication rules:
                - Remove exact duplicate records caused by previous-page context.
                - Remove near-duplicate records only when they clearly represent the same row, item, paragraph, or record.
                - Do not merge unrelated records just because they appear on adjacent pages.
                - If uncertain whether two records are duplicates, keep both.

                Continuation rules:
                - Some records, rows, items, tables, or paragraphs may continue across page boundaries.
                - If two adjacent primary-page outputs clearly describe the same continued record, merge them.
                - Merge only when identifiers, row order, description text, quantities, amounts, or surrounding context clearly match.
                - If a record starts on one page and continues on the next, preserve all visible parts.
                - Do not fabricate missing columns or values during merge.
                - If uncertain whether a record continued, keep the information separate.

                Field conflict rules:
                - If the same scalar field appears with the same value multiple times, use that value once.
                - If values conflict, prefer the value with clearer context.
                - For header fields such as vendor, buyer, PO number, invoice number, prefer earlier pages unless later pages clearly correct them.
                - For totals, taxes, grand total, balance, and final payable amount, prefer later summary or total pages.
                - Never replace a concrete non-null value with null.
                - If a requested field is missing everywhere, use null only when the requested output format requires that field.

                JSON-specific rules:
                - If JSON was requested, return valid JSON only.
                - Do not include markdown fences.
                - Do not add fields not requested by the original instruction.
                - Preserve the requested JSON structure.
                - Use null for unavailable scalar fields.
                - Use [] for unavailable arrays.
                - For arrays such as line items, include all non-duplicate items from all primary pages.
                - Preserve identifiers, numbers, dates, and amounts exactly as provided.

                Summary/plain-text rules:
                - If summary, paragraph, or plain text was requested, produce one coherent final response.
                - Do not mention internal chunk numbers unless needed to explain uncertainty.
                - Do not mention previous-page context processing.

                Final self-check:
                - The answer follows the original instruction.
                - No unsupported value was introduced.
                - Continuation records were merged only when clearly safe.
                - Duplicate records caused by previous-page context were removed.
                - JSON is valid if JSON was requested.

                Return only the final consolidated result.
                """);

        ChatClient chatClient = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        ChatResponse response = chatClient.prompt()
                .user(aggregationPrompt.toString())
                .options(ChatOptions.builder().temperature(0D).build())
                .call()
                .chatResponse();

        Objects.requireNonNull(response, "ChatResponse cannot be null");

        String serializedResponse = Objects.requireNonNull(response.getResult())
                .getOutput()
                .getText();

        SystemConsumptionStatsEntity stats = null;

        try {
            stats = systemConsumptionStatsSvc.generateStatsEntityWithoutPersisting(response, request);
        } catch (Exception ignored) {}

        return new VisionInternalStatsAndResponse(serializedResponse, stats);
    }
}