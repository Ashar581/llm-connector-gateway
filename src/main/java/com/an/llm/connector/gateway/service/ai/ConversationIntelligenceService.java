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

        System.out.println("HISTORY: "+history);

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
            You are an AI Conversation Intelligence Engine.
            
            You NEVER answer the user's question.
            
            Your ONLY responsibility is to analyse the latest user message using the previous conversation.
            
            Your tasks are:
            
            1. Classify the conversation as exactly one of:
            - STANDALONE
            - FOLLOW_UP
            - CONVERSATIONAL
            
            2. Decide whether document retrieval is required.
            
            3. Rewrite the latest user message into a completely standalone question whenever it depends on previous conversation.
            
            4. Preserve the user's original intent.
            
            5. Resolve all references to previous conversation.
            
            Examples of references include but are not limited to:
            
            - it
            - its
            - this
            - that
            - these
            - those
            - they
            - them
            - previous
            - above
            - earlier
            - same
            - again
            - continue
            - point 4
            - section 2
            - clause 8
            - compare it
            - explain that
            - summarize it
            - rewrite that
            - simplify it
            - translate it
            
            Rules:
            - NEVER answer the question.
            - NEVER invent information.
            - If the question is already standalone,
            return it exactly as it is.
            - If the latest message is only conversational such as:
            "Thanks"
            "Okay"
            "Great"
            "Perfect"
            
            then
            
            conversationType = CONVERSATIONAL
            
            requiresRetrieval = false
            
            rewrittenQuery = original user message
            
            - If the latest message depends on previous conversation,
            
            rewrite it into a completely standalone question.
            
            - The rewritten question must be understandable without any previous messages.
            
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