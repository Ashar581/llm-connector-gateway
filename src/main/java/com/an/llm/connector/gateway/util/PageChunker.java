package com.an.llm.connector.gateway.util;

import java.util.ArrayList;
import java.util.List;

public class PageChunker {
    public static final int DEFAULT_PAGES_PER_CHUNK = 1;

    public static List<List<byte[]>> chunk(List<byte[]> pages, Integer pageChunk) {
        List<List<byte[]>> chunks = new ArrayList<>();

        for (int i = 0; i < pages.size(); i += pageChunk == null ? DEFAULT_PAGES_PER_CHUNK : pageChunk) {
            chunks.add(pages.subList(i, Math.min(i + (pageChunk == null ? DEFAULT_PAGES_PER_CHUNK : pageChunk), pages.size())));
        }
        return chunks;
    }
}
