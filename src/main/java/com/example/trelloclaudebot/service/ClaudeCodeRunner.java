package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Verwendet {@code --output-format json}, damit Token-Usage und Kosten aus dem
 * JSON-Result ausgelesen werden können.
 * stderr wird live geloggt (Claude Code schreibt dort Fortschritt/Tool-Calls).
 * stdout (JSON) wird nach Abschluss geparst.
 */
@Service
public class ClaudeCodeRunner {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeRunner.class);
    private static final long TIMEOUT_MINUTES = 5;

    private final AppProperties props;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    public ClaudeCodeRunner(AppProperties props) {
        this.props = props;
    }

    /**
     * Ergebnis eines Claude-Code-Laufs: Antworttext + Token-Verbrauch.
     */
    public record RunResult(
            String text,
            int    inputTokens,
            int    outputTokens,
            int    cacheReadTokens,
            double costUsd
    ) {
        /** Liefert true wenn Token-Daten vorhanden sind. */
        public boolean hasTokenInfo() {
            return inputTokens > 0 || outputTokens > 0;
        }

        /** Formatierter Token-Block für Trello-Kommentare. */
        public String tokenSummary() {
            if (!hasTokenInfo()) return "";
            StringBuilder sb = new StringBuilder("\n\n---\n**Token-Verbrauch:**\n");
            sb.append("- Input: ").append(inputTokens).append("\n");
            sb.append("- Output: ").append(outputTokens).append("\n");
            if (cacheReadTokens > 0) {
                sb.append("- Cache-Read: ").append(cacheReadTokens).append("\n");
            }
            if (costUsd > 0) {
                sb.append(String.format("- Kosten: $%.4f%n", costUsd));
            }
            return sb.toString();
        }
    }

    /**
     * Führt Claude Code aus und gibt Text + Token-Verbrauch zurück.
     */
    public RunResult run(InternalTask task, String prompt) {
        String repoPath = props.getClaudeCode().getRepoPath();
        File repoDir = new File(repoPath);

        if (!repoDir.exists() || !repoDir.isDirectory()) {
            String msg = "Repo-Verzeichnis nicht gefunden: " + repoPath;
            log.error("ClaudeCodeRunner: {}", msg);
            return errorResult("❌ " + msg);
        }

        String fullPrompt = buildFullPrompt(prompt);
        log.info("ClaudeCodeRunner: Starte Claude Code für Karte '{}' in '{}'",
                task.getCardId(), repoPath);
        log.debug("ClaudeCodeRunner: Prompt (ersten 200 Zeichen): {}",
                fullPrompt.substring(0, Math.min(200, fullPrompt.length())));

        ProcessBuilder pb = new ProcessBuilder(
                "claude", "-p", "--dangerously-skip-permissions", "--output-format", "json");
        pb.directory(repoDir);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            String cardLabel = "[Karte " + task.getCardId() + "] ";

            // stdin: Prompt schreiben und Stream schließen
            Thread stdinThread = new Thread(() -> {
                try (OutputStream os = process.getOutputStream();
                     OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                    writer.write(fullPrompt);
                } catch (IOException e) {
                    log.warn("ClaudeCodeRunner: Fehler beim Schreiben in stdin", e);
                }
            });

            // stdout: JSON-Result sammeln (kein Live-Log, da ein einzelnes JSON-Objekt)
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.warn("ClaudeCodeRunner: Fehler beim Lesen von stdout", e);
                }
            });

            // stderr: Fortschritt/Tool-Calls live loggen
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("{} {}", cardLabel, line);
                    }
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
                return errorResult("❌ Claude Code Timeout nach " + TIMEOUT_MINUTES + " Minuten.");
            }

            int exitCode = process.exitValue();
            String rawOutput = stdout.toString().trim();

            if (exitCode != 0) {
                log.error("ClaudeCodeRunner: Exitcode {} für Karte '{}'.", exitCode, task.getCardId());
                return errorResult("❌ Claude Code fehlgeschlagen (Exit " + exitCode + "):\n" + rawOutput);
            }

            RunResult result = parseJsonResult(rawOutput, task.getCardId());
            log.info("ClaudeCodeRunner: Abgeschlossen für Karte '{}' – Input: {}, Output: {}, Kosten: ${}",
                    task.getCardId(), result.inputTokens(), result.outputTokens(),
                    String.format("%.4f", result.costUsd()));
            return result;

        } catch (IOException e) {
            log.error("ClaudeCodeRunner: Konnte Claude Code nicht starten – ist 'claude' im PATH?", e);
            return errorResult("❌ Claude Code konnte nicht gestartet werden: " + e.getMessage()
                    + "\nHinweis: Ist die Claude Code CLI installiert und im PATH?");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("ClaudeCodeRunner: Unterbrochen", e);
            return errorResult("❌ Claude Code wurde unterbrochen.");
        }
    }

    // ── JSON-Parsing ──────────────────────────────────────────────────────────

    private RunResult parseJsonResult(String raw, String cardId) {
        if (raw.isBlank()) {
            return errorResult("✅ Claude Code abgeschlossen (keine Ausgabe).");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);

            String text = root.path("result").asText("").trim();
            if (text.isBlank()) {
                text = "✅ Claude Code abgeschlossen (keine Textausgabe).";
            }

            JsonNode usage = root.path("usage");
            int inputTokens     = usage.path("input_tokens").asInt(0);
            int outputTokens    = usage.path("output_tokens").asInt(0);
            int cacheRead       = usage.path("cache_read_input_tokens").asInt(0);
            double cost         = root.path("total_cost_usd").asDouble(0.0);

            return new RunResult(text, inputTokens, outputTokens, cacheRead, cost);

        } catch (Exception e) {
            log.warn("ClaudeCodeRunner: JSON-Parsing fehlgeschlagen für Karte '{}' – Rohausgabe wird verwendet.", cardId);
            return errorResult(raw);
        }
    }

    private static RunResult errorResult(String message) {
        return new RunResult(message, 0, 0, 0, 0.0);
    }

    // ── Kontext-Datei ─────────────────────────────────────────────────────────

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
