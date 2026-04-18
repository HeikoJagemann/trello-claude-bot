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
     * Code-Implementierungs-Prompt für alle Sprint-Listen.
     * Wird direkt an Claude Code CLI übergeben – kein FILE:-Format nötig,
     * Claude Code arbeitet über seine eigenen Tools (Read, Edit, Write, Bash, …)
     * direkt im Repo.
     */
    public String buildCodePrompt(InternalTask task) {
        return """
                Du bist ein erfahrener Java-Softwareentwickler. Du arbeitest in einem bestehenden \
                Spring Boot Projekt. Nutze deine verfügbaren Tools (Read, Grep, Glob, Edit, Write, Bash), \
                um dir zunächst einen Überblick über den relevanten Code zu verschaffen, \
                und implementiere dann die folgende Aufgabe vollständig und produktionsreif.

                Regeln:
                - Lies zuerst die relevanten Dateien, bevor du Änderungen vornimmst
                - Halte dich an den bestehenden Code-Stil und die vorhandene Architektur
                - Schreibe keinen Pseudocode und keine Platzhalter – der Code muss direkt lauffähig sein
                - Fasse am Ende kurz zusammen, was du geändert hast (max. 5 Bullet Points)

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
