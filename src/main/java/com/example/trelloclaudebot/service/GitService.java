package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Führt Git-Operationen im konfigurierten Repo-Verzeichnis aus.
 */
@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);
    private static final long TIMEOUT_SECONDS = 60;

    private final AppProperties props;

    public GitService(AppProperties props) {
        this.props = props;
    }

    /**
     * Gibt den aktuellen HEAD-Commit-Hash zurück (vollständig).
     * Wird vor der Implementierung gespeichert, um danach neue Commits zu ermitteln.
     *
     * @return HEAD-Hash oder leerer String bei Fehler
     */
    public String getCurrentHead() {
        return runGitCommand("rev-parse", "HEAD").stream()
                .findFirst()
                .orElse("");
    }

    /**
     * Prüft ob es nicht-committete Änderungen gibt und commitet diese automatisch.
     * Dient als Fallback falls Claude Code vergisst zu committen.
     *
     * @param commitMessage Commit-Message für den Auto-Commit
     * @return true wenn ein Commit erstellt wurde, false wenn nichts zu committen war
     */
    public boolean commitIfNeeded(String commitMessage) {
        List<String> status = runGitCommand("status", "--porcelain");
        if (status.isEmpty()) {
            log.debug("GitService: Keine nicht-committeten Änderungen vorhanden.");
            return false;
        }

        log.warn("GitService: {} nicht-committete Datei(en) gefunden – erstelle Auto-Commit.", status.size());
        runGitCommand("add", "-A");
        List<String> result = runGitCommand("commit", "-m", commitMessage);
        log.info("GitService: Auto-Commit erstellt: {}", result);
        return true;
    }

    /**
     * Führt {@code git push} im Repo aus.
     *
     * @return true wenn erfolgreich
     */
    public boolean push() {
        log.info("GitService: git push");
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "push");
            pb.directory(getRepoDir());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            List<String> output = readLines(process.getInputStream());
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.error("GitService: git push Timeout");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("GitService: git push fehlgeschlagen (Exit {}): {}", exitCode, output);
                return false;
            }

            log.info("GitService: git push erfolgreich. Output: {}", output);
            return true;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("GitService: git push Fehler", e);
            return false;
        }
    }

    /**
     * Gibt alle Commits zurück, die seit {@code sinceHash} (exklusiv) hinzugekommen sind.
     * Format: "abc1234 Commit-Nachricht"
     *
     * @param sinceHash Hash des letzten bekannten Commits (exklusiv)
     * @return Liste der neuen Commits, neueste zuerst; leer wenn keine neuen Commits
     */
    public List<String> getCommitsSince(String sinceHash) {
        if (sinceHash == null || sinceHash.isBlank()) {
            return Collections.emptyList();
        }
        return runGitCommand("log", sinceHash + "..HEAD", "--oneline");
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private List<String> runGitCommand(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) command.add(arg);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(getRepoDir());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            List<String> lines = readLines(process.getInputStream());
            process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return lines;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("GitService: Fehler bei git {}: {}", String.join(" ", args), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> readLines(java.io.InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) lines.add(line);
            }
        }
        return lines;
    }

    private File getRepoDir() {
        return new File(props.getClaudeCode().getRepoPath());
    }
}
