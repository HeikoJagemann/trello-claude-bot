package com.example.trelloclaudebot.client;

import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.claude.ClaudeMessage;
import com.example.trelloclaudebot.dto.claude.ClaudeRequest;
import com.example.trelloclaudebot.dto.claude.ClaudeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    private final WebClient     webClient;
    private final AppProperties props;

    public ClaudeClient(@Qualifier("claudeWebClient") WebClient webClient,
                        AppProperties props) {
        this.webClient = webClient;
        this.props     = props;
    }

    /**
     * Sendet einen Prompt an die Claude Messages API und gibt die Textantwort zurück.
     *
     * @param promptText Der vollständige Prompt-Text
     * @return Antwort-Text von Claude oder Fehlermeldung
     */
    public String sendPrompt(String promptText) {
        ClaudeRequest request = new ClaudeRequest(
                props.getClaude().getModel(),
                props.getClaude().getMaxTokens(),
                List.of(new ClaudeMessage("user", promptText))
        );

        log.info("Sende Anfrage an Claude API (Modell: {})", props.getClaude().getModel());

        try {
            ClaudeResponse response = webClient.post()
                    .uri("/v1/messages")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ClaudeResponse.class)
                    .block();

            if (response == null) {
                log.warn("Claude API hat eine leere Antwort zurückgegeben.");
                return "Keine Antwort von Claude erhalten.";
            }

            String text = response.extractText();
            log.info("Claude API Antwort erhalten (ID: {})", response.getId());
            return text;

        } catch (WebClientResponseException e) {
            log.error("Claude API Fehler: HTTP {} – {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "Fehler bei der KI-Verarbeitung: " + e.getStatusCode();
        } catch (Exception e) {
            log.error("Unerwarteter Fehler beim Claude API Aufruf", e);
            return "Interner Fehler bei der KI-Verarbeitung.";
        }
    }
}
