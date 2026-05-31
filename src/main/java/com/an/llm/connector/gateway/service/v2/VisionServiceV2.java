package com.an.llm.connector.gateway.service.v2;

import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.ai.DocumentVisionPreprocessor;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VisionServiceV2 {
    private final DocumentVisionPreprocessor documentVisionPreprocessor;
    private final ChunkingVisionService chunkingVisionService;
    private final VisionAggregationService visionAggregationService;
    private final PageChunker pageChunker;

    public String visionPrompt(
            LlmConnectorRequest request
    ) {

        try {

            List<byte[]> pages =
                    documentVisionPreprocessor.preprocess(
                            request.getFiles().getFirst()
                    );

            String prompt =
                    request.getInstructions() != null
                            ? request.getInstructions()
                            : LlmInstructions.INVOICE_OCR_INSTRUCTIONS;

            List<List<byte[]>> chunks =
                    pageChunker.chunk(pages);

            List<String> chunkResponses =
                    new ArrayList<>();

            for (List<byte[]> chunk : chunks) {

                String response =
                        chunkingVisionService.executeChunk(
                                chunk,
                                prompt,
                                request
                        );

                chunkResponses.add(response);
            }

            if (chunkResponses.size() == 1) {
                return chunkResponses.getFirst();
            }

            return visionAggregationService.aggregate(
                    prompt,
                    chunkResponses,
                    request
            );

        } catch (Exception ex) {

            log.error(
                    "Error while communication with VL.",
                    ex
            );

            throw new ApiFallbackException(
                    "Error while communicating with VL model."
            );
        }
    }
}
