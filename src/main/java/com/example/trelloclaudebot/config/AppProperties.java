package com.example.trelloclaudebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Trello trello = new Trello();
    private final Claude claude = new Claude();

    public Trello getTrello() { return trello; }
    public Claude getClaude() { return claude; }

    public static class Trello {
        private String apiKey;
        private String apiToken;
        private String baseUrl        = "https://api.trello.com/1";
        private String boardId;
        private long   pollIntervalMs = 30_000;

        public String getApiKey()        { return apiKey; }
        public String getApiToken()      { return apiToken; }
        public String getBaseUrl()       { return baseUrl; }
        public String getBoardId()       { return boardId; }
        public long   getPollIntervalMs(){ return pollIntervalMs; }

        public void setApiKey(String apiKey)           { this.apiKey = apiKey; }
        public void setApiToken(String apiToken)       { this.apiToken = apiToken; }
        public void setBaseUrl(String baseUrl)         { this.baseUrl = baseUrl; }
        public void setBoardId(String boardId)         { this.boardId = boardId; }
        public void setPollIntervalMs(long ms)         { this.pollIntervalMs = ms; }
    }

    public static class Claude {
        private String apiKey;
        private String baseUrl   = "https://api.anthropic.com";
        private String model     = "claude-sonnet-4-6";
        private int    maxTokens = 1024;

        public String getApiKey()    { return apiKey; }
        public String getBaseUrl()   { return baseUrl; }
        public String getModel()     { return model; }
        public int    getMaxTokens() { return maxTokens; }

        public void setApiKey(String apiKey)    { this.apiKey = apiKey; }
        public void setBaseUrl(String baseUrl)  { this.baseUrl = baseUrl; }
        public void setModel(String model)      { this.model = model; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }
}
