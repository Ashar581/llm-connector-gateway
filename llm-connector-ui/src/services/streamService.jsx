// src/services/streamService.js

import apiSvc from "./apiService";
import {TOKEN_KEY,REFRESH_TOKEN_KEY,} from "../context/AuthContext";

// ─────────────────────────────────────────────────────────────────────────────
// Refresh access token
// ─────────────────────────────────────────────────────────────────────────────

const refreshAccessToken = async () => {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);

    if (!refreshToken) {
        throw new Error("No refresh token available");
    }

    const authBaseUrl = "http://localhost:6969/";

    const response = await fetch(
        `${authBaseUrl}api/llm/v1/users/auth/refresh-token`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                token: refreshToken,
            }),
        }
    );

    if (!response.ok) {
        throw new Error(
            `Refresh token failed: HTTP ${response.status}`
        );
    }

    const data = await response.json();

    const newAccessToken = data?.data?.token;

    if (!newAccessToken) {
        throw new Error(
            "Refresh response did not contain a new access token"
        );
    }

    // Save the new access token.
    localStorage.setItem(TOKEN_KEY, newAccessToken);

    // If the backend rotates the refresh token, save the new one too.
    if (data?.data?.refreshToken) {
        localStorage.setItem(
            REFRESH_TOKEN_KEY,
            data.data.refreshToken
        );
    }

    return newAccessToken;
};

// ─────────────────────────────────────────────────────────────────────────────
// Authenticated streaming fetch
// ─────────────────────────────────────────────────────────────────────────────
//
// Makes the streaming request normally.
//
// If the server returns 401:
//   1. Refresh the access token.
//   2. Save the new token.
//   3. Retry the original request once.
//
// This is intentionally kept inside streamService.js so no other file
// needs to be modified.
// ─────────────────────────────────────────────────────────────────────────────

const fetchStreamWithAuth = async (
    url,
    options = {}
) => {
    let token = localStorage.getItem(TOKEN_KEY);

    const makeRequest = (accessToken) => {
        return fetch(url, {
            ...options,
            headers: {
                ...(options.headers || {}),
                ...(accessToken && {
                    Authorization: `Bearer ${accessToken}`,
                }),
            },
        });
    };

    // First attempt with the current access token.
    let response = await makeRequest(token);

    // Access token expired.
    if (response.status === 401) {
        // Get a new access token using the refresh token.
        const newToken = await refreshAccessToken();

        // Retry the exact same request with the new access token.
        response = await makeRequest(newToken);
    }

    return response;
};

// ─────────────────────────────────────────────────────────────────────────────
// Shared SSE-style stream reader
// ─────────────────────────────────────────────────────────────────────────────
//
// Reads the response body chunk by chunk, splits on newlines, strips an
// optional "data:" prefix, and forwards `data` for any line whose parsed
// JSON has a truthy `status`.
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Normal streaming request
// ─────────────────────────────────────────────────────────────────────────────

export const streamAsk = async ({
    testAgent = false,
    payload,
    signal,
    onChunk,
}) => {
    const baseUrl = apiSvc.defaults?.baseURL ?? "";
    const endpoint = testAgent
        ? "/v1/agent/stream"
        : "/v2/stream/ask";

    const response = await fetchStreamWithAuth(
        `${baseUrl}${endpoint}`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Accept: "text/event-stream",
            },
            body: JSON.stringify(payload),
            signal,
        }
    );

    await consumeStream(response, onChunk);
};

// ─────────────────────────────────────────────────────────────────────────────
// File-based streaming request
// ─────────────────────────────────────────────────────────────────────────────
//
// Used for file-based types such as RAG that need multipart/form-data.
//
// IMPORTANT:
// Do NOT manually set "Content-Type" here.
// The browser automatically sets the multipart boundary for FormData.
// ─────────────────────────────────────────────────────────────────────────────

export const streamFileAsk = async ({
    endpoint,
    formData,
    signal,
    onChunk,
}) => {
    const baseUrl = apiSvc.defaults?.baseURL ?? "";

    const response = await fetchStreamWithAuth(
        `${baseUrl}${endpoint}`,
        {
            method: "POST",
            headers: {
                Accept: "text/event-stream",
            },
            body: formData,
            signal,
        }
    );

    await consumeStream(response, onChunk);
};