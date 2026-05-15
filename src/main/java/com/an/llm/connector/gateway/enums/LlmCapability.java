package com.an.llm.connector.gateway.enums;

import com.an.llm.connector.gateway.exception.NotFoundException;
import jakarta.validation.constraints.NotNull;

public enum LlmCapability {
    AGENT("agent"),
    EMBEDDING("embedding"),
    GENERATION("generation"),
    CHAT("chat"),
    TOOL("tool"),
    SUMMARIZATION("summarization"),
    CODE("code"),
    VISION("vision"),
    ALL("all");

    private final String id;

    LlmCapability(String id){
        this.id = id;
    }

    public String getValue(){
        return this.id;
    }

    public static LlmCapability getFromValue(@NotNull String value){
        for (LlmCapability type : LlmCapability.values()) {
            if (type.getValue().equalsIgnoreCase(value)){
                return type;
            }
        }

        throw new NotFoundException("Invalid llm type.");
    }
}
