package com.an.llm.connector.gateway.service.v2;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PageChunker {

    private static final int PAGES_PER_CHUNK = 3;

    public List<List<byte[]>> chunk(List<byte[]> pages) {

        List<List<byte[]>> chunks = new ArrayList<>();

        for (int i = 0; i < pages.size(); i += PAGES_PER_CHUNK) {

            chunks.add(
                    pages.subList(
                            i,
                            Math.min(i + PAGES_PER_CHUNK, pages.size())
                    )
            );
        }

        return chunks;
    }
}
