package com.an.llm.connector.gateway.enums;

import com.an.llm.connector.gateway.exception.NotFoundException;
import lombok.NonNull;

public enum ChatRole {
    USER("user"),
    ASSISTANT("assistant");

    private final String id;

    ChatRole(String id){
        this.id = id;
    }

    public String getValue(){
        return this.id;
    }

    public static ChatRole getFromValue(@NonNull String value){
        for (ChatRole role : ChatRole.values()) {
            if (role.getValue().equalsIgnoreCase(value)){
                return role;
            }
        }

        throw new NotFoundException("Invalid chat role type.");
    }
}
