package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.dto.internal.InternalTask;
import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {

    /**
     * Analyse-Prompt für Backlog-Karten.
     * Claude gibt einen strukturierten Kommentar zurück – kein Code, kein FILE:-Format.
     * Enthält Aufwandsschätzung in Story Points (Fibonacci) und eine kurze Analyse.
     */
    public String buildAnalysisPrompt(InternalTask task) {
        return """
                Du bist ein erfahrener Agile Coach und Software-Architekt.

                Analysiere die folgende Aufgabe aus dem Backlog und erstelle eine strukturierte \
                Aufwandsschätzung. Antworte ausschließlich auf Deutsch.

                WICHTIG: Gib NUR den Kommentar-Text zurück, keinen einleitenden Satz, keine \
                Erklärung deiner Vorgehensweise.

                ---

                AUSGABEFORMAT (exakt so, ohne Abweichung):

                📋 **Analyse**
                <2–4 Sätze: Was ist die Aufgabe, was ist der Kontext?>

                🎯 **Story Points: <Zahl>**
                Skala: 1 · 2 · 3 · 5 · 8 · 13 · 21 (Fibonacci)

                📊 **Begründung**
                <Warum diese Punktzahl? Welche Faktoren bestimmen den Aufwand?>

                ✅ **Akzeptanzkriterien (Vorschlag)**
                - <Kriterium 1>
                - <Kriterium 2>
                - <Kriterium 3>

                ⚠️ **Risiken & Annahmen**
                - <Risiko oder Annahme 1>
                - <Risiko oder Annahme 2>

                ---

                AUFGABE:

                **Titel:** %s

                **Beschreibung:**
                %s
                """.formatted(
                task.getTitle(),
                task.getDescription().isBlank() ? "(keine Beschreibung angegeben)" : task.getDescription()
        );
    }

    /**
     * Code-Generierungs-Prompt für alle anderen Listen.
     * Claude gibt FILE-Blöcke zurück, die von der ApplyEngine verarbeitet werden.
     */
    public String buildCodePrompt(InternalTask task) {
        return """
                Du bist ein erfahrener Java Softwareentwickler mit Fokus auf sauberen, produktionsreifen Code.

                Deine Aufgabe ist es, Code so zu erzeugen, dass er automatisch von einem System verarbeitet \
                und direkt in Dateien geschrieben werden kann.

                WICHTIG: Dein Output wird von einer Apply Engine geparst. Halte dich strikt an das Format.

                ---

                AUSGABEFORMAT (STRICT – KEINE ABWEICHUNG):

                Für jede Datei:

                FILE: <relativer/pfad/zur/datei>
                ```<sprache>
                <vollständiger code der datei>
                ```

                Regeln:
                - Gib NUR die FILE-Blöcke aus, keinen erklärenden Text davor oder danach
                - Jeder Block beginnt exakt mit "FILE:" in einer eigenen Zeile
                - Der Pfad ist relativ zum Projektroot (z.B. src/main/java/com/example/Foo.java)
                - Der Code ist vollständig und direkt ausführbar – keine Platzhalter, kein Pseudocode
                - Wenn mehrere Dateien betroffen sind, gib alle nacheinander aus

                ---

                AUFGABE:

                **Titel:** %s

                **Beschreibung:**
                %s
                """.formatted(
                task.getTitle(),
                task.getDescription().isBlank() ? "(keine Beschreibung angegeben)" : task.getDescription()
        );
    }
}
