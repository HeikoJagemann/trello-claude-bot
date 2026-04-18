package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.client.ClaudeClient;
import com.example.trelloclaudebot.client.TrelloClient;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import com.example.trelloclaudebot.dto.trello.TrelloAction;
import com.example.trelloclaudebot.dto.trello.TrelloCardData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestratorService.class);

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
     * Verarbeitet eine einzelne Trello-Action.
     * Extrahiert Kartendaten, generiert einen Prompt, ruft Claude auf
     * und schreibt die Antwort als Kommentar zurück auf die Karte.
     */
    public void process(TrelloAction action) {
        if (action.getData() == null) {
            log.warn("Action {} enthält keine Data – wird übersprungen.", action.getId());
            return;
        }

        TrelloCardData card = action.getData().getCard();
        if (card == null || card.getId() == null) {
            log.warn("Action {} enthält keine Kartendaten – wird übersprungen.", action.getId());
            return;
        }

        InternalTask task = new InternalTask(
                card.getId(),
                card.getName() != null ? card.getName() : "(kein Titel)",
                card.getDesc() != null ? card.getDesc() : "",
                action.getType()
        );

        log.info("Verarbeite Task: {}", task);

        String prompt   = promptBuilder.build(task);
        String response = claudeClient.sendPrompt(prompt);

        trelloClient.addComment(task.getCardId(), "🤖 **KI-Analyse:**\n\n" + response);
    }
}
