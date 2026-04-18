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
     * Sprint-Liste: alle Karten werden implementiert.
     * Backlog-Liste: nur Karten mit "Refinement"-Label werden analysiert.
     */
    private void scanExistingCards() {
        log.info("Startup-Scan: Suche vorhandene Karten in Sprint- und Backlog-Liste...");

        List<BoardList> lists = trelloClient.fetchBoardLists();
        if (lists.isEmpty()) {
            log.warn("Startup-Scan: Keine Listen gefunden – Scan übersprungen.");
            return;
        }

        String sprintName    = props.getTrello().getSprintListName();
        String backlogName   = props.getTrello().getBacklogListName();
        String refinementName = props.getTrello().getRefinementLabelName();

        for (BoardList list : lists) {
            if (sprintName.equalsIgnoreCase(list.getName())) {
                List<TrelloCardData> cards = trelloClient.fetchCardsInList(list.getId());
                log.info("Startup-Scan: {} Karte(n) in '{}' gefunden.", cards.size(), sprintName);
                for (TrelloCardData card : cards) {
                    log.info("Startup-Scan: Verarbeite Sprint-Karte '{}'", card.getName());
                    try {
                        orchestratorService.processCard(card, sprintName);
                        markProcessed(card.getId());
                    } catch (Exception e) {
                        log.error("Startup-Scan: Fehler bei Karte '{}'", card.getName(), e);
                    }
                }

            } else if (backlogName.equalsIgnoreCase(list.getName())) {
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
                        log.error("Startup-Scan: Fehler bei Karte '{}'", card.getName(), e);
                    }
                }
            }
        }

        log.info("Startup-Scan abgeschlossen.");
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
            // Trello liefert neueste zuerst → umgekehrt verarbeiten (chronologisch)
            for (int i = actions.size() - 1; i >= 0; i--) {
                TrelloAction action = actions.get(i);

                if (processedActionIds.contains(action.getId())) {
                    log.debug("Action {} bereits verarbeitet – übersprungen.", action.getId());
                    continue;
                }

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

    /** Merkt eine Action-ID als verarbeitet und begrenzt die Set-Größe. */
    private void markProcessed(String actionId) {
        if (processedActionIds.size() >= MAX_PROCESSED_IDS) {
            // Ältesten Eintrag entfernen (LinkedHashSet erhält Einfügereihenfolge)
            processedActionIds.remove(processedActionIds.iterator().next());
        }
        processedActionIds.add(actionId);
    }
}
