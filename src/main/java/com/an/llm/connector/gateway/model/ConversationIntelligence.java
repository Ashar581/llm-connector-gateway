package com.an.llm.connector.gateway.model;

import com.an.llm.connector.gateway.enums.ConversationType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
public class ConversationIntelligence {
    private ConversationType conversationType;
    private Boolean requiresRetrieval;
    private String rewrittenQuery;
}