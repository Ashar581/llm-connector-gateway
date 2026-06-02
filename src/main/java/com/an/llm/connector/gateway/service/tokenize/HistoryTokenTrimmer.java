package com.an.llm.connector.gateway.service.tokenize;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoryTokenTrimmer {

    private final LlamaTokenCountService tokenCountService;

    public List<Message> trimHistory(String llamaBaseUrl, List<Message> history, int historyBudget) {
        if (history == null || history.isEmpty() || historyBudget <= 0) {
            return List.of();
        }

        List<Message> selected = new ArrayList<>();
        int usedTokens = 0;

        for (int i = history.size() - 1; i >= 0; i--) {
            Message current = history.get(i);

            int currentTokens = count(llamaBaseUrl, current);

            if (usedTokens + currentTokens > historyBudget) {
                break;
            }

            selected.add(current);
            usedTokens += currentTokens;
        }

        Collections.reverse(selected);
        return selected;
    }

    private int count(String llamaBaseUrl, Message message) {
        return tokenCountService.countTokens(
                llamaBaseUrl,
                message.getText()
        );
    }
}