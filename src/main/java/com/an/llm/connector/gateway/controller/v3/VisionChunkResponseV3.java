package com.an.llm.connector.gateway.controller.v3;

public record VisionChunkResponseV3(
        int chunkNumber,
        int primaryPage,
        Integer previousContextPage,
        String response
) {}