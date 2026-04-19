package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.dto.internal.InternalTask;
import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {

    /**
     * Analyse-Prompt für Backlog-Karten.
     * Claude gibt ein JSON-Objekt zurück, das von {@code AnalysisResultParser} verarbeitet wird.
     * Das Ergebnis wird als Kartenbeschreibung, Custom Field (Story Points)
     * und Checkliste (Akzeptanzkriterien) in Trello eingetragen.
     */
    public String buildAnalysisPrompt(InternalTask task) {
        return """
                Du bist ein erfahrener Agile Coach und Software-Architekt.
                Analysiere die folgende Aufgabe aus dem Backlog und erstelle eine strukturierte \
                Aufwandsschätzung. Antworte ausschließlich auf Deutsch.

                WICHTIG: Deine Antwort besteht ausschließlich aus einem einzigen JSON-Objekt. \
                Kein Text davor oder danach, keine Erklärung, kein Markdown außer dem JSON selbst.

                JSON-FORMAT (exakt, keine Abweichung):
                {
                  "storyPoints": <Ganzzahl aus der Fibonacci-Folge: 1, 2, 3, 5, 8, 13 oder 21>,
                  "analyse": "<2–4 Sätze: Was ist die Aufgabe, was ist der Kontext?>",
                  "begruendung": "<Warum diese Punktzahl? Welche Faktoren bestimmen den Aufwand?>",
                  "akzeptanzkriterien": [
                    "<Kriterium 1>",
                    "<Kriterium 2>",
                    "<Kriterium 3>"
                  ],
                  "risiken": [
                    "<Risiko oder Annahme 1>",
                    "<Risiko oder Annahme 2>"
                  ]
                }

                AUFGABE:

                Titel: %s

                Beschreibung:
                %s
                """.formatted(
                task.getTitle(),
                task.getDescription().isBlank() ? "(keine Beschreibung angegeben)" : task.getDescription()
        );
    }

    /**
     * Code-Implementierungs-Prompt für alle Sprint-Listen.
     * Wird direkt an Claude Code CLI übergeben – kein FILE:-Format nötig,
     * Claude Code arbeitet über seine eigenen Tools (Read, Edit, Write, Bash, …)
     * direkt im Repo.
     */
    public String buildCodePrompt(InternalTask task) {
        String akSection = buildAkzeptanzkriterienSection(task);

        return """
                Du bist ein erfahrener Softwareentwickler. Du arbeitest in einem bestehenden Projekt. \
                Nutze deine verfügbaren Tools (Read, Grep, Glob, Edit, Write, Bash), \
                um dir zunächst einen Überblick über den relevanten Code zu verschaffen, \
                und implementiere dann die folgende Aufgabe vollständig und produktionsreif.

                Regeln:
                - Lies zuerst die relevanten Dateien, bevor du Änderungen vornimmst
                - Halte dich an den bestehenden Code-Stil und die vorhandene Architektur
                - Schreibe keinen Pseudocode und keine Platzhalter – der Code muss direkt lauffähig sein
                - Alle Akzeptanzkriterien müssen erfüllt sein bevor du fertig bist
                - Kompiliere nach jeder Änderung das betroffene Projekt (z.B. `mvn compile -q` \
                für Maven, `dotnet build` für .NET, `./gradlew compileJava` für Gradle). \
                Schlägt der Build fehl, behebe den Fehler bevor du weitermachst oder abschließt. \
                Liefere keinen Code ab, der nicht kompiliert.
                - Committe deine Änderungen am Ende mit aussagekräftiger Commit-Message: \
                `git add -A && git commit -m "<kurze Beschreibung der Änderung>"`. \
                Ohne Commit werden die Änderungen nicht deployed.
                - Fasse am Ende kurz zusammen, was du geändert hast (max. 5 Bullet Points)

                ---

                AUFGABE:

                **Titel:** %s

                **Beschreibung:**
                %s
                %s
                """.formatted(
                task.getTitle(),
                task.getDescription().isBlank() ? "(keine Beschreibung angegeben)" : task.getDescription(),
                akSection
        );
    }

    private String buildAkzeptanzkriterienSection(InternalTask task) {
        if (task.getAkzeptanzkriterien().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n**Definition of Done (Akzeptanzkriterien):**\n");
        List<String> kriterien = task.getAkzeptanzkriterien();
        for (int i = 0; i < kriterien.size(); i++) {
            sb.append(i + 1).append(". ").append(kriterien.get(i)).append("\n");
        }
        sb.append("""

                **WICHTIG – Abschlusszeile:** Gib in der allerletzten Zeile deiner Antwort an, \
                welche Akzeptanzkriterien du tatsächlich erfüllt hast:
                ERLEDIGT: 1, 2, 3
                (Nur die Nummern der wirklich erledigten Kriterien, kommagetrennt. \
                Nicht erledigte Kriterien weglassen.)
                """);
        return sb.toString();
    }
}
