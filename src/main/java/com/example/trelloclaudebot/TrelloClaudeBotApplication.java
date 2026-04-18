package com.example.trelloclaudebot;

import com.example.trelloclaudebot.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class TrelloClaudeBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrelloClaudeBotApplication.class, args);
    }
}
