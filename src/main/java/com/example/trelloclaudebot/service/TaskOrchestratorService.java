package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.client.ClaudeClient;
import com.example.trelloclaudebot.client.TrelloClient;
import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import com.example.trelloclaudebot.dto.trello.TrelloAction;
import com.example.trelloclaudebot.dto.trello.TrelloActionData;
import com.example.trelloclaudebot.dto.trello.TrelloCardData;
import com.example.trelloclaudebot.dto.trello.TrelloLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
     * Backlog  → nur wenn Label "Refinement" gesetzt:
     *            Claude API analysiert + schätzt Story Points,
     *            danach Label "Refinement" entfernen, Label "Ready" setzen
     * Andere   → Claude Code CLI implementiert direkt im Repo, Summary als Kommentar
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
     * Backlog-Flow:
     * 1. Prüft, ob die Karte das "Refinement"-Label trägt. Falls nicht → überspringen.
     * 2. Claude API analysiert die Aufgabe und schätzt Story Points.
     * 3. Ergebnis als Kommentar auf die Karte.
     * 4. Label "Refinement" entfernen, Label "Ready" setzen.
     */
    private void processAnalysis(InternalTask task) {
        String refinementName = props.getTrello().getRefinementLabelName();
        String readyName      = props.getTrello().getReadyLabelName();

        // Schritt 1 – Label-Prüfung
        List<TrelloLabel> cardLabels = trelloClient.fetchCardLabels(task.getCardId());
        Optional<TrelloLabel> refinementLabel = findLabel(cardLabels, refinementName);

        if (refinementLabel.isEmpty()) {
            log.info("Karte {} hat kein '{}'-Label – wird übersprungen.", task.getCardId(), refinementName);
            return;
        }

        log.info("Modus: Analyse + Story Points via Claude API (Liste: '{}', Label: '{}')",
                task.getListName(), refinementName);

        // Schritt 2 & 3 – Analyse + Kommentar
        String prompt   = promptBuilder.buildAnalysisPrompt(task);
        String response = claudeClient.sendPrompt(prompt);
        trelloClient.addComment(task.getCardId(), response);

        // Schritt 4 – Labels tauschen
        swapLabels(task.getCardId(), refinementLabel.get(), readyName);

        log.info("Analyse für Karte {} abgeschlossen.", task.getCardId());
    }

    // ── Implementierung (alle anderen Listen) ─────────────────────────────────

    /**
     * Implementierungs-Flow: Claude Code CLI arbeitet direkt im Repo.
     */
    private void processImplementation(InternalTask task) {
        log.info("Modus: Implementierung via Claude Code CLI (Liste: '{}')", task.getListName());

        String prompt  = promptBuilder.buildCodePrompt(task);
        String summary = claudeCodeRunner.run(task, prompt);

        trelloClient.addComment(task.getCardId(), summary);
        log.info("Implementierung für Karte {} abgeschlossen.", task.getCardId());
    }

    // ── Label-Verwaltung ──────────────────────────────────────────────────────

    /**
     * Entfernt das "Refinement"-Label und setzt das "Ready"-Label.
     * Wenn "Ready" auf dem Board nicht gefunden wird, wird nur entfernt.
     */
    private void swapLabels(String cardId, TrelloLabel refinementLabel, String readyLabelName) {
        // "Refinement" entfernen
        trelloClient.removeLabelFromCard(cardId, refinementLabel.getId());

        // "Ready" auf dem Board nachschlagen
        List<TrelloLabel> boardLabels = trelloClient.fetchBoardLabels();
        Optional<TrelloLabel> readyLabel = findLabel(boardLabels, readyLabelName);

        if (readyLabel.isEmpty()) {
            log.warn("Label '{}' nicht auf dem Board gefunden – nur '{}' entfernt.",
                    readyLabelName, refinementLabel.getName());
            return;
        }

        trelloClient.addLabelToCard(cardId, readyLabel.get().getId());
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private Optional<TrelloLabel> findLabel(List<TrelloLabel> labels, String name) {
        return labels.stream()
                .filter(l -> name.equalsIgnoreCase(l.getName()))
                .findFirst();
    }

    private boolean isBacklog(String listName) {
        return props.getTrello().getBacklogListName().equalsIgnoreCase(listName);
    }

    /**
     * Ermittelt den Listen-Namen aus der Action-Data.
     *
     * Trello befüllt {@code data.list} nur bei Karten-Bewegungen (createCard, moveCard).
     * Bei updateCard-Actions (Label/Beschreibung ändern) fehlt das Feld – in diesem Fall
     * wird die Liste über {@code data.card.idList} nachgeladen.
     */
    private String extractListName(TrelloActionData data) {
        if (data.getList() != null && data.getList().getName() != null) {
            return data.getList().getName();
        }
        // Fallback: idList aus der Karte → Listenname per API nachladen
        if (data.getCard() != null && data.getCard().getIdList() != null) {
            String listName = trelloClient.fetchListName(data.getCard().getIdList());
            log.debug("Listenname per API nachgeladen: '{}'", listName);
            return listName;
        }
        log.warn("Listenname konnte nicht ermittelt werden – weder data.list noch data.card.idList vorhanden.");
        return "";
    }
}
