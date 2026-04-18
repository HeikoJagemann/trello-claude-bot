package com.example.trelloclaudebot.client;

import com.example.trelloclaudebot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class TrelloClient {

    private static final Logger log = LoggerFactory.getLogger(TrelloClient.class);

    private final WebClient     webClient;
    private final AppProperties props;

    public TrelloClient(@Qualifier("trelloWebClient") WebClient webClient,
                        AppProperties props) {
        this.webClient = webClient;
        this.props     = props;
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
