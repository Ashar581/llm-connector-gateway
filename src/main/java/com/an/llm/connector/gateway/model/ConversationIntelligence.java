package com.an.llm.connector.gateway.model;

import com.an.llm.connector.gateway.enums.ConversationType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ConversationIntelligence {
    private ConversationType conversationType;
    private Boolean requiresRetrieval;
    private Boolean requiresRag;
    private Boolean requiresInternet;
    private String ragQuery;
    private String internetQuery;
}