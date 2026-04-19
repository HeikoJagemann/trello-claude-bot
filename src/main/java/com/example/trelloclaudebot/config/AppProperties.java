package com.example.trelloclaudebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Trello      trello      = new Trello();
    private final ClaudeCode  claudeCode  = new ClaudeCode();

    public Trello     getTrello()     { return trello; }
    public ClaudeCode getClaudeCode() { return claudeCode; }

    public static class Trello {
        private String apiKey;
        private String apiToken;
        private String baseUrl        = "https://api.trello.com/1";
        private String boardId;
        private long   pollIntervalMs  = 30_000;
        private String backlogListName      = "Backlog";
        private String refinementLabelName  = "Refinement";
        private String readyLabelName       = "Refined";
        private String storyPointsFieldName = "Story Points";
        private String sprintListName       = "Sprint";
        private String qaListName           = "QA";

        public String getApiKey()              { return apiKey; }
        public String getApiToken()            { return apiToken; }
        public String getBaseUrl()             { return baseUrl; }
        public String getBoardId()             { return boardId; }
        public long   getPollIntervalMs()      { return pollIntervalMs; }
        public String getBacklogListName()     { return backlogListName; }
        public String getRefinementLabelName()  { return refinementLabelName; }
        public String getReadyLabelName()       { return readyLabelName; }
        public String getStoryPointsFieldName() { return storyPointsFieldName; }
        public String getSprintListName()       { return sprintListName; }
        public String getQaListName()           { return qaListName; }

        public void setApiKey(String apiKey)                       { this.apiKey = apiKey; }
        public void setApiToken(String apiToken)                   { this.apiToken = apiToken; }
        public void setBaseUrl(String baseUrl)                     { this.baseUrl = baseUrl; }
        public void setBoardId(String boardId)                     { this.boardId = boardId; }
        public void setPollIntervalMs(long ms)                     { this.pollIntervalMs = ms; }
        public void setBacklogListName(String name)                { this.backlogListName = name; }
        public void setRefinementLabelName(String name)             { this.refinementLabelName = name; }
        public void setReadyLabelName(String name)                  { this.readyLabelName = name; }
        public void setStoryPointsFieldName(String name)            { this.storyPointsFieldName = name; }
        public void setSprintListName(String name)                  { this.sprintListName = name; }
        public void setQaListName(String name)                      { this.qaListName = name; }
    }

    public static class ClaudeCode {
        /** Pfad zum lokalen Repo-Verzeichnis, in dem Claude Code CLI ausgeführt wird. */
        private String repoPath = ".";

        /**
         * Optionaler Pfad zu einer Kontext-Datei (z.B. CLAUDE.md oder Projektbeschreibung).
         * Ihr Inhalt wird jedem Prompt vorangestellt, damit Claude den Projektrahmen kennt.
         */
        private String contextFile = "";

        /** Maximale Laufzeit eines Claude-Code-Prozesses in Minuten. Standard: 30. */
        private long timeoutMinutes = 30;

        public String getRepoPath()             { return repoPath; }
        public String getContextFile()          { return contextFile; }
        public long   getTimeoutMinutes()       { return timeoutMinutes; }

        public void setRepoPath(String p)       { this.repoPath = p; }
        public void setContextFile(String f)    { this.contextFile = f; }
        public void setTimeoutMinutes(long t)   { this.timeoutMinutes = t; }
    }

}
