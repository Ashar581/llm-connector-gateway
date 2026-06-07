package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.entity.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.VisionInternalStatsAndResponse;
import com.an.llm.connector.gateway.repository.SystemConsumptionStatsRepo;
import com.an.llm.connector.gateway.service.vision.ChunkingVisionService;
import com.an.llm.connector.gateway.service.vision.VisionAggregationService;
import com.an.llm.connector.gateway.util.LlmInstructions;
import com.an.llm.connector.gateway.util.PageChunker;
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
public class VisionServiceV2 {
    private final DocumentVisionPreprocessor documentVisionPreprocessor;
    private final ChunkingVisionService chunkingVisionService;
    private final VisionAggregationService visionAggregationService;
    private final SystemConsumptionStatsRepo systemConsumptionStatsRepo;

    public String visionPrompt(LlmConnectorRequest request) {
        validateAllowedType(request);

        try {
            List<byte[]> pages = documentVisionPreprocessor.preprocess(request.getFiles().getFirst());

            String instructions = request.getInstructions() != null ? request.getInstructions() : LlmInstructions.INVOICE_OCR_INSTRUCTIONS;

            if (pages.size() <= PageChunker.PAGES_PER_CHUNK) {
                log.info("Selecting single vision mode.");
                return chunkingVisionService.executeVisionWithinSingleRange(pages, instructions, request);
            }

            log.info("Selecting aggregation vision mode.");
            List<List<byte[]>> chunks = PageChunker.chunk(pages);
            List<String> chunkResponses = new ArrayList<>();

            long start = System.currentTimeMillis();

            VisionInternalStatsAndResponse visionInternalStatsAndResponseForChunk = new VisionInternalStatsAndResponse();
            for (List<byte[]> chunk : chunks) {
                visionInternalStatsAndResponseForChunk = chunkingVisionService.executeChunk(chunk, instructions, request);
                chunkResponses.add(visionInternalStatsAndResponseForChunk.getResponse() == null ? "" : visionInternalStatsAndResponseForChunk.getResponse());
            }

            VisionInternalStatsAndResponse visionInternalStatsAndResponseForAggregate = 
                    visionAggregationService.aggregate(instructions, chunkResponses, request);

            //code block to populate the final token stats
            long completionTimeMs = System.currentTimeMillis() - start;

            if (visionInternalStatsAndResponseForChunk.getStats() != null && visionInternalStatsAndResponseForAggregate.getStats() != null) {
                SystemConsumptionStatsEntity finalStats = getSystemConsumptionStatsEntity(visionInternalStatsAndResponseForAggregate, visionInternalStatsAndResponseForChunk, completionTimeMs);
                try {
                    Thread.startVirtualThread(()->systemConsumptionStatsRepo.save(finalStats));
                }catch (Exception ignore) {}
            }

            return visionInternalStatsAndResponseForAggregate.getResponse();
            
        } catch (Exception e) {
            log.error("Error while communication with VL.", e);
            throw new ApiFallbackException("Error while communicating with VL model.");
        }
    }

    @NotNull
    private SystemConsumptionStatsEntity getSystemConsumptionStatsEntity(VisionInternalStatsAndResponse visionInternalStatsAndResponseForAggregate, VisionInternalStatsAndResponse visionInternalStatsAndResponseForChunk, long completionTimeMs) {
        SystemConsumptionStatsEntity finalStats = visionInternalStatsAndResponseForAggregate.getStats();
        int promptTokens = visionInternalStatsAndResponseForChunk.getStats().getPromptTokens() + visionInternalStatsAndResponseForAggregate.getStats().getPromptTokens();
        int completionTokens = visionInternalStatsAndResponseForChunk.getStats().getCompletionTokens() + visionInternalStatsAndResponseForAggregate.getStats().getCompletionTokens();
        int totalTokens = visionInternalStatsAndResponseForChunk.getStats().getTotalTokens() + visionInternalStatsAndResponseForAggregate.getStats().getTotalTokens();

        finalStats.setResponseTimeInMs(completionTimeMs);
        finalStats.setPromptTokens(promptTokens);
        finalStats.setCompletionTokens(completionTokens);
        finalStats.setTotalTokens(totalTokens);
        return finalStats;
    }

    private void validateAllowedType(LlmConnectorRequest request){
        LlmCapability type = LlmCapability.getFromValue(request.getType());
        //not allowed list
        Set<LlmCapability> allowedTypes = Set.of(LlmCapability.VISION);

        if (!allowedTypes.contains(type)) throw new ApiFallbackException("The requested type is not supported by this endpoint.");
    }
}
