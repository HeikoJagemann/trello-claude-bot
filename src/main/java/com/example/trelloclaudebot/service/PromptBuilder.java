package com.example.trelloclaudebot.service;

import com.example.trelloclaudebot.dto.internal.InternalTask;
import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {

    /**
     * Erstellt einen strukturierten Code-Generierungs-Prompt.
     * Die Antwort von Claude wird von der ApplyEngine geparst:
     * jeder FILE-Block wird direkt als Datei auf Disk geschrieben.
     */
    public String build(InternalTask task) {
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
