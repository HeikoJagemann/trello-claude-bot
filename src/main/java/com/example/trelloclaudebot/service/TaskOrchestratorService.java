package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.client.ClaudeClient;
import com.example.trelloclaudebot.client.TrelloClient;
import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.internal.ApplyResult;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import com.example.trelloclaudebot.dto.trello.TrelloAction;
import com.example.trelloclaudebot.dto.trello.TrelloActionData;
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
    private final AppProperties props;

    public TaskOrchestratorService(PromptBuilder promptBuilder,
                                   ClaudeClient claudeClient,
                                   ApplyEngine applyEngine,
                                   TrelloClient trelloClient,
                                   AppProperties props) {
        this.promptBuilder = promptBuilder;
        this.claudeClient  = claudeClient;
        this.applyEngine   = applyEngine;
        this.trelloClient  = trelloClient;
        this.props         = props;
    }

    /**
     * Routing-Logik basierend auf der Trello-Liste:
     *
     * Backlog  → Analyse + Story-Point-Schätzung als Kommentar
     * Andere   → Code-Generierung via ApplyEngine + Summary-Kommentar
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

        String listName = extractListName(action.getData());
        InternalTask task = new InternalTask(
                card.getId(),
                card.getName() != null ? card.getName() : "(kein Titel)",
                card.getDesc() != null ? card.getDesc() : "",
                action.getType(),
                listName
        );

        log.info("Verarbeite Task: {}", task);

        if (isBacklog(listName)) {
            processAnalysis(task);
        } else {
            processCodeGeneration(task);
        }
    }

    // ── Analyse (Backlog) ─────────────────────────────────────────────────────

    /**
     * Backlog-Flow: Claude analysiert die Aufgabe und schätzt Story Points.
     * Ergebnis wird direkt als Kommentar auf die Karte geschrieben.
     */
    private void processAnalysis(InternalTask task) {
        log.info("Modus: Analyse + Story Points (Liste: '{}')", task.getListName());

        String prompt   = promptBuilder.buildAnalysisPrompt(task);
        String response = claudeClient.sendPrompt(prompt);

        trelloClient.addComment(task.getCardId(), response);
        log.info("Analyse für Karte {} abgeschlossen.", task.getCardId());
    }

    // ── Code-Generierung (alle anderen Listen) ────────────────────────────────

    /**
     * Code-Generierungs-Flow: Claude gibt FILE-Blöcke zurück,
     * ApplyEngine schreibt sie auf Disk, Summary wird als Kommentar geschrieben.
     */
    private void processCodeGeneration(InternalTask task) {
        log.info("Modus: Code-Generierung (Liste: '{}')", task.getListName());

        String prompt         = promptBuilder.buildCodePrompt(task);
        String claudeResponse = claudeClient.sendPrompt(prompt);
        List<ApplyResult> results = applyEngine.apply(claudeResponse);
        String summary        = applyEngine.buildSummary(results);

        trelloClient.addComment(task.getCardId(), summary);
        log.info("Code-Generierung für Karte {} abgeschlossen. {} Datei(en) geschrieben.",
                task.getCardId(),
                results.stream().filter(r -> r.getStatus() == ApplyResult.Status.WRITTEN).count());
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private boolean isBacklog(String listName) {
        return props.getTrello().getBacklogListName().equalsIgnoreCase(listName);
    }

    private String extractListName(TrelloActionData data) {
        if (data.getList() != null && data.getList().getName() != null) {
            return data.getList().getName();
        }
        return "";
    }
}
