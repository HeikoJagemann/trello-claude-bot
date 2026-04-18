package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.dto.internal.InternalTask;
import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {

    /**
     * Erstellt einen strukturierten Prompt aus dem InternalTask.
     * Kann später durch Template-Engine oder komplexere Logik ersetzt werden.
     */
    public String build(InternalTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Du bist ein hilfreicher Assistent für Projektmanagement.\n\n");
        sb.append("Eine neue Aufgabe wurde in Trello erstellt oder aktualisiert:\n\n");
        sb.append("**Titel:** ").append(task.getTitle()).append("\n");

        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            sb.append("**Beschreibung:**\n").append(task.getDescription()).append("\n");
        } else {
            sb.append("**Beschreibung:** (keine Beschreibung angegeben)\n");
        }

        sb.append("\nBitte analysiere diese Aufgabe und gib:\n");
        sb.append("1. Eine kurze Einschätzung des Aufwands (Klein / Mittel / Groß)\n");
        sb.append("2. Mögliche Teilaufgaben oder nächste Schritte\n");
        sb.append("3. Eventuelle Rückfragen oder Risiken\n\n");
        sb.append("Antworte präzise und auf Deutsch.");

        return sb.toString();
    }
}
