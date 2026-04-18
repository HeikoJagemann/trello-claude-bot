package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.internal.ApplyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parst die strukturierte Ausgabe der Claude API und schreibt die enthaltenen
 * Dateien in das konfigurierte Basisverzeichnis.
 *
 * Erwartetes Format im Claude-Response:
 *
 *   FILE: relativer/pfad/zur/datei.java
 *   ```java
 *   // vollständiger Inhalt
 *   ```
 *
 * Mehrere FILE-Blöcke pro Response sind erlaubt.
 */
@Service
public class ApplyEngine {

    private static final Logger log = LoggerFactory.getLogger(ApplyEngine.class);

    /**
     * Erkennt: FILE: <pfad> (optionale Leerzeile) ```<sprache> <code> ```
     * - Gruppe 1: relativer Dateipfad
     * - Gruppe 2: Dateiinhalt
     */
    private static final Pattern FILE_BLOCK_PATTERN = Pattern.compile(
            "FILE:\\s*(\\S+)\\s*\\n+```[a-zA-Z]*\\n([\\s\\S]*?)\\n```",
            Pattern.MULTILINE
    );

    private final AppProperties props;

    public ApplyEngine(AppProperties props) {
        this.props = props;
    }

    /**
     * Wendet alle FILE-Blöcke aus {@code claudeResponse} an.
     *
     * @param claudeResponse vollständiger Text der Claude-Antwort
     * @return Liste der Ergebnisse (je ein Eintrag pro erkanntem FILE-Block)
     */
    public List<ApplyResult> apply(String claudeResponse) {
        List<ApplyResult> results = new ArrayList<>();
        Path basePath = Paths.get(props.getApply().getBasePath()).toAbsolutePath().normalize();

        Matcher matcher = FILE_BLOCK_PATTERN.matcher(claudeResponse);
        int matchCount = 0;

        while (matcher.find()) {
            matchCount++;
            String relativePath = matcher.group(1).trim();
            String content      = matcher.group(2);

            results.add(writeFile(basePath, relativePath, content));
        }

        if (matchCount == 0) {
            log.warn("ApplyEngine: Kein FILE-Block in der Claude-Antwort gefunden. " +
                     "Möglicherweise hält sich Claude nicht an das Ausgabeformat.");
        } else {
            log.info("ApplyEngine: {} Datei(en) verarbeitet.", matchCount);
        }

        return results;
    }

    private ApplyResult writeFile(Path basePath, String relativePath, String content) {
        // Pfad-Traversal verhindern
        Path target = basePath.resolve(relativePath).normalize();
        if (!target.startsWith(basePath)) {
            log.error("Sicherheitsfehler: Pfad '{}' liegt außerhalb des Basisverzeichnisses!", relativePath);
            return ApplyResult.skipped(relativePath, "Pfad außerhalb des Basisverzeichnisses – übersprungen");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            log.info("ApplyEngine: Geschrieben → {}", target);
            return ApplyResult.written(target.toString());
        } catch (IOException e) {
            log.error("ApplyEngine: Fehler beim Schreiben von '{}'", target, e);
            return ApplyResult.error(relativePath, e.getMessage());
        }
    }

    /**
     * Erstellt eine menschenlesbare Zusammenfassung für den Trello-Kommentar.
     */
    public String buildSummary(List<ApplyResult> results) {
        if (results.isEmpty()) {
            return "ℹ️ Keine Dateien generiert – Claude hat kein strukturiertes Format zurückgegeben.";
        }

        long written  = results.stream().filter(r -> r.getStatus() == ApplyResult.Status.WRITTEN).count();
        long skipped  = results.stream().filter(r -> r.getStatus() == ApplyResult.Status.SKIPPED).count();
        long errors   = results.stream().filter(r -> r.getStatus() == ApplyResult.Status.ERROR).count();

        StringBuilder sb = new StringBuilder();
        sb.append("⚙️ **Apply Engine – Ergebnis:**\n\n");

        results.forEach(r -> {
            String icon = switch (r.getStatus()) {
                case WRITTEN  -> "✅";
                case SKIPPED  -> "⚠️";
                case ERROR    -> "❌";
            };
            sb.append(icon).append(" `").append(r.getFilePath()).append("`\n");
        });

        sb.append("\n📊 **Gesamt:** %d geschrieben, %d übersprungen, %d Fehler"
                .formatted(written, skipped, errors));
        return sb.toString();
    }
}
