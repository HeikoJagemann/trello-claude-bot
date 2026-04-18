# Trello Claude Bot

Ein Spring Boot Backend, das Trello-Karten automatisch per KI verarbeitet.

- **Backlog-Karten mit Label "Refinement"** → Claude API analysiert die Aufgabe und schätzt Story Points (Fibonacci). Danach wird das Label "Refinement" entfernt und "Ready" gesetzt.
- **Sprint-Karten** → Claude Code CLI implementiert die Aufgabe direkt im lokalen Repo.

## Architektur

```
Trello Board (Polling alle 30s)
        │
        ▼
TrelloPollingService
        │  neue createCard / updateCard Actions
        ▼
TaskOrchestratorService
        │
        ├─ Backlog  ──► Hat Label "Refinement"?
        │                    │ Nein → überspringen
        │                    │ Ja
        │                    ▼
        │              PromptBuilder ──► ClaudeClient (API)
        │                                      │
        │                              Analyse + Story Points
        │                                      │
        │                              TrelloClient → Kommentar
        │                              TrelloClient → Label "Refinement" entfernen
        │                              TrelloClient → Label "Ready" setzen
        │
        └─ Sprint   ──► PromptBuilder ──► ClaudeCodeRunner (CLI)
                                                │
                                        claude -p "..." im Repo-Verzeichnis
                                        (liest/ändert Dateien direkt via Tools)
                                                │
                                        TrelloClient → Summary als Kommentar
```

### Warum Claude Code CLI statt API für Code-Aufgaben?

Die Claude API kennt nur das, was im Prompt steht. Sinnvolle Code-Generierung erfordert
Kontext: bestehende Klassen, Architekturmuster, Imports, Konfiguration. Diesen Kontext
manuell zusammenzustellen ist fehleranfällig und skaliert nicht.

**Claude Code CLI löst das:** Es hat direkt Zugriff auf das Repo über seine nativen Tools
(`Read`, `Grep`, `Glob`, `Edit`, `Write`, `Bash`) und kann selbstständig den nötigen
Kontext ermitteln, bevor es Änderungen vornimmt — genau wie ein Entwickler.

---

## Voraussetzungen

- Java 17+
- Maven 3.8+
- [Claude Code CLI](https://claude.ai/code) installiert und im PATH (`claude --version`)
- Trello API Key & Token ([hier holen](https://trello.com/power-ups/admin))
- Anthropic API Key für die Analyse-Funktion ([hier holen](https://console.anthropic.com))
- Auf dem Trello-Board müssen die Labels **Refinement** und **Ready** angelegt sein

---

## Konfiguration

Die `application.yml` verwendet Umgebungsvariablen. Für die lokale Entwicklung eine Datei
`src/main/resources/application-local.yml` anlegen (wird nicht eingecheckt):

```yaml
app:
  trello:
    api-key: DEIN_TRELLO_API_KEY
    api-token: DEIN_TRELLO_API_TOKEN
    board-id: DEINE_BOARD_ID        # aus der URL: trello.com/b/BOARD_ID/...

  claude:
    api-key: DEIN_ANTHROPIC_API_KEY

  claude-code:
    repo-path: /pfad/zum/lokalen/repo  # Verzeichnis, in dem Claude Code ausgeführt wird
```

Alternativ als Umgebungsvariablen:

```bash
export TRELLO_API_KEY=...
export TRELLO_API_TOKEN=...
export TRELLO_BOARD_ID=...
export CLAUDE_API_KEY=...
export CLAUDE_CODE_REPO_PATH=/pfad/zum/repo
```

### Alle Konfigurationsoptionen

| Property | Standard | Beschreibung |
|----------|----------|-------------|
| `app.trello.poll-interval-ms` | `30000` | Polling-Intervall in ms |
| `app.trello.backlog-list-name` | `Backlog` | Name der Backlog-Liste |
| `app.trello.refinement-label-name` | `Refinement` | Label, das die Analyse auslöst |
| `app.trello.ready-label-name` | `Ready` | Label, das nach der Analyse gesetzt wird |
| `app.claude.model` | `claude-sonnet-4-6` | Claude Modell (für Analyse) |
| `app.claude.max-tokens` | `4096` | Max. Tokens pro Analyse-Antwort |
| `app.claude-code.repo-path` | `.` | Repo-Verzeichnis für Claude Code CLI |

---

## Starten

### IntelliJ

Die Run Configuration **TrelloClaudeBotApplication** ist im Repository eingecheckt
(`.idea/runConfigurations/`) und startet die App automatisch mit dem Profil `local`.
Einfach oben rechts auswählen und auf ▶ klicken.

### Kommandozeile

**Mit lokalem Profil** (empfohlen für Entwicklung):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

**Mit Umgebungsvariablen:**

```bash
TRELLO_API_KEY=xxx TRELLO_API_TOKEN=yyy TRELLO_BOARD_ID=zzz \
CLAUDE_API_KEY=aaa CLAUDE_CODE_REPO_PATH=/pfad/zum/repo \
  mvn spring-boot:run
```

**Als JAR:**

```bash
mvn clean package -DskipTests
java -jar target/trello-claude-bot-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Der Server startet auf Port **8085** (kein öffentlicher Endpunkt erforderlich).

---

## Projektstruktur

```
src/main/java/com/example/trelloclaudebot/
├── TrelloClaudeBotApplication.java
├── config/
│   ├── AppProperties.java            # Typisierte Konfiguration (Trello, Claude, ClaudeCode)
│   └── WebClientConfig.java          # WebClient Beans (Claude + Trello)
├── service/
│   ├── TrelloPollingService.java     # @Scheduled Polling des Trello-Boards
│   ├── TaskOrchestratorService.java  # Routing: Backlog → API, Sprint → CLI
│   ├── PromptBuilder.java            # Prompts für Analyse und Implementierung
│   └── ClaudeCodeRunner.java         # ProcessBuilder → claude -p "..." im Repo
├── client/
│   ├── ClaudeClient.java             # Anthropic Messages API (Analyse)
│   └── TrelloClient.java             # Trello REST API (Polling, Labels, Kommentar)
└── dto/
    ├── internal/
    │   └── InternalTask.java         # Internes Task-Objekt
    ├── trello/                       # Trello Action/Label DTOs
    └── claude/                       # Claude Request/Response DTOs
```

---

## Verarbeitete Trello-Events

| Action-Type  | Beschreibung        |
|--------------|---------------------|
| `createCard` | Neue Karte angelegt |
| `updateCard` | Karte bearbeitet    |

Alle anderen Events werden stillschweigend ignoriert.

---

## Sicherheit

- API Keys werden **nicht** ins Repository eingecheckt — nur `${ENV_VAR}`-Platzhalter
- `application-local.yml` ist in `.gitignore` eingetragen

---

## Nächste Schritte

- [ ] Asynchrone Verarbeitung (`@Async`) damit der Polling-Thread nicht blockiert
- [ ] Persistenz: bereits verarbeitete Action-IDs speichern (verhindert Doppelverarbeitung nach Neustart)
- [ ] Konfigurierbare Listen-Namen → Routing-Regeln (z.B. "Review" → Review-Prompt)
- [ ] Docker-Support
