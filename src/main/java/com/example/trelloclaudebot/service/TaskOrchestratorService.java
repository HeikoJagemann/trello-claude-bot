package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.client.ClaudeClient;
import com.example.trelloclaudebot.client.TrelloClient;
import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import com.example.trelloclaudebot.dto.trello.TrelloAction;
import com.example.trelloclaudebot.dto.trello.TrelloActionData;
import com.example.trelloclaudebot.dto.trello.TrelloCardData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestratorService.class);

    private final PromptBuilder     promptBuilder;
    private final ClaudeClient      claudeClient;
    private final ClaudeCodeRunner  claudeCodeRunner;
    private final TrelloClient      trelloClient;
    private final AppProperties     props;

    public TaskOrchestratorService(PromptBuilder promptBuilder,
                                   ClaudeClient claudeClient,
                                   ClaudeCodeRunner claudeCodeRunner,
                                   TrelloClient trelloClient,
                                   AppProperties props) {
        this.promptBuilder    = promptBuilder;
        this.claudeClient     = claudeClient;
        this.claudeCodeRunner = claudeCodeRunner;
        this.trelloClient     = trelloClient;
        this.props            = props;
    }

    /**
     * Routing-Logik basierend auf der Trello-Liste:
     *
     * Backlog  → Claude API: Analyse + Story-Point-Schätzung als Kommentar
     * Andere   → Claude Code CLI: implementiert direkt im Repo, Summary als Kommentar
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
            processImplementation(task);
        }
    }

    // ── Analyse (Backlog) ─────────────────────────────────────────────────────

    /**
     * Backlog-Flow: Claude API analysiert die Aufgabe und schätzt Story Points.
     * Kein Repo-Zugriff nötig – reine Textanalyse.
     */
    private void processAnalysis(InternalTask task) {
        log.info("Modus: Analyse + Story Points via Claude API (Liste: '{}')", task.getListName());

        String prompt   = promptBuilder.buildAnalysisPrompt(task);
        String response = claudeClient.sendPrompt(prompt);

        trelloClient.addComment(task.getCardId(), response);
        log.info("Analyse für Karte {} abgeschlossen.", task.getCardId());
    }

    // ── Implementierung (alle anderen Listen) ─────────────────────────────────

    /**
     * Implementierungs-Flow: Claude Code CLI arbeitet direkt im Repo.
     * Hat nativen Zugriff auf alle Dateien via Read/Edit/Write/Bash-Tools.
     * Die Ausgabe (Zusammenfassung der Änderungen) wird als Kommentar geschrieben.
     */
    private void processImplementation(InternalTask task) {
        log.info("Modus: Implementierung via Claude Code CLI (Liste: '{}')", task.getListName());

        String prompt  = promptBuilder.buildCodePrompt(task);
        String summary = claudeCodeRunner.run(task, prompt);

        trelloClient.addComment(task.getCardId(), summary);
        log.info("Implementierung für Karte {} abgeschlossen.", task.getCardId());
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
