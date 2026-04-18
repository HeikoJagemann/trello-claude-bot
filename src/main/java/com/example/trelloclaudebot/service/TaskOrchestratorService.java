package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.client.TrelloClient;
import com.example.trelloclaudebot.client.TrelloClient.BoardList;
import com.example.trelloclaudebot.client.TrelloClient.TrelloChecklistRead;
import com.example.trelloclaudebot.client.TrelloClient.TrelloCustomField;
import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.internal.AnalysisResult;
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

    private final PromptBuilder         promptBuilder;
    private final ClaudeCodeRunner      claudeCodeRunner;
    private final AnalysisResultParser  analysisResultParser;
    private final TrelloClient          trelloClient;
    private final AppProperties         props;

    public TaskOrchestratorService(PromptBuilder promptBuilder,
                                   ClaudeCodeRunner claudeCodeRunner,
                                   AnalysisResultParser analysisResultParser,
                                   TrelloClient trelloClient,
                                   AppProperties props) {
        this.promptBuilder        = promptBuilder;
        this.claudeCodeRunner     = claudeCodeRunner;
        this.analysisResultParser = analysisResultParser;
        this.trelloClient         = trelloClient;
        this.props                = props;
    }

    /**
     * Routing-Logik basierend auf der Trello-Liste:
     *
     * Backlog  → nur wenn Label "Refinement" gesetzt:
     *            Claude Code CLI analysiert + schätzt Story Points,
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
        } else if (isSprint(listName)) {
            processImplementation(task);
        } else {
            log.info("Liste '{}' wird nicht verarbeitet – nur '{}' und '{}' sind aktiv.",
                    listName, props.getTrello().getBacklogListName(), props.getTrello().getSprintListName());
        }
    }

    // ── Analyse (Backlog) ─────────────────────────────────────────────────────

    /**
     * Backlog-Flow:
     * 1. Prüft, ob die Karte das "Refinement"-Label trägt. Falls nicht → überspringen.
     * 2. Claude Code CLI analysiert die Aufgabe (JSON-Output).
     * 3. Beschreibung, Story-Points-Custom-Field und Akzeptanzkriterien-Checkliste setzen.
     * 4. Label "Refinement" entfernen, Label "Ready" setzen.
     */
    private void processAnalysis(InternalTask task) {
        String refinementName      = props.getTrello().getRefinementLabelName();
        String readyName           = props.getTrello().getReadyLabelName();
        String storyPointsField    = props.getTrello().getStoryPointsFieldName();

        // Schritt 1 – Label-Prüfung
        List<TrelloLabel> cardLabels = trelloClient.fetchCardLabels(task.getCardId());
        Optional<TrelloLabel> refinementLabel = findLabel(cardLabels, refinementName);

        if (refinementLabel.isEmpty()) {
            log.info("Karte {} hat kein '{}'-Label – wird übersprungen.", task.getCardId(), refinementName);
            return;
        }

        log.info("Modus: Analyse + Story Points via Claude Code CLI (Liste: '{}', Label: '{}')",
                task.getListName(), refinementName);

        // Schritt 2 – Claude CLI aufrufen
        String prompt   = promptBuilder.buildAnalysisPrompt(task);
        String response = claudeCodeRunner.run(task, prompt);

        // Schritt 3 – JSON parsen und Karte befüllen
        AnalysisResult result = analysisResultParser.parse(response);

        if (result == null) {
            log.warn("Analyse-Ergebnis konnte nicht geparst werden – schreibe Rohausgabe als Kommentar.");
            trelloClient.addComment(task.getCardId(),
                    "⚠️ Analyse konnte nicht strukturiert verarbeitet werden.\n\nRohausgabe:\n" + response);
        } else {
            applyAnalysisToCard(task, result, storyPointsField);
        }

        // Schritt 4 – Labels tauschen
        swapLabels(task.getCardId(), refinementLabel.get(), readyName);
        log.info("Analyse für Karte {} abgeschlossen.", task.getCardId());
    }

    /**
     * Schreibt das Analyse-Ergebnis in die Karte:
     * - Beschreibung: Analyse + Begründung + Risiken als Markdown
     * - Custom Field: Story Points (Zahl)
     * - Checkliste: Akzeptanzkriterien
     */
    private void applyAnalysisToCard(InternalTask task, AnalysisResult result, String storyPointsFieldName) {
        // Beschreibung zusammenbauen
        String description = buildDescription(task, result);
        trelloClient.updateCardDescription(task.getCardId(), description);

        // Story Points als Custom Field
        setStoryPointsField(task.getCardId(), result.getStoryPoints(), storyPointsFieldName);

        // Akzeptanzkriterien als Checkliste
        trelloClient.createChecklistWithItems(
                task.getCardId(),
                "Akzeptanzkriterien",
                result.getAkzeptanzkriterien()
        );
    }

    private String buildDescription(InternalTask task, AnalysisResult result) {
        StringBuilder sb = new StringBuilder();

        // Ursprüngliche Aufgabenbeschreibung erhalten
        if (!task.getDescription().isBlank()) {
            sb.append(task.getDescription()).append("\n\n---\n\n");
        }

        sb.append("## Analyse\n").append(result.getAnalyse()).append("\n\n");
        sb.append("## Begründung (").append(result.getStoryPoints()).append(" Story Points)\n")
          .append(result.getBegruendung()).append("\n\n");

        if (!result.getRisiken().isEmpty()) {
            sb.append("## Risiken & Annahmen\n");
            result.getRisiken().forEach(r -> sb.append("- ").append(r).append("\n"));
        }

        return sb.toString().trim();
    }

    private void setStoryPointsField(String cardId, int storyPoints, String fieldName) {
        List<TrelloCustomField> fields = trelloClient.fetchBoardCustomFields();
        Optional<TrelloCustomField> field = fields.stream()
                .filter(f -> fieldName.equalsIgnoreCase(f.getName()))
                .findFirst();

        if (field.isEmpty()) {
            log.warn("Custom Field '{}' nicht auf dem Board gefunden – Story Points nicht gesetzt. " +
                     "Bitte das Custom Field im Trello-Board anlegen.", fieldName);
            return;
        }

        trelloClient.setCustomFieldNumber(cardId, field.get().getId(), storyPoints);
    }

    // ── Implementierung (alle anderen Listen) ─────────────────────────────────

    private void processImplementation(InternalTask task) {
        log.info("Modus: Implementierung via Claude Code CLI (Liste: '{}')", task.getListName());

        // Akzeptanzkriterien aus Trello-Checklisten laden
        List<String> akzeptanzkriterien = fetchAkzeptanzkriterien(task.getCardId());
        if (!akzeptanzkriterien.isEmpty()) {
            log.info("{} Akzeptanzkriterien für Karte {} geladen.", akzeptanzkriterien.size(), task.getCardId());
        }

        InternalTask enrichedTask = new InternalTask(
                task.getCardId(),
                task.getTitle(),
                task.getDescription(),
                task.getActionType(),
                task.getListName(),
                akzeptanzkriterien
        );

        String prompt  = promptBuilder.buildCodePrompt(enrichedTask);
        String summary = claudeCodeRunner.run(enrichedTask, prompt);

        trelloClient.addComment(task.getCardId(), summary);
        log.info("Implementierung für Karte {} abgeschlossen.", task.getCardId());

        // Alle Akzeptanzkriterien abhaken
        List<TrelloChecklistRead> checklists = trelloClient.fetchCardChecklists(task.getCardId());
        if (!checklists.isEmpty()) {
            log.info("Hake alle Checklisteneinträge auf Karte {} ab.", task.getCardId());
            trelloClient.checkAllCheckItems(task.getCardId(), checklists);
        }

        // Karte in QA-Liste verschieben
        moveCardToQa(task.getCardId());
    }

    /**
     * Holt alle Checklisten-Items der Karte und gibt sie als flache Liste zurück.
     * Mehrere Checklisten werden zusammengeführt (mit Checklisten-Name als Prefix falls > 1).
     */
    private List<String> fetchAkzeptanzkriterien(String cardId) {
        List<TrelloChecklistRead> checklists = trelloClient.fetchCardChecklists(cardId);
        if (checklists.isEmpty()) return java.util.Collections.emptyList();

        boolean multipleChecklists = checklists.size() > 1;
        List<String> items = new java.util.ArrayList<>();

        for (TrelloChecklistRead checklist : checklists) {
            for (TrelloChecklistRead.CheckItem item : checklist.getCheckItems()) {
                if (multipleChecklists) {
                    items.add("[" + checklist.getName() + "] " + item.getName());
                } else {
                    items.add(item.getName());
                }
            }
        }
        return items;
    }

    // ── Label-Verwaltung ──────────────────────────────────────────────────────

    private void swapLabels(String cardId, TrelloLabel refinementLabel, String readyLabelName) {
        trelloClient.removeLabelFromCard(cardId, refinementLabel.getId());

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

    private boolean isSprint(String listName) {
        return props.getTrello().getSprintListName().equalsIgnoreCase(listName);
    }

    private void moveCardToQa(String cardId) {
        String qaListName = props.getTrello().getQaListName();
        List<BoardList> lists = trelloClient.fetchBoardLists();
        Optional<BoardList> qaList = lists.stream()
                .filter(l -> qaListName.equalsIgnoreCase(l.getName()))
                .findFirst();

        if (qaList.isEmpty()) {
            log.warn("QA-Liste '{}' nicht auf dem Board gefunden – Karte {} bleibt in Sprint.", qaListName, cardId);
            return;
        }

        trelloClient.moveCardToList(cardId, qaList.get().getId());
        log.info("Karte {} in Liste '{}' verschoben.", cardId, qaListName);
    }

    private String extractListName(TrelloActionData data) {
        if (data.getList() != null && data.getList().getName() != null) {
            return data.getList().getName();
        }
        if (data.getCard() != null && data.getCard().getIdList() != null) {
            String listName = trelloClient.fetchListName(data.getCard().getIdList());
            log.debug("Listenname per API nachgeladen: '{}'", listName);
            return listName;
        }
        log.warn("Listenname konnte nicht ermittelt werden – weder data.list noch data.card.idList vorhanden.");
        return "";
    }
}
