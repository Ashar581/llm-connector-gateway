package com.an.llm.connector.gateway.service.tokenize;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenChunkService {
    private static final int CHARS_PER_TOKEN = 4;

    private final LlamaTokenCountService tokenCountService;

    public List<String> chunk(String text, String tokenizerBaseUrl, int maxTokens) {

        if (text == null || text.isBlank()) {
            return List.of();
        }

        int estimatedTokens = text.length() / CHARS_PER_TOKEN;

        if (estimatedTokens <= maxTokens) {
            int actualTokens = tokenCountService.countTokens(tokenizerBaseUrl, text);

            if (actualTokens <= maxTokens) {
                return List.of(text);
            }
        }

        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {

            int estimatedEnd = Math.min(
                    text.length(),
                    start + (maxTokens * CHARS_PER_TOKEN)
            );

            int end = findLargestValidEnd(
                    text,
                    start,
                    estimatedEnd,
                    tokenizerBaseUrl,
                    maxTokens
            );

            if (end <= start) {
                end = Math.min(start + 1000, text.length());
            }

            int whitespace = lastWhitespace(text, start, end);

            if (whitespace > start) {
                end = whitespace;
            }

            chunks.add(text.substring(start, end).trim());

            start = end;
        }

        return chunks;
    }

    private int findLargestValidEnd(String text, int start, int estimatedEnd, String tokenizerBaseUrl, int maxTokens) {
        int low = start + 1;
        int high = estimatedEnd;
        int best = low;

        while (low <= high) {
            int mid = (low + high) >>> 1;

            int tokens = tokenCountService.countTokens(
                    tokenizerBaseUrl,
                    text.substring(start, mid)
            );

            if (tokens <= maxTokens) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return best;
    }

    private int lastWhitespace(String text, int start, int end) {

        for (int i = end - 1; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }

        return -1;
    }
}
