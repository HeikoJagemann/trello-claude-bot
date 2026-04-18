package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Führt Claude Code CLI als Subprocess im konfigurierten Repo-Verzeichnis aus.
 *
 * Aufruf: {@code claude -p "<prompt>"}
 *
 * Claude Code hat direkten Zugriff auf alle Dateien im Repo und kann über seine
 * eigenen Tools (Read, Edit, Write, Bash, Grep usw.) den Code selbstständig
 * analysieren und anpassen. Kein manuelles Übergeben von Kontext nötig.
 */
@Service
public class ClaudeCodeRunner {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeRunner.class);

    /** Maximale Wartezeit für einen Claude-Code-Lauf (5 Minuten). */
    private static final long TIMEOUT_MINUTES = 5;

    private final AppProperties props;

    public ClaudeCodeRunner(AppProperties props) {
        this.props = props;
    }

    /**
     * Führt Claude Code mit dem übergebenen Prompt im konfigurierten Repo-Verzeichnis aus.
     *
     * @param task Der zu verarbeitende Task (wird für Logging verwendet)
     * @param prompt Der vollständige Prompt für Claude Code
     * @return Ausgabe von Claude Code (stdout), oder eine Fehlermeldung
     */
    public String run(InternalTask task, String prompt) {
        String repoPath = props.getClaudeCode().getRepoPath();
        File repoDir = new File(repoPath);

        if (!repoDir.exists() || !repoDir.isDirectory()) {
            String msg = "Repo-Verzeichnis nicht gefunden: " + repoPath;
            log.error("ClaudeCodeRunner: {}", msg);
            return "❌ " + msg;
        }

        log.info("ClaudeCodeRunner: Starte Claude Code für Karte '{}' in '{}'",
                task.getCardId(), repoPath);

        ProcessBuilder pb = new ProcessBuilder("claude", "-p", prompt);
        pb.directory(repoDir);
        pb.redirectErrorStream(false); // stderr separat lesen

        try {
            Process process = pb.start();

            // stdout und stderr parallel in eigenen Threads einlesen (verhindert Deadlock)
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    reader.lines().forEach(line -> stdout.append(line).append("\n"));
                } catch (IOException e) {
                    log.warn("ClaudeCodeRunner: Fehler beim Lesen von stdout", e);
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    reader.lines().forEach(line -> stderr.append(line).append("\n"));
                } catch (IOException e) {
                    log.warn("ClaudeCodeRunner: Fehler beim Lesen von stderr", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);

            stdoutThread.join();
            stderrThread.join();

            if (!finished) {
                process.destroyForcibly();
                log.error("ClaudeCodeRunner: Timeout nach {} Minuten für Karte '{}'",
                        TIMEOUT_MINUTES, task.getCardId());
                return "❌ Claude Code Timeout nach " + TIMEOUT_MINUTES + " Minuten.";
            }

            int exitCode = process.exitValue();
            String output = stdout.toString().trim();
            String errOutput = stderr.toString().trim();

            if (!errOutput.isBlank()) {
                log.warn("ClaudeCodeRunner stderr: {}", errOutput);
            }

            if (exitCode != 0) {
                log.error("ClaudeCodeRunner: Exitcode {} für Karte '{}'. Stderr: {}",
                        exitCode, task.getCardId(), errOutput);
                return "❌ Claude Code fehlgeschlagen (Exit " + exitCode + "):\n" + errOutput;
            }

            log.info("ClaudeCodeRunner: Erfolgreich abgeschlossen für Karte '{}'", task.getCardId());
            return output.isBlank() ? "✅ Claude Code abgeschlossen (keine Ausgabe)." : output;

        } catch (IOException e) {
            log.error("ClaudeCodeRunner: Konnte Claude Code nicht starten – ist 'claude' im PATH?", e);
            return "❌ Claude Code konnte nicht gestartet werden: " + e.getMessage()
                    + "\nHinweis: Ist die Claude Code CLI installiert und im PATH?";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("ClaudeCodeRunner: Unterbrochen", e);
            return "❌ Claude Code wurde unterbrochen.";
        }
    }
}
