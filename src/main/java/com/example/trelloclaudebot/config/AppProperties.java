package com.example.trelloclaudebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Trello trello = new Trello();
    private final Claude claude = new Claude();
    private final Apply  apply  = new Apply();

    public Trello getTrello() { return trello; }
    public Claude getClaude() { return claude; }
    public Apply  getApply()  { return apply; }

    public static class Trello {
        private String apiKey;
        private String apiToken;
        private String baseUrl        = "https://api.trello.com/1";
        private String boardId;
        private long   pollIntervalMs  = 30_000;
        private String backlogListName = "Backlog";

        public String getApiKey()         { return apiKey; }
        public String getApiToken()       { return apiToken; }
        public String getBaseUrl()        { return baseUrl; }
        public String getBoardId()        { return boardId; }
        public long   getPollIntervalMs() { return pollIntervalMs; }
        public String getBacklogListName(){ return backlogListName; }

        public void setApiKey(String apiKey)                  { this.apiKey = apiKey; }
        public void setApiToken(String apiToken)              { this.apiToken = apiToken; }
        public void setBaseUrl(String baseUrl)                { this.baseUrl = baseUrl; }
        public void setBoardId(String boardId)                { this.boardId = boardId; }
        public void setPollIntervalMs(long ms)                { this.pollIntervalMs = ms; }
        public void setBacklogListName(String name)           { this.backlogListName = name; }
    }

    public static class Apply {
        /** Basisverzeichnis, in das generierte Dateien geschrieben werden. */
        private String basePath = ".";

        public String getBasePath()          { return basePath; }
        public void   setBasePath(String p)  { this.basePath = p; }
    }

    public static class Claude {
        private String apiKey;
        private String baseUrl   = "https://api.anthropic.com";
        private String model     = "claude-sonnet-4-6";
        private int    maxTokens = 4096;

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
