package com.an.llm.connector.gateway.service.classification;

import com.an.llm.connector.gateway.entity.system.SystemConsumptionStatsEntity;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.classification.DocumentTypeDefinition;
import com.an.llm.connector.gateway.model.classification.PageAnalysisResult;
import com.an.llm.connector.gateway.repository.SystemConsumptionStatsRepo;
import com.an.llm.connector.gateway.service.ai.DocumentVisionPreprocessor;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.stats.SystemConsumptionStatsSvc;
import com.an.llm.connector.gateway.util.PageContentHeuristics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageAnalysisService {
    private static final String UNKNOWN = "UNKNOWN";

    private final DocumentVisionPreprocessor preprocessor;
    private final AiBeanFactory aiBeanFactory;
    private final ClassificationPromptBuilder promptBuilder;
    private final SystemConsumptionStatsSvc systemConsumptionStatsSvc;
    private final SystemConsumptionStatsRepo systemConsumptionStatsRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    public List<PageAnalysisResult> analyzeAllPages(LlmConnectorRequest request) throws Exception {
        List<byte[]> pages = preprocessor.preprocess(request.getFiles().getFirst());

        if (pages == null || pages.isEmpty()) {
            return Collections.emptyList();
        }

        ChatClient client = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        String prompt = promptBuilder.build(parseDocumentTypes(request.getDocumentTypes()), request.getInstructions());

        List<PageAnalysisResult> results = new ArrayList<>();

        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;

        long start = System.currentTimeMillis();
        SystemConsumptionStatsEntity stats = null;

        for (int i = 0; i < pages.size(); i++) {
            byte[] currentPage = pages.get(i);

            if (i > 0 && PageContentHeuristics.isLikelyBlankOrStampOnly(currentPage)) {
                PageAnalysisResult previous = results.getLast();

                results.add(
                        new PageAnalysisResult(
                                i + 1,
                                previous.getDocumentType(),
                                false,
                                true,
                                Math.max(previous.getConfidence() * 0.85, 0.50)
                        )
                );
                continue;
            }

            try {
                List<Media> media = List.of(buildMedia(currentPage));

                UserMessage message = UserMessage.builder()
                        .text(prompt)
                        .media(media)
                        .build();

                ChatResponse response = client.prompt(new Prompt(message))
                        .options(buildChatOptions(request))
                        .call()
                        .chatResponse();

                assert response != null;

                stats = systemConsumptionStatsSvc.generateStatsEntityWithoutPersisting(response,request);

                promptTokens+=stats.getPromptTokens();
                completionTokens+=stats.getCompletionTokens();
                totalTokens+=stats.getTotalTokens();

                String serializedResponse = Objects.requireNonNull(response.getResult()).getOutput().getText();
                results.add(parse(serializedResponse, i + 1));

            } catch (Exception e) {
                log.error("Failed processing page {}", i + 1, e);
                results.add(fallbackUnknown(i + 1));
            }
        }

        long completionTimeMs = System.currentTimeMillis() - start;

        if (stats != null) {
            try {
                stats.setResponseTimeInMs(completionTimeMs);
                stats.setCompletionTokens(completionTokens);
                stats.setPromptTokens(promptTokens);
                stats.setTotalTokens(totalTokens);

                SystemConsumptionStatsEntity finalStats = stats;
                Thread.startVirtualThread(() -> systemConsumptionStatsRepo.save(finalStats));
            } catch (Exception e) {
                log.error("Error recording non-stream consumption tokens stats.", e);
            }
        }

        postProcess(results);
        return results;
    }

    public List<PageAnalysisResult> sampleAndAnalyze(LlmConnectorRequest request) throws Exception {
        List<byte[]> pages = preprocessor.preprocess(request.getFiles().getFirst());

        if (pages == null || pages.isEmpty()) {
            return Collections.emptyList();
        }

        ChatClient client = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        String prompt = promptBuilder.build(
                parseDocumentTypes(request.getDocumentTypes()),
                request.getInstructions()
        );

        List<Integer> indexes = buildSampleIndexes(pages.size());

        List<PageAnalysisResult> results = new ArrayList<>();

        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;

        long start = System.currentTimeMillis();
        SystemConsumptionStatsEntity stats = null;

        for (Integer index : indexes) {
            try {
                List<Media> media = List.of(buildMedia(pages.get(index)));

                UserMessage message = UserMessage.builder()
                        .text(prompt)
                        .media(media)
                        .build();

                ChatResponse response = client
                        .prompt(new Prompt(message))
                        .call()
                        .chatResponse();

                assert response != null;

                stats = systemConsumptionStatsSvc.generateStatsEntityWithoutPersisting(response,request);

                promptTokens+=stats.getPromptTokens();
                completionTokens+=stats.getCompletionTokens();
                totalTokens+=stats.getTotalTokens();

                String serializedResponse = Objects.requireNonNull(response.getResult()).getOutput().getText();

                results.add(parse(serializedResponse, index + 1));
            } catch (Exception e) {
                log.error("Failed processing sampled page {}", index + 1, e);
                results.add(fallbackUnknown(index + 1));
            }
        }

        //code block for saving the final tokens stats.
        long completionTimeMs = System.currentTimeMillis() - start;

        if (stats != null) {
            try {
                stats.setResponseTimeInMs(completionTimeMs);
                stats.setCompletionTokens(completionTokens);
                stats.setPromptTokens(promptTokens);
                stats.setTotalTokens(totalTokens);

                SystemConsumptionStatsEntity finalStats = stats;
                Thread.startVirtualThread(() -> systemConsumptionStatsRepo.save(finalStats));
            } catch (Exception e) {
                log.error("Error recording non-stream consumption tokens stats.", e);
            }
        }

        postProcess(results);
        return results;
    }

    private List<Integer> buildSampleIndexes(int totalPages) {

        List<Integer> indexes = new ArrayList<>();

        indexes.add(0);

        if (totalPages > 2) {
            indexes.add(totalPages / 2);
        }

        if (totalPages > 1) {
            indexes.add(totalPages - 1);
        }

        return indexes.stream()
                .distinct()
                .sorted()
                .toList();
    }

    private Media buildMedia(byte[] pageBytes) {
        return Media.builder()
                .mimeType(MediaType.IMAGE_PNG)
                .data(new ByteArrayResource(pageBytes))
                .build();
    }

    private PageAnalysisResult parse(String response, int pageNumber) {
        try {
            String json = extractJson(response);
            JsonNode node = mapper.readTree(json);

            String documentType = normalizeType(node.path("documentType").asText(UNKNOWN));

            boolean newDocument = node.path("isNewDocument").asBoolean(pageNumber == 1);
            boolean blankPage = node.path("blankPage").asBoolean(false);
            double confidence = node.path("confidence").asDouble(0.0);


            return new PageAnalysisResult(
                    pageNumber,
                    documentType,
                    newDocument,
                    blankPage,
                    confidence
            );
        } catch (Exception e) {
            log.error("Failed parsing classification response for page {}: {}", pageNumber, response, e);
            return fallbackUnknown(pageNumber);
        }
    }

    private String extractJson(String response) {
        Pattern pattern = Pattern.compile("\\{.*}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group();
        }

        throw new IllegalArgumentException("No valid JSON found in model response.");
    }

    private String normalizeType(String type) {
        if (type == null) {
            return UNKNOWN;
        }

        return type.trim()
                .replace(" ", "_")
                .toUpperCase();
    }

    private PageAnalysisResult fallbackUnknown(int pageNumber) {
        return new PageAnalysisResult(
                pageNumber,
                UNKNOWN,
                pageNumber == 1,
                false,
                0.0);
    }

    private void postProcess(List<PageAnalysisResult> pages) {
        for (int i = 1; i < pages.size(); i++) {
            PageAnalysisResult current = pages.get(i);
            PageAnalysisResult previous = pages.get(i - 1);

            if (current.isBlankPage()) {
                current.setNewDocument(false);
                current.setDocumentType(previous.getDocumentType());

                current.setConfidence(
                        Math.max(
                                current.getConfidence(),
                                previous.getConfidence() * 0.85
                        )
                );
                continue;
            }

            boolean strongSameType = current.getDocumentType()
                    .equals(previous.getDocumentType())
                    && current.getConfidence() >= 0.80
                    && previous.getConfidence() >= 0.80;

            if (strongSameType && current.isNewDocument()) {
                current.setNewDocument(false);
            }

            boolean strongTypeChange = !current.getDocumentType()
                    .equals(previous.getDocumentType())
                    && !"UNKNOWN".equals(current.getDocumentType())
                    && current.getConfidence() >= 0.90;

            if (strongTypeChange) {
                current.setNewDocument(true);
            }
        }
    }

    private List<DocumentTypeDefinition> parseDocumentTypes(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<DocumentTypeDefinition>>() {});
        }catch (Exception e){
            throw new NullException("Invalid document types received.");
        }
    }

    // make a dynamic configuration
    private ChatOptions buildChatOptions(LlmConnectorRequest request){
        ChatOptions.Builder<?> builder = ChatOptions.builder();

        builder.temperature(request.getTemperature());
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }

        return builder.build();
    }
}