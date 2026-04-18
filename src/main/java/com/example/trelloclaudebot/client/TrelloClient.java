package com.example.trelloclaudebot.client;

import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.trello.TrelloAction;
import com.example.trelloclaudebot.dto.trello.TrelloLabel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TrelloClient {

    private static final Logger log = LoggerFactory.getLogger(TrelloClient.class);

    private static final String ACTION_FILTER = "createCard,updateCard";

    private final WebClient     webClient;
    private final AppProperties props;

    public TrelloClient(@Qualifier("trelloWebClient") WebClient webClient,
                        AppProperties props) {
        this.webClient = webClient;
        this.props     = props;
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    /**
     * Ruft alle relevanten Actions des konfigurierten Boards ab, die nach {@code since} aufgetreten sind.
     *
     * @param since Nur Actions nach diesem Zeitpunkt werden zurückgegeben
     * @return Liste der Actions, neueste zuerst
     */
    public List<TrelloAction> fetchRecentActions(Instant since) {
        String boardId  = props.getTrello().getBoardId();
        // Trello API versteht nur Millisekunden-Präzision – Nanosekunden abschneiden
        String sinceStr = DateTimeFormatter.ISO_INSTANT.format(since.truncatedTo(ChronoUnit.MILLIS));

        log.debug("Polling Trello Board {} seit {}", boardId, sinceStr);

        try {
            List<TrelloAction> actions = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/boards/{boardId}/actions")
                            .queryParam("key",    props.getTrello().getApiKey())
                            .queryParam("token",  props.getTrello().getApiToken())
                            .queryParam("filter", ACTION_FILTER)
                            .queryParam("since",  sinceStr)
                            .queryParam("limit",  "50")
                            .build(boardId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<TrelloAction>>() {})
                    .block();

            return actions != null ? actions : Collections.emptyList();

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Polling: HTTP {} – {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Trello Polling", e);
            return Collections.emptyList();
        }
    }

    // ── Listen ───────────────────────────────────────────────────────────────

    /**
     * Gibt den Namen der Liste zurück, in der eine Karte liegt.
     * Wird als Fallback genutzt, wenn die Action-Data kein {@code list}-Objekt enthält
     * (z.B. bei updateCard-Actions für Label- oder Beschreibungsänderungen).
     *
     * @param listId ID der Trello-Liste
     * @return Listenname oder leerer String bei Fehler
     */
    public String fetchListName(String listId) {
        try {
            ListNameResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/lists/{listId}")
                            .queryParam("fields", "name")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(listId))
                    .retrieve()
                    .bodyToMono(ListNameResponse.class)
                    .block();

            return response != null && response.name != null ? response.name : "";

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Laden des Listennamens für {}: HTTP {} – {}",
                    listId, e.getStatusCode(), e.getResponseBodyAsString());
            return "";
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Laden des Listennamens für {}", listId, e);
            return "";
        }
    }

    // ── Labels ────────────────────────────────────────────────────────────────

    /**
     * Gibt alle Labels zurück, die aktuell auf der angegebenen Karte sind.
     */
    public List<TrelloLabel> fetchCardLabels(String cardId) {
        try {
            CardLabelsResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards/{cardId}")
                            .queryParam("fields", "labels")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(cardId))
                    .retrieve()
                    .bodyToMono(CardLabelsResponse.class)
                    .block();

            return response != null && response.labels != null ? response.labels : Collections.emptyList();

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Laden der Karten-Labels {}: HTTP {} – {}",
                    cardId, e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Laden der Karten-Labels {}", cardId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Gibt alle Labels zurück, die auf dem konfigurierten Board definiert sind.
     * Wird genutzt, um Label-IDs anhand des Namens nachzuschlagen.
     */
    public List<TrelloLabel> fetchBoardLabels() {
        String boardId = props.getTrello().getBoardId();

        try {
            List<TrelloLabel> labels = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/boards/{boardId}/labels")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(boardId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<TrelloLabel>>() {})
                    .block();

            return labels != null ? labels : Collections.emptyList();

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Laden der Board-Labels: HTTP {} – {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Laden der Board-Labels", e);
            return Collections.emptyList();
        }
    }

    /**
     * Entfernt ein Label von einer Karte.
     *
     * @param cardId  ID der Karte
     * @param labelId ID des zu entfernenden Labels
     */
    public void removeLabelFromCard(String cardId, String labelId) {
        log.info("Entferne Label {} von Karte {}", labelId, cardId);

        try {
            webClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards/{cardId}/idLabels/{labelId}")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(cardId, labelId))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Label {} erfolgreich von Karte {} entfernt.", labelId, cardId);

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Entfernen von Label {}: HTTP {} – {}",
                    labelId, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Entfernen von Label {} von Karte {}", labelId, cardId, e);
        }
    }

    /**
     * Fügt ein Label zu einer Karte hinzu.
     *
     * @param cardId  ID der Karte
     * @param labelId ID des hinzuzufügenden Labels
     */
    public void addLabelToCard(String cardId, String labelId) {
        log.info("Füge Label {} zu Karte {} hinzu", labelId, cardId);

        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards/{cardId}/idLabels")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .queryParam("value", labelId)
                            .build(cardId))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Label {} erfolgreich zu Karte {} hinzugefügt.", labelId, cardId);

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Hinzufügen von Label {}: HTTP {} – {}",
                    labelId, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Hinzufügen von Label {} zu Karte {}", labelId, cardId, e);
        }
    }

    // ── Checklisten lesen ────────────────────────────────────────────────────

    /**
     * Gibt alle Checklisten einer Karte zurück, jeweils mit ihren Items.
     */
    public List<TrelloChecklistRead> fetchCardChecklists(String cardId) {
        try {
            List<TrelloChecklistRead> checklists = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards/{cardId}/checklists")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(cardId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<TrelloChecklistRead>>() {})
                    .block();

            return checklists != null ? checklists : Collections.emptyList();

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Laden der Checklisten für Karte {}: HTTP {} – {}",
                    cardId, e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Laden der Checklisten für Karte {}", cardId, e);
            return Collections.emptyList();
        }
    }

    // ── Karte aktualisieren ───────────────────────────────────────────────────

    /**
     * Setzt die Beschreibung einer Karte.
     * Bestehender Inhalt wird überschrieben.
     */
    public void updateCardDescription(String cardId, String description) {
        log.info("Aktualisiere Beschreibung von Karte {}", cardId);

        try {
            webClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards/{cardId}")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(cardId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of("desc", description))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Beschreibung von Karte {} aktualisiert.", cardId);

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Aktualisieren der Beschreibung {}: HTTP {} – {}",
                    cardId, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Aktualisieren der Beschreibung von Karte {}", cardId, e);
        }
    }

    // ── Custom Fields ─────────────────────────────────────────────────────────

    /**
     * Gibt alle Custom Fields zurück, die auf dem konfigurierten Board definiert sind.
     */
    public List<TrelloCustomField> fetchBoardCustomFields() {
        String boardId = props.getTrello().getBoardId();

        try {
            List<TrelloCustomField> fields = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/boards/{boardId}/customFields")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(boardId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<TrelloCustomField>>() {})
                    .block();

            return fields != null ? fields : Collections.emptyList();

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Laden der Custom Fields: HTTP {} – {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Laden der Custom Fields", e);
            return Collections.emptyList();
        }
    }

    /**
     * Setzt den Wert eines Custom Fields (Typ: number) auf einer Karte.
     *
     * @param cardId       ID der Karte
     * @param customFieldId ID des Custom Fields
     * @param value        Wert (z.B. Story Points als Zahl)
     */
    public void setCustomFieldNumber(String cardId, String customFieldId, int value) {
        log.info("Setze Custom Field {} auf Karte {} = {}", customFieldId, cardId, value);

        try {
            webClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards/{cardId}/customField/{fieldId}/item")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(cardId, customFieldId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of("value", java.util.Map.of("number", String.valueOf(value))))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Custom Field {} auf Karte {} gesetzt.", customFieldId, cardId);

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Setzen des Custom Fields {}: HTTP {} – {}",
                    customFieldId, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Setzen des Custom Fields {} auf Karte {}", customFieldId, cardId, e);
        }
    }

    // ── Checklisten ───────────────────────────────────────────────────────────

    /**
     * Erstellt eine Checkliste auf einer Karte und befüllt sie mit den übergebenen Einträgen.
     *
     * @param cardId    ID der Karte
     * @param name      Name der Checkliste
     * @param items     Einträge (jeder wird als offenes Checklist-Item angelegt)
     */
    public void createChecklistWithItems(String cardId, String name, List<String> items) {
        if (items == null || items.isEmpty()) {
            log.info("Keine Checklisten-Einträge übergeben – Checkliste wird nicht erstellt.");
            return;
        }

        log.info("Erstelle Checkliste '{}' mit {} Einträgen auf Karte {}", name, items.size(), cardId);

        try {
            // 1. Checkliste anlegen
            ChecklistResponse checklist = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/checklists")
                            .queryParam("key",    props.getTrello().getApiKey())
                            .queryParam("token",  props.getTrello().getApiToken())
                            .queryParam("idCard", cardId)
                            .queryParam("name",   name)
                            .build())
                    .retrieve()
                    .bodyToMono(ChecklistResponse.class)
                    .block();

            if (checklist == null || checklist.id == null) {
                log.error("Checkliste konnte nicht erstellt werden – leere Antwort.");
                return;
            }

            String checklistId = checklist.id;
            log.info("Checkliste {} erstellt, füge {} Items hinzu.", checklistId, items.size());

            // 2. Items hinzufügen
            for (String item : items) {
                addCheckItem(checklistId, item);
            }

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Erstellen der Checkliste: HTTP {} – {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Erstellen der Checkliste auf Karte {}", cardId, e);
        }
    }

    private void addCheckItem(String checklistId, String itemName) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", itemName);

        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/checklists/{checklistId}/checkItems")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(checklistId))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Hinzufügen von CheckItem '{}': HTTP {} – {}",
                    itemName, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Hinzufügen von CheckItem '{}'", itemName, e);
        }
    }

    // ── Kommentar ─────────────────────────────────────────────────────────────

    /**
     * Fügt einen Kommentar zur Trello-Karte hinzu.
     *
     * @param cardId  ID der Trello-Karte
     * @param comment Text des Kommentars
     */
    public void addComment(String cardId, String comment) {
        log.info("Schreibe Kommentar auf Trello-Karte {}", cardId);

        // "text" wird als Form-Body gesendet, NICHT als Query-Parameter.
        // Grund: der Kommentartext kann geschweifte Klammern enthalten (z.B. JSON-Fehlertexte),
        // die Spring's URI-Builder sonst als Template-Variablen interpretiert.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("text", comment);

        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards/{cardId}/actions/comments")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .build(cardId))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Kommentar erfolgreich auf Karte {} geschrieben.", cardId);

        } catch (WebClientResponseException e) {
            log.error("Trello API Fehler beim Kommentar schreiben: HTTP {} – {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Trello Kommentar schreiben", e);
        }
    }

    // ── Hilfsobjekte ──────────────────────────────────────────────────────────

    /** Wrapper für den GET /cards/{id}?fields=labels Response. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CardLabelsResponse {
        @JsonProperty("labels")
        List<TrelloLabel> labels;
    }

    /** Wrapper für den GET /lists/{id}?fields=name Response. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ListNameResponse {
        @JsonProperty("name")
        String name;
    }

    /** Wrapper für den POST /checklists Response. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChecklistResponse {
        @JsonProperty("id")
        String id;
    }

    // ── Öffentliche DTOs ─────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrelloChecklistRead {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("checkItems")
        private List<CheckItem> checkItems = new ArrayList<>();

        public String         getId()         { return id; }
        public String         getName()       { return name; }
        public List<CheckItem> getCheckItems() { return checkItems; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CheckItem {
            @JsonProperty("name")
            private String name;

            @JsonProperty("state")
            private String state; // "incomplete" oder "complete"

            public String getName()  { return name; }
            public String getState() { return state; }
        }
    }

    // ── Öffentliches DTO für Custom Fields ───────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrelloCustomField {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type;

        public String getId()   { return id; }
        public String getName() { return name; }
        public String getType() { return type; }
    }
}
