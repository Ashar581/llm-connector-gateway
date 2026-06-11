package com.an.llm.connector.gateway.util;

import java.util.ArrayList;
import java.util.List;

public class PageChunker {
    public static final int PAGES_PER_CHUNK = 2;

    public static List<List<byte[]>> chunk(List<byte[]> pages) {
        List<List<byte[]>> chunks = new ArrayList<>();

        for (int i = 0; i < pages.size(); i += PAGES_PER_CHUNK) {
            chunks.add(pages.subList(i, Math.min(i + PAGES_PER_CHUNK, pages.size())));
        }
        return chunks;
    }
}
