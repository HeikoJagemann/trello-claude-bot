package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.client.TrelloClient;
import com.example.trelloclaudebot.client.TrelloClient.BoardList;
import com.example.trelloclaudebot.service.ClaudeCodeRunner.RunResult;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TaskOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestratorService.class);

    private final PromptBuilder              promptBuilder;
    private final ClaudeCodeRunner           claudeCodeRunner;
    private final AnalysisResultParser       analysisResultParser;
    private final TrelloClient               trelloClient;
    private final GitService                 gitService;
    private final AppProperties              props;
    private final ScheduledExecutorService   scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "claude-retry-scheduler");
                t.setDaemon(true);
                return t;
            });

    public TaskOrchestratorService(PromptBuilder promptBuilder,
                                   ClaudeCodeRunner claudeCodeRunner,
                                   AnalysisResultParser analysisResultParser,
                                   TrelloClient trelloClient,
                                   GitService gitService,
                                   AppProperties props) {
        this.promptBuilder        = promptBuilder;
        this.claudeCodeRunner     = claudeCodeRunner;
        this.analysisResultParser = analysisResultParser;
        this.trelloClient         = trelloClient;
        this.gitService           = gitService;
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
        processCard(card, listName, action.getType());
    }

    /**
     * Verarbeitet eine Karte direkt (ohne TrelloAction) – wird beim Startup-Scan genutzt.
     */
    public void processCard(TrelloCardData card, String listName) {
        processCard(card, listName, "startupScan");
    }

    private void processCard(TrelloCardData card, String listName, String actionType) {
        InternalTask task = new InternalTask(
                card.getId(),
                card.getName() != null ? card.getName() : "(kein Titel)",
                card.getDesc() != null ? card.getDesc() : "",
                actionType,
                listName
        );

        log.info("Verarbeite Task: {}", task);

        if (isBacklog(listName)) {
            processAnalysis(task);
        } else if (isBugs(listName)) {
            log.info("Modus: Bug-Fix via Claude Code CLI (Liste: '{}') – hohe Priorität", task.getListName());
            processImplementation(task);
        } else if (isSprint(listName)) {
            processImplementation(task);
        } else {
            log.info("Liste '{}' wird nicht verarbeitet – aktive Listen: '{}', '{}', '{}'.",
                    listName,
                    props.getTrello().getBacklogListName(),
                    props.getTrello().getBugsListName(),
                    props.getTrello().getSprintListName());
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
        String prompt      = promptBuilder.buildAnalysisPrompt(task);
        RunResult runResult = claudeCodeRunner.run(task, prompt);
        String response    = runResult.text();

        if (runResult.hasTokenInfo()) {
            log.info("Analyse-Tokens für Karte {}: Input={}, Output={}, Kosten=${}",
                    task.getCardId(), runResult.inputTokens(), runResult.outputTokens(),
                    String.format("%.4f", runResult.costUsd()));
        }

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
        processImplementationWithRetry(task, null, 0);
    }

    private void processImplementationWithRetry(InternalTask task, String sessionId, int attempt) {
        int maxRetries = props.getClaudeCode().getMaxRetries();

        if (attempt > maxRetries) {
            log.error("Maximale Wiederholungsanzahl ({}) erreicht für Karte {} – abgebrochen.", maxRetries, task.getCardId());
            trelloClient.addComment(task.getCardId(),
                    "❌ Maximale Anzahl Versuche (" + maxRetries + ") erreicht – manuelle Bearbeitung nötig.");
            return;
        }

        // Offene Akzeptanzkriterien mit IDs laden
        List<CheckItemRef> checkItems = fetchIncompleteCheckItems(task.getCardId());
        List<String> kriterienNamen = checkItems.stream().map(CheckItemRef::displayName).toList();

        InternalTask enrichedTask = new InternalTask(
                task.getCardId(), task.getTitle(), task.getDescription(),
                task.getActionType(), task.getListName(), kriterienNamen);

        String headBefore = gitService.getCurrentHead();

        RunResult runResult;
        if (sessionId != null && !sessionId.isBlank()) {
            log.info("Resume Versuch {} von {} für Karte '{}'", attempt, maxRetries, task.getCardId());
            String continuationPrompt = """
                    Bitte mache weiter wo du aufgehört hast.
                    Vergiss nicht, am Ende die ERLEDIGT-Zeile zu schreiben (z.B. ERLEDIGT: 1, 2).
                    """;
            runResult = claudeCodeRunner.resume(enrichedTask, sessionId, continuationPrompt);
        } else {
            String prompt = promptBuilder.buildCodePrompt(enrichedTask);
            runResult = claudeCodeRunner.run(enrichedTask, prompt);
        }

        // Rate-Limit → warten und neu versuchen
        if (runResult.isRateLimit()) {
            long waitMinutes = props.getClaudeCode().getRateLimitWaitMinutes();
            log.warn("Rate-Limit für Karte {} – warte {} Minuten (Versuch {}/{}).",
                    task.getCardId(), waitMinutes, attempt + 1, maxRetries);
            trelloClient.addComment(task.getCardId(),
                    "⏳ Token-Limit erreicht – warte " + waitMinutes + " Minuten, dann Versuch "
                            + (attempt + 1) + "/" + maxRetries + ".");
            String nextSessionId = runResult.sessionId().isBlank() ? sessionId : runResult.sessionId();
            scheduler.schedule(
                    () -> processImplementationWithRetry(task, nextSessionId, attempt + 1),
                    waitMinutes, TimeUnit.MINUTES);
            return;
        }

        // Commit + Push
        gitService.commitIfNeeded("Implementierung: " + task.getTitle());
        boolean pushed    = gitService.push();
        List<String> newCommits = gitService.getCommitsSince(headBefore);

        // Kommentar
        String comment = buildImplementationComment(runResult.text(), pushed, newCommits, runResult);
        trelloClient.addComment(task.getCardId(), comment);

        // Erledigte Kriterien abhaken
        if (!checkItems.isEmpty()) {
            Set<Integer> erledigtIndices = parseErledigtIndices(runResult.text(), checkItems.size());
            if (erledigtIndices.isEmpty()) {
                log.info("Keine ERLEDIGT-Zeile – keine Kriterien abgehakt.");
            } else {
                log.info("Hake {} von {} Kriterien auf Karte {} ab: {}",
                        erledigtIndices.size(), checkItems.size(), task.getCardId(), erledigtIndices);
                for (int i = 0; i < checkItems.size(); i++) {
                    if (erledigtIndices.contains(i + 1)) {
                        CheckItemRef ref = checkItems.get(i);
                        trelloClient.markCheckItemComplete(task.getCardId(), ref.checklistId(), ref.itemId());
                    }
                }
            }
        }

        // Noch offene Kriterien → Resume planen
        List<CheckItemRef> nochOffen = fetchIncompleteCheckItems(task.getCardId());
        if (!nochOffen.isEmpty() && attempt < maxRetries) {
            String nextSessionId = runResult.sessionId().isBlank() ? sessionId : runResult.sessionId();
            if (nextSessionId != null && !nextSessionId.isBlank()) {
                log.info("{} Kriterien noch offen – plane Resume für Karte {} (Versuch {}/{}).",
                        nochOffen.size(), task.getCardId(), attempt + 1, maxRetries);
                trelloClient.addComment(task.getCardId(),
                        "🔄 Noch " + nochOffen.size() + " offene Kriterien – starte Resume (Versuch "
                                + (attempt + 1) + "/" + maxRetries + ").");
                scheduler.schedule(
                        () -> processImplementationWithRetry(task, nextSessionId, attempt + 1),
                        1, TimeUnit.SECONDS);
            } else {
                log.warn("Keine Session-ID verfügbar – Resume nicht möglich. Karte {} bleibt in Sprint.", task.getCardId());
            }
            return;
        }

        // Alle erledigt → nach QA
        if (nochOffen.isEmpty()) {
            log.info("Alle Kriterien erledigt – verschiebe Karte {} in QA.", task.getCardId());
            moveCardToQa(task.getCardId());
        } else {
            log.warn("Max. Versuche erreicht, {} Kriterien noch offen – Karte {} bleibt in Sprint.",
                    nochOffen.size(), task.getCardId());
            trelloClient.addComment(task.getCardId(),
                    "⚠️ Nach " + maxRetries + " Versuchen noch " + nochOffen.size()
                            + " offene Kriterien – bitte manuell prüfen.");
        }
        log.info("Implementierung für Karte {} abgeschlossen (Versuch {}).", task.getCardId(), attempt + 1);
    }

    /** Referenz auf ein einzelnes Checklist-Item inkl. IDs für die Trello API. */
    private record CheckItemRef(String checklistId, String itemId, String displayName) {}

    /**
     * Gibt alle noch nicht abgehakten Checklist-Items zurück, mit Checklist- und Item-ID
     * für das selektive Abhaken nach der Implementierung.
     */
    private List<CheckItemRef> fetchIncompleteCheckItems(String cardId) {
        List<TrelloChecklistRead> checklists = trelloClient.fetchCardChecklists(cardId);
        if (checklists.isEmpty()) return java.util.Collections.emptyList();

        boolean multipleChecklists = checklists.size() > 1;
        List<CheckItemRef> refs = new java.util.ArrayList<>();

        for (TrelloChecklistRead checklist : checklists) {
            for (TrelloChecklistRead.CheckItem item : checklist.getCheckItems()) {
                if ("complete".equals(item.getState())) continue;
                String displayName = multipleChecklists
                        ? "[" + checklist.getName() + "] " + item.getName()
                        : item.getName();
                refs.add(new CheckItemRef(checklist.getId(), item.getId(), displayName));
            }
        }

        log.info("{} offene Akzeptanzkriterien für Karte {} geladen.", refs.size(), cardId);
        return refs;
    }

    /**
     * Parst die "ERLEDIGT: 1, 2, 3"-Zeile aus Claudes Ausgabe.
     * Gibt die 1-basierten Indices der erledigten Kriterien zurück.
     */
    private Set<Integer> parseErledigtIndices(String output, int totalItems) {
        Set<Integer> indices = new HashSet<>();
        if (output == null || output.isBlank()) return indices;

        for (String line : output.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("ERLEDIGT:")) {
                String numberPart = trimmed.substring("ERLEDIGT:".length()).trim();
                for (String token : numberPart.split("[,\\s]+")) {
                    try {
                        int idx = Integer.parseInt(token.trim());
                        if (idx >= 1 && idx <= totalItems) {
                            indices.add(idx);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                break;
            }
        }
        return indices;
    }

    private String buildImplementationComment(String summary, boolean pushed,
                                               List<String> commits, RunResult runResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(summary.isBlank() ? "✅ Implementierung abgeschlossen." : summary.trim());

        if (!commits.isEmpty()) {
            sb.append("\n\n---\n**Commits:**\n");
            commits.forEach(c -> sb.append("- `").append(c).append("`\n"));
        }

        if (!pushed) {
            sb.append("\n\n⚠️ `git push` fehlgeschlagen – bitte manuell pushen.");
        }

        sb.append(runResult.tokenSummary());

        return sb.toString();
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

    private boolean isBugs(String listName) {
        return props.getTrello().getBugsListName().equalsIgnoreCase(listName);
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
