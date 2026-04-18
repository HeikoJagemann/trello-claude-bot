package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.client.ClaudeClient;
import com.example.trelloclaudebot.client.TrelloClient;
import com.example.trelloclaudebot.dto.internal.ApplyResult;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import com.example.trelloclaudebot.dto.trello.TrelloAction;
import com.example.trelloclaudebot.dto.trello.TrelloCardData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestratorService.class);

    private final PromptBuilder promptBuilder;
    private final ClaudeClient  claudeClient;
    private final ApplyEngine   applyEngine;
    private final TrelloClient  trelloClient;

    public TaskOrchestratorService(PromptBuilder promptBuilder,
                                   ClaudeClient claudeClient,
                                   ApplyEngine applyEngine,
                                   TrelloClient trelloClient) {
        this.promptBuilder = promptBuilder;
        this.claudeClient  = claudeClient;
        this.applyEngine   = applyEngine;
        this.trelloClient  = trelloClient;
    }

    /**
     * Vollständiger Verarbeitungsfluss für eine Trello-Action:
     *
     * 1. Kartendaten → InternalTask
     * 2. Strukturierten Prompt bauen
     * 3. Claude API aufrufen (erwartet FILE-Blöcke im Response)
     * 4. ApplyEngine parst Response und schreibt Dateien
     * 5. Summary als Kommentar auf die Karte schreiben
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

        // Schritt 1: Prompt mit strukturiertem Ausgabeformat bauen
        String prompt = promptBuilder.build(task);

        // Schritt 2: Claude API aufrufen
        String claudeResponse = claudeClient.sendPrompt(prompt);

        // Schritt 3: FILE-Blöcke parsen und auf Disk schreiben
        List<ApplyResult> results = applyEngine.apply(claudeResponse);

        // Schritt 4: Summary als Trello-Kommentar
        String summary = applyEngine.buildSummary(results);
        trelloClient.addComment(task.getCardId(), summary);

        log.info("Task {} abgeschlossen. {} Datei(en) geschrieben.",
                task.getCardId(),
                results.stream().filter(r -> r.getStatus() == ApplyResult.Status.WRITTEN).count());
    }
}
