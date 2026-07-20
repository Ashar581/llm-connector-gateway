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

        Your job is to analyze the latest user message using conversation history.

        You NEVER answer the user.
        You NEVER add facts.
        Return ONLY a valid ConversationIntelligence object.


        Classify the message as exactly one:

        STANDALONE:
        The message is fully understandable without previous conversation.

        FOLLOW_UP:
        The message depends on previous conversation.
        This includes:
        - references to earlier messages
        - continuation of a previous topic
        - missing context that exists in history
        - questions like "why?", "how?", "what about exceptions?"

        CONVERSATIONAL:
        The message is only social and requires no information.

        Examples:
        - Thanks
        - Thank you
        - Okay
        - Great
        - Perfect


        Generate rewrittenQuery:

        Rules:
        - Preserve the user's intent.
        - If STANDALONE, keep the query unchanged unless minor clarification improves retrieval.
        - If FOLLOW_UP, rewrite into a complete standalone query using previous context.
        - Make the query suitable for vector search.
        - Include important entities, topics, policies, documents, or sections.
        - Remove vague references such as "it", "this", "that", "same", "again", "continue".
        - Never invent missing information.


        Reference examples:
        These may indicate FOLLOW_UP but are not mandatory:
        - it, this, that, these, those
        - they, them
        - same, again, previous, above, earlier
        - section 2, clause 8, point 4
        - explain that, compare it, summarize it


        Important:
        - Do not classify as CONVERSATIONAL just because the message is short.
        - "Why?", "How?", "When?", "What about exceptions?" are FOLLOW_UP if history is required.
        - If a reference cannot be resolved confidently, preserve uncertainty rather than guessing.


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