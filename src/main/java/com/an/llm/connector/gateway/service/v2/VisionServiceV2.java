package com.an.llm.connector.gateway.service.v2;

import com.an.llm.connector.gateway.enums.LlmCapability;
import com.an.llm.connector.gateway.exception.ApiFallbackException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.service.ai.DocumentVisionPreprocessor;
import com.an.llm.connector.gateway.util.LlmInstructions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final PageChunker pageChunker;

    public String visionPrompt(LlmConnectorRequest request) {

        validateAllowedType(request);

        try {

            List<byte[]> pages =
                    documentVisionPreprocessor.preprocess(
                            request.getFiles().getFirst()
                    );

            String prompt =
                    request.getInstructions() != null
                            ? request.getInstructions()
                            : LlmInstructions.INVOICE_OCR_INSTRUCTIONS;

            /*
             * Fast path.
             *
             * Images and small PDFs should remain
             * a single VLM call.
             */
            if (pages.size() <= PageChunker.PAGES_PER_CHUNK) {

                return chunkingVisionService.executeChunk(
                        pages,
                        prompt,
                        request
                );
            }

            /*
             * Large document path.
             */
            List<List<byte[]>> chunks =
                    pageChunker.chunk(pages);

            List<String> chunkResponses =
                    new ArrayList<>();

            for (List<byte[]> chunk : chunks) {

                chunkResponses.add(
                        chunkingVisionService.executeChunk(
                                chunk,
                                prompt,
                                request
                        )
                );
            }

            System.out.println("\n\nChunk Response: "+chunkResponses+"\n\n");

            return visionAggregationService.aggregate(
                    prompt,
                    chunkResponses,
                    request
            );

        } catch (Exception e) {

            log.error(
                    "Error while communication with VL.",
                    e
            );

            throw new ApiFallbackException(
                    "Error while communicating with VL model."
            );
        }
    }

    private void validateAllowedType(LlmConnectorRequest request){
        LlmCapability type = LlmCapability.getFromValue(request.getType());
        //not allowed list
        Set<LlmCapability> allowedTypes = Set.of(LlmCapability.VISION);

        if (!allowedTypes.contains(type)) throw new ApiFallbackException("The requested type is not supported by this endpoint.");
    }
}
