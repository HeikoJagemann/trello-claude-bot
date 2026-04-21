package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.config.AppProperties;
import com.example.trelloclaudebot.dto.internal.InternalTask;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptBuilder {

    private final AppProperties props;

    public PromptBuilder(AppProperties props) {
        this.props = props;
    }

    /**
     * Analyse-Prompt für Backlog-Karten.
     * Claude gibt ein JSON-Objekt zurück, das von {@code AnalysisResultParser} verarbeitet wird.
     * Das Ergebnis wird als Kartenbeschreibung, Custom Field (Story Points)
     * und Checkliste (Akzeptanzkriterien) in Trello eingetragen.
     */
    public String buildAnalysisPrompt(InternalTask task) {
        String repoHinweis = buildRepoHinweis();

        return """
                Du bist ein erfahrener Softwareentwickler, der ein Backlog-Refinement durchführt. \
                Deine Aufgabe ist es, eine User Story technisch zu analysieren, den Aufwand auf \
                Basis des tatsächlichen Codes realistisch einzuschätzen und konkrete, testbare \
                Akzeptanzkriterien zu formulieren. Antworte ausschließlich auf Deutsch.

                SCHRITT 1 – CODE LESEN:
                Verschaffe dir zuerst einen genauen Überblick über den tatsächlichen Code in \
                ALLEN Projekten des Verbunds. Nutze deine Tools (Glob, Grep, Read), um alle \
                relevanten Dateien zu finden und zu lesen. Mache keine Annahmen über vorhandene \
                Klassen, Methoden, Datenstrukturen oder Architektur – prüfe alles direkt im Code.
                %s
                Typische Sucheinstiege:
                - Grep nach Schlüsselbegriffen aus dem Kartentitel (Entitätsnamen, Feature-Namen)
                - Read der gefundenen Controller, Services, DTOs, Entities, GDScript-Dateien, \
                WPF-ViewModels etc.
                - Prüfe, welche Teile bereits vorhanden sind und was noch fehlt

                SCHRITT 2 – EINSCHÄTZEN UND AUSGEBEN:
                Nur auf Basis des gelesenen Codes schätze Aufwand und formuliere Akzeptanzkriterien.

                WICHTIG – AUSGABE:
                Deine Antwort besteht ausschließlich aus einem einzigen JSON-Objekt. \
                Kein Text davor oder danach, keine Erklärung, kein Markdown außer dem JSON selbst.

                JSON-FORMAT (exakt, keine Abweichung):
                {
                  "storyPoints": <Ganzzahl aus der Fibonacci-Folge: 1, 2, 3, 5, 8, 13 oder 21>,
                  "analyse": "<2–4 Sätze: Was ist die Aufgabe, was ist der Kontext? Bezug auf den tatsächlichen Code nehmen.>",
                  "begruendung": "<Warum diese Punktzahl? Welche konkreten Änderungen sind nötig (Klassen, Dateien, Methoden)?>",
                  "akzeptanzkriterien": [
                    "<Kriterium 1 – konkret und testbar>",
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
                repoHinweis,
                task.getTitle(),
                task.getDescription().isBlank() ? "(keine Beschreibung angegeben)" : task.getDescription()
        );
    }

    /**
     * Code-Implementierungs-Prompt für Sprint- und Bugs-Listen.
     * Wird direkt an Claude Code CLI übergeben – Claude Code arbeitet über seine eigenen
     * Tools (Read, Edit, Write, Bash, …) direkt in den Repos.
     */
    public String buildCodePrompt(InternalTask task) {
        String akSection    = buildAkzeptanzkriterienSection(task);
        String repoRegeln   = buildRepoRegeln();

        return """
                Du bist ein erfahrener Softwareentwickler. Du arbeitest in einem Projektverbund \
                aus mehreren Repositories. Nutze deine verfügbaren Tools (Read, Grep, Glob, Edit, \
                Write, Bash), um dir zunächst einen Überblick über den relevanten Code zu \
                verschaffen, und implementiere dann die folgende Aufgabe vollständig und \
                produktionsreif.

                Regeln:
                %s
                - Halte dich an den bestehenden Code-Stil und die vorhandene Architektur
                - Schreibe keinen Pseudocode und keine Platzhalter – der Code muss direkt lauffähig sein
                - Alle Akzeptanzkriterien müssen erfüllt sein bevor du fertig bist
                - Kompiliere nach jeder Änderung das betroffene Projekt (z.B. `mvn compile -q` \
                für Maven, `dotnet build` für .NET). Schlägt der Build fehl, behebe den Fehler \
                bevor du weitermachst. Liefere keinen Code ab, der nicht kompiliert.
                - Committe und pushe deine Änderungen am Ende in jedem betroffenen Projekt separat: \
                `cd <projekt-pfad> && git add -A && git commit -m "<kurze Beschreibung>" && git push`. \
                Ohne Commit und Push werden die Änderungen nicht deployed.
                - Fasse am Ende kurz zusammen, was du in welchem Repo geändert hast (max. 5 Bullet Points)

                ---

                AUFGABE:

                **Titel:** %s

                **Beschreibung:**
                %s
                %s
                """.formatted(
                repoRegeln,
                task.getTitle(),
                task.getDescription().isBlank() ? "(keine Beschreibung angegeben)" : task.getDescription(),
                akSection
        );
    }

    // ── Repo-Abschnitte ───────────────────────────────────────────────────────

    /**
     * Hinweis für den Analyse-Prompt: welche Repos es gibt und wo sie liegen.
     */
    private String buildRepoHinweis() {
        List<AppProperties.Repo> repos = props.getClaudeCode().getRepos();
        if (repos.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(
                "Die folgenden Repositories gehören zum Projektverbund – suche in allen nach relevantem Code:\n");
        for (AppProperties.Repo repo : repos) {
            sb.append("- **").append(repo.getName()).append("**: `").append(repo.getPath()).append("`\n");
        }
        return sb.toString();
    }

    /**
     * Regeln für den Implementierungs-Prompt: alle Repos prüfen, kein vorzeitiges Fertig.
     */
    private String buildRepoRegeln() {
        List<AppProperties.Repo> repos = props.getClaudeCode().getRepos();
        if (repos.isEmpty()) {
            return "- Lies zuerst die relevanten Dateien, bevor du Änderungen vornimmst";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- Lies zuerst die relevanten Dateien in ALLEN Repos des Projektverbunds:\n");
        for (AppProperties.Repo repo : repos) {
            sb.append("    * **").append(repo.getName()).append("**: `").append(repo.getPath()).append("`\n");
        }
        sb.append("- Prüfe für jedes Repo explizit, ob dort Änderungen nötig sind. ")
          .append("Wenn ein Repo nicht betroffen ist, begründe kurz warum. ")
          .append("Schließe kein Repo ohne Prüfung aus.\n");
        sb.append("- \"Der Code ist bereits implementiert\" ist nur dann gültig, wenn du ")
          .append("den Code in ALLEN Repos gelesen und geprüft hast.");

        return sb.toString();
    }

    // ── Akzeptanzkriterien ────────────────────────────────────────────────────

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
