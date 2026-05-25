package com.an.llm.connector.gateway.enums;

import com.an.llm.connector.gateway.exception.NotFoundException;
import jakarta.validation.constraints.NotNull;

public enum LlmModels {
    QWEN_INSTRUCT("qwen-instruct"),
    QWEN_SML("qwen-sml"),
    BONSAI("bonsai"),
    QWEN_CODE("qwen-coder"),
    QWEN_VL("qwen-vl"),
    BGE_LARGE_EMBED("bge-large-embed"),
    QWEN3_EMBED("qwen-3-embed"),
    GEMMA_4("gemma-4");

    private final String id;

    LlmModels(String id){
        this.id = id;
    }

    public String getValue(){
        return this.id;
    }

    public static LlmModels getFromValue(@NotNull String value){
        for (LlmModels model : LlmModels.values()){
            if (model.getValue().equalsIgnoreCase(value)){
                return model;
            }
        }

        throw new NotFoundException("We do not support the given model.");
    }
}
