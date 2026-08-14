package com.an.llm.connector.gateway.service.vision;

import com.an.llm.connector.gateway.entity.system.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.VisionInternalStatsAndResponse;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import com.an.llm.connector.gateway.util.PageChunker;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class VisionAggregationService {
    private final AiBeanFactory aiBeanFactory;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;

    public VisionInternalStatsAndResponse aggregate(String originalPrompt, List<String> chunkResponses, int totalPages,LlmConnectorRequest request) {
        StringBuilder aggregationPrompt = new StringBuilder();

        aggregationPrompt.append("""
                The following responses were generated
                from different page groups of the same
                document.

                Original Instruction:
                """);

        aggregationPrompt.append(originalPrompt);
        aggregationPrompt.append("\n\n");

        for (int i = 0; i < chunkResponses.size(); i++) {
            int startPage = (i * (request.getPageChunk() == null ? PageChunker.DEFAULT_PAGES_PER_CHUNK : request.getPageChunk())) + 1;
            int endPage = Math.min(startPage + (request.getPageChunk() == null ? PageChunker.DEFAULT_PAGES_PER_CHUNK : request.getPageChunk()) - 1, totalPages);

            aggregationPrompt.append("\nCHUNK ").append(i + 1)
                    .append(" | PAGES ").append(startPage).append("-").append(endPage).append(":\n");
            aggregationPrompt.append(chunkResponses.get(i));
            aggregationPrompt.append("\n");
        }

        aggregationPrompt.append("""
            You are consolidating candidate responses generated from different page groups of the SAME document.

            Important:
            - Chunk responses may contain mistakes.
            - Treat every chunk response as a candidate, not as guaranteed truth.
            - Do not invent values.
            - Do not infer missing values from document patterns.
            - Use only information present in the chunk responses.
            - Preserve the output format requested by the original instruction.
            - Return only the final answer.

            Conflict rules:
            - If the same field appears with the same value in multiple chunks, prefer that value.
            - If the same field has conflicting values, prefer the value with clearer surrounding context.
            - For document-level header fields, prefer earlier pages unless a later chunk clearly corrects it.
            - For totals, taxes, grand totals, and final amounts, prefer later summary/total pages.
            - For line items, append rows from all chunks and remove exact duplicates.
            - Never merge two different line items into one unless they are clearly identical.
            - Never replace a concrete value with null.
            - If a field is missing from all chunks, use null only if the requested format requires the field.

            JSON-specific rules:
            - If the original instruction requests JSON, return valid JSON only.
            - Do not include markdown fences.
            - Do not add fields that are not requested by the original instruction.
            - Preserve the requested JSON structure.
            - Use null for unavailable scalar fields.
            - Use [] for unavailable arrays.
            - For arrays such as line items, include all non-duplicate items from all chunks.
            - Preserve numbers, dates, IDs, PO numbers, invoice numbers, GST/VAT numbers exactly as written.
            - Do not normalize, round, or reformat identifiers.

            Summary/plain-text rules:
            - If the original instruction requests a summary or paragraph, synthesize the chunk responses into one coherent answer.
            - Do not mention internal chunk numbers unless useful to explain uncertainty.
            - If important information conflicts, state the uncertainty briefly.

            Final validation before answering:
            - Check that the answer follows the original instruction.
            - Check that JSON is valid if JSON was requested.
            - Check that no unsupported value was introduced.
            - Check that no line items from chunks were silently dropped.

            Return only the final consolidated result.
            """);

        ChatClient chatClient = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        ChatResponse response =  chatClient.prompt()
                .user(aggregationPrompt.toString())
                .options(ChatOptions.builder().temperature(0D).build())
                .call()
                .chatResponse();

        //code block for computing the tokens stats.
        assert response != null;
        String serializedResponse = Objects.requireNonNull(response.getResult()).getOutput().getText();

        //block for retaining the token consumption stats.
        SystemConsumptionStatsEntity stats = null;
        try {
            stats = systemConsumptionStatsSvc.generateStatsEntityWithoutPersisting(response, request);
        } catch (Exception ignore) {}

        return new VisionInternalStatsAndResponse(serializedResponse,stats);
    }
}