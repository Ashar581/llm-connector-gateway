package com.an.llm.connector.gateway.model;

import com.an.llm.connector.gateway.enums.ChatRole;
import lombok.Data;

@Data
public class ChatHistory {
    private ChatRole role;
    private String content;
}
