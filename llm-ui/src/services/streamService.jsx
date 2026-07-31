// src/services/streamService.js

import apiSvc from "./apiService";
import { TOKEN_KEY } from "../context/AuthContext";

// Shared SSE-style stream reader used by every streaming call below.
// Reads the response body chunk by chunk, splits on newlines, strips an
// optional "data:" prefix, and forwards `data` for any line whose parsed
// JSON has a truthy `status`.
const consumeStream = async (response, onChunk) => {
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }

    if (!response.body) {
        throw new Error("No response body");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");

    while (true) {
        const { done, value } = await reader.read();

        if (done) break;

        const chunk = decoder.decode(value, {
            stream: true,
        });

        const lines = chunk.split("\n");

        for (const line of lines) {
            if (!line.trim()) continue;

            try {
                const cleaned = line.startsWith("data:")
                    ? line.replace("data:", "").trim()
                    : line.trim();

                const parsed = JSON.parse(cleaned);

                if (parsed.status) {
                    onChunk(parsed.data ?? "");
                }
            } catch (err) {
                console.error(
                    "Chunk parse failed",
                    err
                );
            }
        }
    }
};

export const streamAsk = async ({
    testAgent = false,
    payload,
    signal,
    onChunk,
}) => {
    const token = localStorage.getItem(TOKEN_KEY);
    const baseUrl = apiSvc.defaults?.baseURL ?? "";
    const endpoint = testAgent ? "/v1/agent/stream" : "/v2/stream/ask";
    const response = await fetch(
        `${baseUrl}${endpoint}`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Accept: "text/event-stream",
                ...(token && {
                    Authorization: `Bearer ${token}`,
                }),
            },
            body: JSON.stringify(payload),
            signal,
        }
    );

    await consumeStream(response, onChunk);
};

// Streaming variant for file-based types (e.g. RAG) that need to send
// multipart/form-data instead of JSON. Used when a type is both a file
// type AND supports streaming (see TYPE_FIELD_CONFIGS[type].supportsStream).
//
// IMPORTANT: do NOT set a "Content-Type" header here — when the body is a
// FormData instance, the browser must set its own multipart boundary. Setting
// it manually breaks the multipart parsing on the server.
export const streamFileAsk = async ({
    endpoint,
    formData,
    signal,
    onChunk,
}) => {
    const token = localStorage.getItem(TOKEN_KEY);
    const baseUrl = apiSvc.defaults?.baseURL ?? "";
    const response = await fetch(
        `${baseUrl}${endpoint}`,
        {
            method: "POST",
            headers: {
                Accept: "text/event-stream",
                ...(token && {
                    Authorization: `Bearer ${token}`,
                }),
            },
            body: formData,
            signal,
        }
    );

    await consumeStream(response, onChunk);
};