package com.example.trelloclaudebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
        private String bugsListName         = "Bugs";
        private String qaListName           = "QA";

        public String getApiKey()               { return apiKey; }
        public String getApiToken()             { return apiToken; }
        public String getBaseUrl()              { return baseUrl; }
        public String getBoardId()              { return boardId; }
        public long   getPollIntervalMs()       { return pollIntervalMs; }
        public String getBacklogListName()      { return backlogListName; }
        public String getRefinementLabelName()  { return refinementLabelName; }
        public String getReadyLabelName()       { return readyLabelName; }
        public String getStoryPointsFieldName() { return storyPointsFieldName; }
        public String getSprintListName()       { return sprintListName; }
        public String getBugsListName()         { return bugsListName; }
        public String getQaListName()           { return qaListName; }

        public void setApiKey(String apiKey)                       { this.apiKey = apiKey; }
        public void setApiToken(String apiToken)                   { this.apiToken = apiToken; }
        public void setBaseUrl(String baseUrl)                     { this.baseUrl = baseUrl; }
        public void setBoardId(String boardId)                     { this.boardId = boardId; }
        public void setPollIntervalMs(long ms)                     { this.pollIntervalMs = ms; }
        public void setBacklogListName(String name)                { this.backlogListName = name; }
        public void setRefinementLabelName(String name)            { this.refinementLabelName = name; }
        public void setReadyLabelName(String name)                 { this.readyLabelName = name; }
        public void setStoryPointsFieldName(String name)           { this.storyPointsFieldName = name; }
        public void setSprintListName(String name)                 { this.sprintListName = name; }
        public void setBugsListName(String name)                   { this.bugsListName = name; }
        public void setQaListName(String name)                     { this.qaListName = name; }
    }

    // ── Repo-Konfiguration ────────────────────────────────────────────────────

    /** Ein einzelnes Repository im Projektverbund. */
    public static class Repo {
        /** Anzeigename des Repos (z.B. "FM-Backend"). */
        private String name;
        /** Absoluter Pfad zum lokalen Repo-Verzeichnis. */
        private String path;

        public String getName() { return name; }
        public String getPath() { return path; }
        public void setName(String name) { this.name = name; }
        public void setPath(String path) { this.path = path; }

        @Override
        public String toString() { return name + " (" + path + ")"; }
    }

    // ── Claude Code Konfiguration ─────────────────────────────────────────────

    public static class ClaudeCode {
        /**
         * Arbeitsverzeichnis, in dem Claude Code CLI gestartet wird.
         * Typischerweise eines der konfigurierten Repos oder ein gemeinsames Parent-Verzeichnis.
         */
        private String repoPath = ".";

        /**
         * Liste aller Repos, die zum Projekt gehören.
         * Aus jedem Repo wird automatisch die CLAUDE.md gelesen (falls vorhanden)
         * und als Kontext jedem Prompt vorangestellt.
         */
        private List<Repo> repos = new ArrayList<>();

        /**
         * Optionaler Pfad zu einer einzelnen Kontext-Datei als Fallback,
         * wenn keine Repos konfiguriert sind.
         */
        private String contextFile = "";

        /** Maximale Laufzeit eines Claude-Code-Prozesses in Minuten. Standard: 30. */
        private long timeoutMinutes = 30;

        /** Maximale Anzahl Wiederholungsversuche bei unvollständigem Ergebnis. Standard: 5. */
        private int maxRetries = 5;

        /** Wartezeit in Minuten nach Token-Erschöpfung vor dem nächsten Versuch. Standard: 240 (4h). */
        private long rateLimitWaitMinutes = 240;

        public String     getRepoPath()             { return repoPath; }
        public List<Repo> getRepos()                { return repos; }
        public String     getContextFile()          { return contextFile; }
        public long       getTimeoutMinutes()       { return timeoutMinutes; }
        public int        getMaxRetries()           { return maxRetries; }
        public long       getRateLimitWaitMinutes() { return rateLimitWaitMinutes; }

        public void setRepoPath(String p)           { this.repoPath = p; }
        public void setRepos(List<Repo> repos)      { this.repos = repos; }
        public void setContextFile(String f)        { this.contextFile = f; }
        public void setTimeoutMinutes(long t)       { this.timeoutMinutes = t; }
        public void setMaxRetries(int r)            { this.maxRetries = r; }
        public void setRateLimitWaitMinutes(long m) { this.rateLimitWaitMinutes = m; }
    }

}
