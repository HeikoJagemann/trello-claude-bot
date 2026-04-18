package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.client.ClaudeClient;
import com.example.trelloclaudebot.client.TrelloClient;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import com.example.trelloclaudebot.dto.trello.TrelloAction;
import com.example.trelloclaudebot.dto.trello.TrelloCardData;
import com.example.trelloclaudebot.dto.trello.TrelloWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class TaskOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestratorService.class);

    /** Nur diese Action-Types lösen eine KI-Verarbeitung aus. */
    private static final Set<String> RELEVANT_ACTIONS = Set.of(
            "createCard",
            "updateCard"
    );

    private final PromptBuilder promptBuilder;
    private final ClaudeClient  claudeClient;
    private final TrelloClient  trelloClient;

    public TaskOrchestratorService(PromptBuilder promptBuilder,
                                   ClaudeClient claudeClient,
                                   TrelloClient trelloClient) {
        this.promptBuilder = promptBuilder;
        this.claudeClient  = claudeClient;
        this.trelloClient  = trelloClient;
    }

    /**
     * Verarbeitet einen eingehenden Trello Webhook Payload.
     * Wird ignoriert, wenn der Action-Type nicht relevant ist.
     */
    public void process(TrelloWebhookPayload payload) {
        TrelloAction action = payload.getAction();

        if (action == null || action.getData() == null) {
            log.debug("Payload ohne Action oder Data empfangen – wird ignoriert.");
            return;
        }

        if (!RELEVANT_ACTIONS.contains(action.getType())) {
            log.debug("Action-Type '{}' ist nicht relevant – wird ignoriert.", action.getType());
            return;
        }

        TrelloCardData card = action.getData().getCard();
        if (card == null || card.getId() == null) {
            log.warn("Payload enthält keine Kartendaten – wird ignoriert.");
            return;
        }

        InternalTask task = toInternalTask(card, action.getType());
        log.info("Verarbeite Task: {}", task);

        String prompt   = promptBuilder.build(task);
        String response = claudeClient.sendPrompt(prompt);

        trelloClient.addComment(task.getCardId(), formatComment(response));
    }

    private InternalTask toInternalTask(TrelloCardData card, String actionType) {
        return new InternalTask(
                card.getId(),
                card.getName() != null ? card.getName() : "(kein Titel)",
                card.getDesc() != null ? card.getDesc() : "",
                actionType
        );
    }

    private String formatComment(String claudeResponse) {
        return "🤖 **KI-Analyse:**\n\n" + claudeResponse;
    }
}
