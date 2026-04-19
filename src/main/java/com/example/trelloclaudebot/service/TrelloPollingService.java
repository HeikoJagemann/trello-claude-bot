package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.client.TrelloClient;
import com.example.trelloclaudebot.client.TrelloClient.BoardList;
import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.trello.TrelloAction;
import com.example.trelloclaudebot.dto.trello.TrelloCardData;
import com.example.trelloclaudebot.dto.trello.TrelloLabel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pollt das konfigurierte Trello-Board regelmäßig auf neue Actions.
 * Verarbeitet nur Actions, die seit dem letzten erfolgreichen Poll aufgetreten sind.
 * Kein öffentlicher Endpunkt notwendig.
 */
@Service
public class TrelloPollingService {

    private static final Logger log = LoggerFactory.getLogger(TrelloPollingService.class);

    /**
     * Maximale Anzahl gespeicherter Action-IDs. Verhindert unbegrenztes Wachstum
     * des Sets bei langer Laufzeit.
     */
    private static final int MAX_PROCESSED_IDS = 500;

    private final TrelloClient            trelloClient;
    private final TaskOrchestratorService orchestratorService;
    private final AppProperties           props;

    /** Zeitstempel des letzten Polls – nur Actions danach werden verarbeitet. */
    private volatile Instant lastPollTime;

    /**
     * Bereits verarbeitete Action-IDs.
     * Verhindert Mehrfachverarbeitung, da Label-Änderungen (z.B. "Ready" setzen)
     * selbst wieder updateCard-Actions erzeugen, die im nächsten Poll auftauchen.
     */
    private final Set<String> processedActionIds = new LinkedHashSet<>();

    public TrelloPollingService(TrelloClient trelloClient,
                                TaskOrchestratorService orchestratorService,
                                AppProperties props) {
        this.trelloClient        = trelloClient;
        this.orchestratorService = orchestratorService;
        this.props               = props;
    }

    @PostConstruct
    public void init() {
        lastPollTime = Instant.now();
        log.info("Trello Polling gestartet für Board '{}'. Polling-Intervall: {} ms",
                props.getTrello().getBoardId(),
                props.getTrello().getPollIntervalMs());
        scanExistingCards();
    }

    /**
     * Scannt beim Start alle relevanten Listen und verarbeitet vorhandene Karten sofort,
     * ohne dass eine Änderung an der Karte nötig ist.
     *
     * Reihenfolge (Priorität): Bugs → Sprint → Backlog (nur mit "Refinement"-Label).
     */
    private void scanExistingCards() {
        log.info("Startup-Scan: Suche vorhandene Karten in Bugs-, Sprint- und Backlog-Liste...");

        List<BoardList> lists = trelloClient.fetchBoardLists();
        if (lists.isEmpty()) {
            log.warn("Startup-Scan: Keine Listen gefunden – Scan übersprungen.");
            return;
        }

        String bugsName       = props.getTrello().getBugsListName();
        String sprintName     = props.getTrello().getSprintListName();
        String backlogName    = props.getTrello().getBacklogListName();
        String refinementName = props.getTrello().getRefinementLabelName();

        // Bugs zuerst (höchste Priorität)
        findList(lists, bugsName).ifPresent(list -> {
            List<TrelloCardData> cards = trelloClient.fetchCardsInList(list.getId());
            log.info("Startup-Scan: {} Karte(n) in '{}' gefunden (hohe Priorität).", cards.size(), bugsName);
            for (TrelloCardData card : cards) {
                log.info("Startup-Scan: Verarbeite Bug-Karte '{}'", card.getName());
                try {
                    orchestratorService.processCard(card, bugsName);
                    markProcessed(card.getId());
                } catch (Exception e) {
                    log.error("Startup-Scan: Fehler bei Bug-Karte '{}'", card.getName(), e);
                }
            }
        });

        // Sprint
        findList(lists, sprintName).ifPresent(list -> {
            List<TrelloCardData> cards = trelloClient.fetchCardsInList(list.getId());
            log.info("Startup-Scan: {} Karte(n) in '{}' gefunden.", cards.size(), sprintName);
            for (TrelloCardData card : cards) {
                log.info("Startup-Scan: Verarbeite Sprint-Karte '{}'", card.getName());
                try {
                    orchestratorService.processCard(card, sprintName);
                    markProcessed(card.getId());
                } catch (Exception e) {
                    log.error("Startup-Scan: Fehler bei Sprint-Karte '{}'", card.getName(), e);
                }
            }
        });

        // Backlog (nur Refinement-Karten)
        findList(lists, backlogName).ifPresent(list -> {
            List<TrelloCardData> cards = trelloClient.fetchCardsInList(list.getId());
            log.info("Startup-Scan: {} Karte(n) in '{}' gefunden.", cards.size(), backlogName);
            for (TrelloCardData card : cards) {
                List<TrelloLabel> labels = trelloClient.fetchCardLabels(card.getId());
                boolean hasRefinement = labels.stream()
                        .anyMatch(l -> refinementName.equalsIgnoreCase(l.getName()));
                if (!hasRefinement) {
                    log.debug("Startup-Scan: Karte '{}' hat kein '{}'-Label – übersprungen.",
                            card.getName(), refinementName);
                    continue;
                }
                log.info("Startup-Scan: Verarbeite Backlog-Karte '{}'", card.getName());
                try {
                    orchestratorService.processCard(card, backlogName);
                    markProcessed(card.getId());
                } catch (Exception e) {
                    log.error("Startup-Scan: Fehler bei Backlog-Karte '{}'", card.getName(), e);
                }
            }
        });

        log.info("Startup-Scan abgeschlossen.");
    }

