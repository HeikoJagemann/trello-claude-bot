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
 * Verwendet {@code --output-format stream-json}, damit jedes Event sofort auf stdout
 * geloggt wird (Tool-Calls, Assistant-Text, Fortschritt).
 * Die letzte Zeile (type=result) enthält Token-Usage und Kosten.
 */
@Service
public class ClaudeCodeRunner {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeRunner.class);
    private final AppProperties props;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    public ClaudeCodeRunner(AppProperties props) {
        this.props = props;
    }

    /**
     * Ergebnis eines Claude-Code-Laufs: Antworttext + Token-Verbrauch + Session-ID.
     */
    public record RunResult(
            String  text,
            String  sessionId,
            int     inputTokens,
            int     outputTokens,
            int     cacheReadTokens,
            double  costUsd,
            boolean isError,
            boolean isRateLimit
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
     * Setzt einen unterbrochenen Lauf mit der gespeicherten Session-ID fort.
     *
     * @param task               Task (für Logging)
     * @param sessionId          Session-ID aus dem vorherigen RunResult
     * @param continuationPrompt Prompt der an Claude geschickt wird (z.B. "Mache weiter...")
     */
    public RunResult resume(InternalTask task, String sessionId, String continuationPrompt) {
        log.info("ClaudeCodeRunner: Resume Session '{}' für Karte '{}'", sessionId, task.getCardId());
        return execute(task, continuationPrompt,
                new ProcessBuilder("claude", "--resume", sessionId,
                        "-p", "--verbose", "--dangerously-skip-permissions", "--output-format", "stream-json"));
    }

    /**
     * Führt Claude Code aus und gibt Text + Token-Verbrauch zurück.
     */
    public RunResult run(InternalTask task, String prompt) {
        ProcessBuilder pb = new ProcessBuilder(
                "claude", "-p", "--verbose", "--dangerously-skip-permissions", "--output-format", "stream-json");
        return execute(task, buildFullPrompt(prompt), pb);
    }

    private RunResult execute(InternalTask task, String prompt, ProcessBuilder pb) {
        String repoPath = props.getClaudeCode().getRepoPath();
        File repoDir = new File(repoPath);

        if (!repoDir.exists() || !repoDir.isDirectory()) {
            String msg = "Repo-Verzeichnis nicht gefunden: " + repoPath;
            log.error("ClaudeCodeRunner: {}", msg);
            return errorResult("❌ " + msg);
        }

        log.info("ClaudeCodeRunner: Starte Claude Code für Karte '{}' in '{}'",
                task.getCardId(), repoPath);
        log.debug("ClaudeCodeRunner: Prompt (ersten 200 Zeichen): {}",
                prompt.substring(0, Math.min(200, prompt.length())));

        pb.directory(repoDir);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();

            String cardLabel = "[Karte " + task.getCardId() + "] ";
            // Holds the last "type=result" line for token extraction
            StringBuilder resultLine  = new StringBuilder();
            // Holds the full assistant text (accumulated from type=assistant events)
            StringBuilder resultText  = new StringBuilder();

            // stdin: Prompt schreiben und Stream schließen
            Thread stdinThread = new Thread(() -> {
                try (OutputStream os = process.getOutputStream();
                     OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                    writer.write(prompt);
                } catch (IOException e) {
                    log.warn("ClaudeCodeRunner: Fehler beim Schreiben in stdin", e);
                }
            });

            // stdout: JSONL-Events live loggen (stream-json liefert eine Zeile pro Event)
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        logStreamEvent(cardLabel, line, resultLine, resultText);
                    }
                } catch (IOException e) {
                    log.warn("ClaudeCodeRunner: Fehler beim Lesen von stdout", e);
                }
            });

            // stderr: Fehler/Warnungen loggen
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.warn("{}[stderr] {}", cardLabel, line);
                    }
                } catch (IOException e) {
                    log.warn("ClaudeCodeRunner: Fehler beim Lesen von stderr", e);
                }
            });

            stdinThread.start();
            stdoutThread.start();
            stderrThread.start();

            long timeoutMinutes = props.getClaudeCode().getTimeoutMinutes();
            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            stdinThread.join();
            stdoutThread.join();
            stderrThread.join();

            if (!finished) {
                process.destroyForcibly();
                log.error("ClaudeCodeRunner: Timeout nach {} Minuten für Karte '{}'",
                        timeoutMinutes, task.getCardId());
                return errorResult("❌ Claude Code Timeout nach " + timeoutMinutes + " Minuten.");
            }

            int exitCode = process.exitValue();

            if (exitCode != 0 && resultLine.isEmpty()) {
                log.error("ClaudeCodeRunner: Exitcode {} für Karte '{}'.", exitCode, task.getCardId());
                return errorResult("❌ Claude Code fehlgeschlagen (Exit " + exitCode + ").");
            }

            RunResult result = parseResultLine(resultLine.toString().trim(), resultText.toString().trim(), task.getCardId());
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

    // ── Stream-JSON Event-Logging ─────────────────────────────────────────────

    /**
     * Parsed eine JSONL-Zeile aus dem stream-json Output und loggt sie lesbar.
     * Schreibt die result-Zeile in {@code resultLine} und den Assistant-Text in {@code resultText}.
     */
    private void logStreamEvent(String cardLabel, String line,
                                StringBuilder resultLine, StringBuilder resultText) {
        try {
            JsonNode node = objectMapper.readTree(line);
            String type = node.path("type").asText("");

            switch (type) {
                case "assistant" -> {
                    // Enthält message.content[] mit type=text oder type=tool_use
                    JsonNode content = node.path("message").path("content");
                    if (content.isArray()) {
                        for (JsonNode block : content) {
                            String blockType = block.path("type").asText("");
                            if ("text".equals(blockType)) {
                                String text = block.path("text").asText("").strip();
                                if (!text.isBlank()) {
                                    log.info("{}[Claude] {}", cardLabel, text);
                                    if (!resultText.isEmpty()) resultText.append("\n");
                                    resultText.append(text);
                                }
                            } else if ("tool_use".equals(blockType)) {
                                String toolName  = block.path("name").asText("?");
                                JsonNode input   = block.path("input");
                                String inputSummary = summarizeToolInput(toolName, input);
                                log.info("{}[Tool] {} {}", cardLabel, toolName, inputSummary);
                            }
                        }
                    }
                }
                case "tool_result" -> {
                    // Tool-Ergebnisse sind oft groß – nur kurz loggen
                    log.debug("{}[ToolResult] {}", cardLabel, line.substring(0, Math.min(200, line.length())));
                }
                case "result" -> {
                    // Letzte Zeile: enthält session_id, usage, total_cost_usd, result-Text
                    resultLine.setLength(0);
                    resultLine.append(line);
                    boolean isError = node.path("is_error").asBoolean(false);
                    String resultTextFromNode = node.path("result").asText("").strip();
                    if (!resultTextFromNode.isBlank()) {
                        log.info("{}[Ergebnis] {}", cardLabel, resultTextFromNode);
                        // Überschreibe mit dem finalen result-Text wenn vorhanden
                        resultText.setLength(0);
                        resultText.append(resultTextFromNode);
                    }
                    log.info("{}[Status] isError={}", cardLabel, isError);
                }
                case "system" -> {
                    String subtype = node.path("subtype").asText("");
                    if ("init".equals(subtype)) {
                        log.info("{}[Init] Claude Code Session gestartet", cardLabel);
                    }
                }
                default -> {
                    // Unbekannte Event-Typen auf DEBUG
                    log.debug("{}[{}] {}", cardLabel, type, line.substring(0, Math.min(200, line.length())));
                }
            }
        } catch (Exception e) {
            // Kein gültiges JSON (z.B. Statusmeldung) – direkt loggen
            log.info("{}{}", cardLabel, line);
        }
    }

    private String summarizeToolInput(String toolName, JsonNode input) {
        return switch (toolName) {
            case "Read"  -> input.path("file_path").asText(input.toString());
            case "Write" -> input.path("file_path").asText(input.toString());
            case "Edit"  -> input.path("file_path").asText(input.toString());
            case "Bash"  -> {
                String cmd = input.path("command").asText(input.toString());
                yield cmd.length() > 120 ? cmd.substring(0, 120) + "…" : cmd;
            }
            case "Grep"  -> "pattern=" + input.path("pattern").asText("?");
            case "Glob"  -> "pattern=" + input.path("pattern").asText("?");
            default      -> input.toString().substring(0, Math.min(120, input.toString().length()));
        };
    }

    // ── JSON-Parsing ──────────────────────────────────────────────────────────

    /**
     * Parsed die finale result-Zeile aus dem stream-json Output.
     * Fällt auf den gesammelten resultText zurück, wenn kein result-Text vorhanden.
     */
    private RunResult parseResultLine(String resultJson, String fallbackText, String cardId) {
        if (resultJson.isBlank()) {
            if (fallbackText.isBlank()) {
                return errorResult("✅ Claude Code abgeschlossen (keine Ausgabe).");
            }
            // Kein result-Event, aber Text gesammelt
            return new RunResult(fallbackText, "", 0, 0, 0, 0.0,
                    false, false);
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);

            String  sessionId   = root.path("session_id").asText("");
            boolean isError     = root.path("is_error").asBoolean(false);
            String  subtype     = root.path("subtype").asText("");

            String text = root.path("result").asText("").trim();
            if (text.isBlank()) {
                text = fallbackText.isBlank()
                        ? (isError ? "❌ Claude Code meldete einen Fehler." : "✅ Claude Code abgeschlossen.")
                        : fallbackText;
            }

            JsonNode usage      = root.path("usage");
            int inputTokens     = usage.path("input_tokens").asInt(0);
            int outputTokens    = usage.path("output_tokens").asInt(0);
            int cacheRead       = usage.path("cache_read_input_tokens").asInt(0);
            double cost         = root.path("total_cost_usd").asDouble(0.0);

            boolean isRateLimit = isError && isRateLimitError(text + " " + subtype);

            return new RunResult(text, sessionId, inputTokens, outputTokens, cacheRead, cost, isError, isRateLimit);

        } catch (Exception e) {
            log.warn("ClaudeCodeRunner: JSON-Parsing fehlgeschlagen für Karte '{}'", cardId);
            String text = fallbackText.isBlank() ? resultJson : fallbackText;
            return new RunResult(text, "", 0, 0, 0, 0.0, true, isRateLimitError(resultJson));
        }
    }

    private static boolean isRateLimitError(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("rate limit") || lower.contains("rate_limit")
                || lower.contains("quota")  || lower.contains("429")
                || lower.contains("too many requests") || lower.contains("overloaded")
                || lower.contains("token limit") || lower.contains("context length");
    }

    private static RunResult errorResult(String message) {
        return new RunResult(message, "", 0, 0, 0, 0.0, true, false);
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
