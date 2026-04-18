package com.example.trelloclaudebot.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

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
        return WebClient.builder()
                .baseUrl(props.getTrello().getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