    private Optional<BoardList> findList(List<BoardList> lists, String name) {
        return lists.stream()
                .filter(l -> name.equalsIgnoreCase(l.getName()))
                .findFirst();
    }

    @Scheduled(fixedDelayString = "${app.trello.poll-interval-ms:30000}")
    public void poll() {
        Instant pollStart = Instant.now();
        log.debug("Polling Trello seit {}", lastPollTime);

        List<TrelloAction> actions = trelloClient.fetchRecentActions(lastPollTime);

        if (actions.isEmpty()) {
            log.debug("Keine neuen Actions gefunden.");
        } else {
            log.info("{} neue Action(s) gefunden – starte Verarbeitung.", actions.size());

            String bugsName = props.getTrello().getBugsListName();

            // Trello liefert neueste zuerst → chronologisch sortieren,
            // dann Bug-Actions vor allen anderen verarbeiten (Priorität).
            List<TrelloAction> chronological = new java.util.ArrayList<>(actions);
            java.util.Collections.reverse(chronological);

            List<TrelloAction> bugActions   = new java.util.ArrayList<>();
            List<TrelloAction> otherActions = new java.util.ArrayList<>();
            for (TrelloAction action : chronological) {
                if (processedActionIds.contains(action.getId())) continue;
                String listName = extractListNameFromAction(action);
                if (bugsName.equalsIgnoreCase(listName)) {
                    bugActions.add(action);
                } else {
                    otherActions.add(action);
                }
            }

            if (!bugActions.isEmpty()) {
                log.info("{} Bug-Action(s) werden priorisiert verarbeitet.", bugActions.size());
            }

            for (TrelloAction action : bugActions) {
                log.info("Verarbeite Bug-Action: type={}, id={}", action.getType(), action.getId());
                try {
                    orchestratorService.process(action);
                } catch (Exception e) {
                    log.error("Fehler bei Verarbeitung von Bug-Action {}", action.getId(), e);
                }
                markProcessed(action.getId());
            }

            for (TrelloAction action : otherActions) {
                log.info("Verarbeite Action: type={}, id={}", action.getType(), action.getId());
                try {
                    orchestratorService.process(action);
                } catch (Exception e) {
                    log.error("Fehler bei Verarbeitung von Action {}", action.getId(), e);
                }
                markProcessed(action.getId());
            }
        }

        lastPollTime = pollStart;
    }

    /**
     * Liest den Listennamen aus einer Action ohne API-Call.
     * Gibt einen leeren String zurück, wenn nicht ermittelbar.
     */
    private String extractListNameFromAction(TrelloAction action) {
        if (action.getData() == null) return "";
        var list = action.getData().getList();
        if (list != null && list.getName() != null) return list.getName();
        var card = action.getData().getCard();
        if (card != null && card.getIdList() != null) {
            // idList ist keine Listenname – wir können hier keinen API-Call machen (Performance).
            // Daher: unbekannt → nicht als Bug-Action behandeln.
        }
        return "";
    }

    /** Merkt eine Action-ID als verarbeitet und begrenzt die Set-Größe. */
    private void markProcessed(String actionId) {
        if (processedActionIds.size() >= MAX_PROCESSED_IDS) {
            // Ältesten Eintrag entfernen (LinkedHashSet erhält Einfügereihenfolge)
            processedActionIds.remove(processedActionIds.iterator().next());
        }
        processedActionIds.add(actionId);
    }
}
