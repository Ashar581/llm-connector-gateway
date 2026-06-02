package com.an.llm.connector.gateway.util;

import com.an.llm.connector.gateway.model.ChatHistory;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageContextUtils {

    public static SystemMessage buildSystemMessage(String instructions) {
        return new SystemMessage(instructions != null ? instructions : "");
    }

    public static UserMessage buildCurrentUserMessage(LlmConnectorRequest request) {
        return new UserMessage(request.getQuery() != null ? request.getQuery() : "");
    }

    public static List<Message> buildHistoryMessages(LlmConnectorRequest request) {
        List<Message> historyMessages = new ArrayList<>();

        if (request.isChatHistoryEnabled()
                && request.getChatHistory() != null
                && !request.getChatHistory().isEmpty()) {

            for (ChatHistory history : request.getChatHistory()) {
                if (history.getContent() == null || history.getContent().isBlank()) {
                    continue;
                }

                switch (history.getRole()) {
                    case ASSISTANT -> historyMessages.add(new AssistantMessage(history.getContent()));
                    case USER -> historyMessages.add(new UserMessage(history.getContent()));
                }
            }
        }

        return historyMessages;
    }

    public static List<Message> merge(
            SystemMessage systemMessage,
            List<Message> historyMessages,
            UserMessage currentUserMessage
    ) {
        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.addAll(historyMessages);
        messages.add(currentUserMessage);
        return messages;
    }
}