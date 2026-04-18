package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Führt Claude Code CLI als Subprocess im konfigurierten Repo-Verzeichnis aus.
 *
 * Der Prompt wird über stdin an den Prozess übergeben (nicht als Argument),
 * um Probleme mit Sonderzeichen und Längenbeschränkungen auf Windows zu vermeiden.
 *
 * Optionaler Kontext: Ist {@code app.claude-code.context-file} konfiguriert,
 * wird der Inhalt dieser Datei jedem Prompt vorangestellt.
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
     * @param task   Der zu verarbeitende Task (für Logging)
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

        String fullPrompt = buildFullPrompt(prompt);
        log.info("ClaudeCodeRunner: Starte Claude Code für Karte '{}' in '{}'",
                task.getCardId(), repoPath);
        log.debug("ClaudeCodeRunner: Prompt (ersten 200 Zeichen): {}",
                fullPrompt.substring(0, Math.min(200, fullPrompt.length())));

        // Prompt über stdin übergeben, nicht als Argument –
        // vermeidet Escaping-Probleme mit <, >, {, } auf Windows
        ProcessBuilder pb = new ProcessBuilder("claude", "-p");
        pb.directory(repoDir);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            // stdin: Prompt schreiben und Stream schließen (EOF signalisiert Ende der Eingabe)
            Thread stdinThread = new Thread(() -> {
                try (OutputStream os = process.getOutputStream();
                     OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                    writer.write(fullPrompt);
                } catch (IOException e) {
                    log.warn("ClaudeCodeRunner: Fehler beim Schreiben in stdin", e);
                }
            });

            // stdout und stderr parallel lesen (verhindert Deadlock)
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

            stdinThread.start();
            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);

            stdinThread.join();
            stdoutThread.join();
            stderrThread.join();

            if (!finished) {
                process.destroyForcibly();
                log.error("ClaudeCodeRunner: Timeout nach {} Minuten für Karte '{}'",
                        TIMEOUT_MINUTES, task.getCardId());
                return "❌ Claude Code Timeout nach " + TIMEOUT_MINUTES + " Minuten.";
            }

            int exitCode = process.exitValue();
            String output   = stdout.toString().trim();
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

    /**
     * Stellt den optionalen Kontext-Text aus der konfigurierten Datei
     * dem eigentlichen Prompt voran.
     */
    private String buildFullPrompt(String prompt) {
        String contextFile = props.getClaudeCode().getContextFile();
        if (contextFile == null || contextFile.isBlank()) {
            return prompt;
        }

        Path path = Paths.get(contextFile);
        if (!Files.exists(path)) {
            log.warn("ClaudeCodeRunner: Kontext-Datei '{}' nicht gefunden – wird ignoriert.", contextFile);
            return prompt;
        }

        try {
            String context = Files.readString(path, StandardCharsets.UTF_8).strip();
            log.debug("ClaudeCodeRunner: Kontext-Datei '{}' geladen ({} Zeichen).", contextFile, context.length());
            return context + "\n\n---\n\n" + prompt;
        } catch (IOException e) {
            log.warn("ClaudeCodeRunner: Kontext-Datei '{}' konnte nicht gelesen werden.", contextFile, e);
            return prompt;
        }
    }
}
