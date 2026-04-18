package com.example.trelloclaudebot.client;

import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.trello.TrelloAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

    /**
     * Ruft alle relevanten Actions des konfigurierten Boards ab, die nach {@code since} aufgetreten sind.
     *
     * @param since Nur Actions nach diesem Zeitpunkt werden zurückgegeben
     * @return Liste der Actions, neueste zuerst
     */
    public List<TrelloAction> fetchRecentActions(Instant since) {
        String boardId = props.getTrello().getBoardId();
        String sinceStr = DateTimeFormatter.ISO_INSTANT.format(since);

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

    /**
     * Fügt einen Kommentar zur Trello-Karte hinzu.
     *
     * @param cardId  ID der Trello-Karte
     * @param comment Text des Kommentars
     */
    public void addComment(String cardId, String comment) {
        log.info("Schreibe Kommentar auf Trello-Karte {}", cardId);

        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards/{cardId}/actions/comments")
                            .queryParam("key",   props.getTrello().getApiKey())
                            .queryParam("token", props.getTrello().getApiToken())
                            .queryParam("text",  comment)
                            .build(cardId))
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
}
