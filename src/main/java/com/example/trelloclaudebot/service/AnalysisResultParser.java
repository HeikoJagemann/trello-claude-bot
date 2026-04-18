package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.dto.internal.AnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrahiert ein {@link AnalysisResult} aus der Rohausgabe der Claude Code CLI.
 *
 * Claude kann das JSON entweder direkt oder in einem Markdown-Code-Block ausgeben:
 *   ```json
 *   { ... }
 *   ```
 * Beide Varianten werden unterstützt.
 */
@Service
public class AnalysisResultParser {

    private static final Logger log = LoggerFactory.getLogger(AnalysisResultParser.class);

    /** Findet JSON in einem optionalen ```json ... ``` Code-Block. */
    private static final Pattern CODE_BLOCK = Pattern.compile(
            "```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```",
            Pattern.MULTILINE
    );

    /** Findet das erste vollständige JSON-Objekt in der Antwort (Fallback). */
    private static final Pattern BARE_JSON = Pattern.compile(
            "\\{[\\s\\S]*\\}",
            Pattern.MULTILINE
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parst die Claude-Ausgabe und gibt ein {@link AnalysisResult} zurück.
     * Gibt {@code null} zurück wenn kein JSON gefunden oder geparst werden kann.
     *
     * @param rawResponse Rohausgabe der Claude Code CLI
     */
    public AnalysisResult parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("AnalysisResultParser: Leere Antwort von Claude.");
            return null;
        }

        String json = extractJson(rawResponse);
        if (json == null) {
            log.warn("AnalysisResultParser: Kein JSON in der Antwort gefunden.\nAntwort: {}", rawResponse);
            return null;
        }

        try {
            AnalysisResult result = objectMapper.readValue(json, AnalysisResult.class);
            log.info("AnalysisResultParser: Erfolgreich geparst. Story Points: {}, {} Akzeptanzkriterien",
                    result.getStoryPoints(), result.getAkzeptanzkriterien().size());
            return result;
        } catch (Exception e) {
            log.error("AnalysisResultParser: JSON-Parsing fehlgeschlagen. JSON: {}", json, e);
            return null;
        }
    }

    private String extractJson(String text) {
        // 1. Versuch: JSON in Code-Block
        Matcher codeMatcher = CODE_BLOCK.matcher(text);
        if (codeMatcher.find()) {
            return codeMatcher.group(1).trim();
        }

        // 2. Versuch: Bare JSON-Objekt
        Matcher bareMatcher = BARE_JSON.matcher(text);
        if (bareMatcher.find()) {
            return bareMatcher.group().trim();
        }

        return null;
    }
}
