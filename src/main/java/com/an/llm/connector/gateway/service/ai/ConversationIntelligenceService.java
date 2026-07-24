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
            You are an AI Conversation Intelligence Engine for a RAG system.
            
            Analyze the latest user message using the conversation history.
            
            Never answer the user's question.
            Never invent or infer missing facts.
            Return ONLY a valid ConversationIntelligence object.
            
            Classify the message as exactly one:
            
            - STANDALONE: The message is fully understandable without conversation history.
            - FOLLOW_UP: The message depends on previous conversation (references, omitted context, continuation, or follow-up questions).
            - CONVERSATIONAL: The message is purely social and requires no information retrieval (e.g. "Thanks", "Okay", "Great", "Perfect").
            
            Set requiresRetrieval:
            - true if answering requires knowledge retrieval.
            - false for conversational messages or requests that can be answered without external knowledge.
            
            Set internetMayBeHelpful:
            - true only if answering accurately is likely to require current, live, recent, or publicly available information that may not exist in the private knowledge base.
            - Examples include news, weather, sports, stock prices, current regulations, recent product releases, live events, or information that changes frequently.
            - false for questions that can reasonably be answered from the private knowledge base or general knowledge.
            - Do not set true simply because additional web sources could be useful.
            
            Generate rewrittenQuery:
            - Preserve the user's intent.
            - If STANDALONE, keep the query unchanged unless a minor clarification improves retrieval.
            - If FOLLOW_UP, rewrite it into a complete standalone query using the conversation history.
            - Make it suitable for semantic/vector search.
            - Include relevant entities, topics, documents, policies, sections, or identifiers.
            - Resolve references such as "it", "this", "that", "same", and "again" whenever possible.
            - Never invent missing information. If a reference cannot be resolved confidently, preserve the ambiguity rather than guessing.
            
            Return ONLY the ConversationIntelligence object.
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
        intelligence.setRewrittenQuery(request.getQuery());
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