package com.an.llm.connector.gateway.service.ai;

import com.an.llm.connector.gateway.enums.ConversationType;
import com.an.llm.connector.gateway.model.ChatHistory;
import com.an.llm.connector.gateway.model.ContextBudget;
import com.an.llm.connector.gateway.model.ConversationIntelligence;
import com.an.llm.connector.gateway.model.LlmConnectorRequest;
import com.an.llm.connector.gateway.model.config.ModelConfig;
import com.an.llm.connector.gateway.service.LlmConfigService;
import com.an.llm.connector.gateway.service.factory.AiBeanFactory;
import com.an.llm.connector.gateway.service.tokenize.ContextBudgetService;
import com.an.llm.connector.gateway.service.tokenize.HistoryTokenTrimmer;
import com.an.llm.connector.gateway.util.ChatMessageContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationIntelligenceService {
    private final AiBeanFactory aiBeanFactory;
    private final ContextBudgetService contextBudgetService;
    private final HistoryTokenTrimmer historyTokenTrimmer;
    private final LlmConfigService llmConfigService;

    public ConversationIntelligence analyse(LlmConnectorRequest request) {
        String history = buildHistory(buildTrimmedHistory(request));

        ChatClient client = aiBeanFactory.getChatClient(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        try {
            return client.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(USER_PROMPT.formatted(
                            history,
                            request.getQuery()
                    ))
                    .call()
                    .entity(ConversationIntelligence.class);
        } catch (Exception e){
            log.warn("Conversation intelligence failed. Falling back to default structure.");
            return fallback(request);
        }
    }

    private String buildHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "No previous conversation.";
        }

        StringBuilder builder = new StringBuilder();

        for (Message message : history) {
            builder.append(message.getMessageType())
                    .append(": ")
                    .append(message.getText())
                    .append("\n");
        }

        return builder.toString();
    }

    private static final String SYSTEM_PROMPT = """
            Set requiresRetrieval:
            - true if any external knowledge retrieval is needed.
            - false only for conversational messages or requests that can be answered without retrieval.
            
            Set requiresRag:
            - true if the private knowledge base should be searched.
            - false otherwise.
            
            Set requiresInternet:
            - true if Internet search is needed to answer accurately.
            - This includes current, recent, live, or publicly available information, or knowledge unlikely to exist in the private knowledge base.
            - false otherwise.
            
            Generate ragQuery:
            - Only if requiresRag is true.
            - Rewrite into a standalone semantic search query optimized for vector retrieval.
            - Resolve conversation references.
            - Include relevant entities, document names, sections, policies, etc.
            
            Generate internetQuery:
            - Only if requiresInternet is true.
            - Rewrite into a concise web search query.
            - Include relevant entities, dates, locations, products, organizations, or people.
            - Resolve conversation references.
            - Do not include unnecessary wording.
            
            If a query requires both sources, produce both queries independently.
            Never invent missing information.
            """;

    private static final String USER_PROMPT = """
            ##############
            Conversation History
            ##############
            
            %s
            
            ##############
            Latest User Message
            ##############
            
            %s
            """;

    private ConversationIntelligence fallback(LlmConnectorRequest request) {
        ConversationIntelligence intelligence = new ConversationIntelligence();

        intelligence.setConversationType(ConversationType.STANDALONE);
        intelligence.setRequiresRetrieval(true);
        intelligence.setRequiresRag(true);
        intelligence.setRequiresInternet(false);
        intelligence.setRagQuery(request.getQuery());
        intelligence.setInternetQuery(null);

        return intelligence;
    }

    private List<Message> buildTrimmedHistory(LlmConnectorRequest request) {
        ModelConfig modelConfig = llmConfigService.getModelConfig(
                request.getSource(),
                request.getType(),
                request.getModel()
        );

        ContextBudget budget = contextBudgetService.calculate(
                modelConfig,
                modelConfig.getBaseUrl(),
                SYSTEM_PROMPT,
                request.getQuery()
        );

        List<Message> history =
                ChatMessageContextUtils.buildHistoryMessages(request);

        return historyTokenTrimmer.trimHistory(
                modelConfig.getBaseUrl(),
                history,
                budget.getHistoryBudget()
        );
    }
}