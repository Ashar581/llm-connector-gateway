package com.an.llm.connector.gateway.controller.v3;

import com.an.llm.connector.gateway.entity.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.VisionInternalStatsAndResponse;
import com.an.llm.connector.gateway.repository.SystemConsumptionStatsRepo;
import com.an.llm.connector.gateway.service.ai.DocumentVisionPreprocessor;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class VisionServiceV3 {

    private final DocumentVisionPreprocessor documentVisionPreprocessor;
    private final ChunkingVisionServiceV3 chunkingVisionServiceV3;
    private final VisionAggregationServiceV3 visionAggregationServiceV3;
    private final SystemConsumptionStatsRepo systemConsumptionStatsRepo;

    public String visionPrompt(LlmConnectorRequest request) {
        validateAllowedType(request);

        try {
            if (request.getFiles() == null || request.getFiles().isEmpty()) {
                throw new ApiFallbackException("At least one file is required.");
            }

            List<byte[]> pages = documentVisionPreprocessor.preprocess(request.getFiles().getFirst());

            if (pages == null || pages.isEmpty()) {
                throw new ApiFallbackException("No readable pages found in the uploaded file.");
            }

            String instructions = request.getInstructions() != null && !request.getInstructions().isBlank()
                    ? request.getInstructions()
                    : LlmInstructions.INVOICE_OCR_INSTRUCTIONS;

            if (pages.size() == 1) {
                log.info("Selecting V3 single-page vision mode.");
                return chunkingVisionServiceV3.executeSinglePage(
                        pages.getFirst(),
                        instructions,
                        request
                );
            }

            log.info("Selecting V3 primary-page sliding-context mode. pages={}", pages.size());

            long start = System.currentTimeMillis();

            List<VisionChunkResponseV3> chunkResponses = new ArrayList<>();
            List<SystemConsumptionStatsEntity> chunkStats = new ArrayList<>();

            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                int primaryPage = pageIndex + 1;

                byte[] currentPage = pages.get(pageIndex);

                VisionInternalStatsAndResponse chunkResponse =
                        chunkingVisionServiceV3.executePrimaryPageOnly(
                                currentPage,
                                primaryPage,
                                instructions,
                                request
                        );

                chunkResponses.add(new VisionChunkResponseV3(
                        primaryPage,
                        primaryPage,
                        pageIndex > 0 ? primaryPage - 1 : null,
                        chunkResponse.getResponse() == null ? "" : chunkResponse.getResponse()
                ));

                if (chunkResponse.getStats() != null) {
                    chunkStats.add(chunkResponse.getStats());
                }
            }

            VisionInternalStatsAndResponse aggregateResponse =
                    visionAggregationServiceV3.aggregate(instructions, chunkResponses, request);

            long completionTimeMs = System.currentTimeMillis() - start;

            if (aggregateResponse.getStats() != null) {
                SystemConsumptionStatsEntity finalStats =
                        mergeStats(aggregateResponse.getStats(), chunkStats, completionTimeMs);

                try {
                    Thread.startVirtualThread(() -> systemConsumptionStatsRepo.save(finalStats));
                } catch (Exception ignored) {}
            }

            return aggregateResponse.getResponse();

        } catch (ApiFallbackException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while communicating with V3 VL.", e);
            throw new ApiFallbackException("Error while communicating with VL model.");
        }
    }

    @NotNull
    private SystemConsumptionStatsEntity mergeStats(
            SystemConsumptionStatsEntity aggregateStats,
            List<SystemConsumptionStatsEntity> chunkStats,
            long completionTimeMs
    ) {
        int promptTokens = safeInt(aggregateStats.getPromptTokens());
        int completionTokens = safeInt(aggregateStats.getCompletionTokens());
        int totalTokens = safeInt(aggregateStats.getTotalTokens());

        for (SystemConsumptionStatsEntity stat : chunkStats) {
            promptTokens += safeInt(stat.getPromptTokens());
            completionTokens += safeInt(stat.getCompletionTokens());
            totalTokens += safeInt(stat.getTotalTokens());
        }

        aggregateStats.setResponseTimeInMs(completionTimeMs);
        aggregateStats.setPromptTokens(promptTokens);
        aggregateStats.setCompletionTokens(completionTokens);
        aggregateStats.setTotalTokens(totalTokens);

        return aggregateStats;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void validateAllowedType(LlmConnectorRequest request) {
        LlmCapability type = LlmCapability.getFromValue(request.getType());
        Set<LlmCapability> allowedTypes = Set.of(LlmCapability.VISION);

        if (!allowedTypes.contains(type)) {
            throw new ApiFallbackException("The requested type is not supported by this endpoint.");
        }
    }
}