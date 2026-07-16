package com.an.llm.connector.gateway.enums;

public enum ConversationType {

    /**
     * Completely independent question.
     */
    STANDALONE,

    /**
     * Depends on previous messages.
     */
    FOLLOW_UP,

    /**
     * Pure conversation.
     */
    CONVERSATIONAL
}
