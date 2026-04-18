package com.example.trelloclaudebot.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier("claudeWebClient")
    public WebClient claudeWebClient(AppProperties props) {
        return WebClient.builder()
                .baseUrl(props.getClaude().getBaseUrl())
                .defaultHeader("x-api-key",        props.getClaude().getApiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type",      "application/json")
                .build();
    }

    @Bean
    @Qualifier("trelloWebClient")
    public WebClient trelloWebClient(AppProperties props) {
        // VALUES_ONLY: Query-Parameter-Werte werden URL-enkodiert, aber NICHT als
        // URI-Template-Variablen interpretiert. Verhindert den Fehler, wenn Werte
        // geschweifte Klammern enthalten (z.B. unaufgelöste ${ENV_VAR}-Platzhalter).
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(props.getTrello().getBaseUrl());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        return WebClient.builder()
                .uriBuilderFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
