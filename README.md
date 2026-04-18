# Trello Claude Bot

Ein Spring Boot Backend, das Trello-Karten automatisch per KI verarbeitet, daraus Code generiert und die erzeugten Dateien direkt auf Disk schreibt.

## Funktionsweise

```
Trello Board (Polling)
        │
        ▼  alle 30s
TrelloPollingService
        │  neue createCard / updateCard Actions
        ▼
TaskOrchestratorService
        │
        ├─► PromptBuilder       → strukturierter Prompt (FILE:-Format)
        │
        ├─► ClaudeClient        → Claude API aufrufen
        │
        ├─► ApplyEngine         → FILE-Blöcke parsen, Dateien schreiben
        │
        └─► TrelloClient        → Summary als Kommentar auf die Karte
```

### Ablauf Schritt für Schritt

1. `TrelloPollingService` pollt das konfigurierte Board alle 30 Sekunden
2. Neue `createCard`- oder `updateCard`-Actions werden erkannt
3. `PromptBuilder` generiert einen Prompt, der Claude anweist, Code im `FILE:`-Format zurückzugeben
4. `ClaudeClient` ruft die Anthropic Messages API auf
5. `ApplyEngine` parst die Antwort und schreibt jede erkannte Datei in das konfigurierte Zielverzeichnis
6. `TrelloClient` schreibt einen Kommentar mit dem Ergebnis (✅ geschrieben / ❌ Fehler) zurück auf die Karte

### Claude Ausgabeformat (Apply-Engine-kompatibel)

Claude wird angewiesen, ausschließlich folgendes Format zurückzugeben:

```
FILE: src/main/java/com/example/MyService.java
```java
// vollständiger Dateiinhalt
```

FILE: src/main/java/com/example/MyController.java
```java
// vollständiger Dateiinhalt
```
```

Die `ApplyEngine` erkennt alle `FILE:`-Blöcke per Regex und schreibt sie direkt ins konfigurierte `apply.base-path`.

---

## Voraussetzungen

- Java 17+
- Maven 3.8+
- Trello API Key & Token ([hier holen](https://trello.com/power-ups/admin))
- Anthropic API Key ([hier holen](https://console.anthropic.com))

---

## Konfiguration

Die `application.yml` verwendet Umgebungsvariablen. Für die lokale Entwicklung eine Datei `src/main/resources/application-local.yml` anlegen (wird nicht eingecheckt):

```yaml
app:
  trello:
    api-key: DEIN_TRELLO_API_KEY
    api-token: DEIN_TRELLO_API_TOKEN
    board-id: DEINE_BOARD_ID       # aus der URL: trello.com/b/BOARD_ID/...

  claude:
    api-key: DEIN_ANTHROPIC_API_KEY
```

Alternativ als Umgebungsvariablen:

```bash
export TRELLO_API_KEY=...
export TRELLO_API_TOKEN=...
export TRELLO_BOARD_ID=...
export CLAUDE_API_KEY=...
```

### Weitere Optionen (`application.yml`)

| Property | Standard | Beschreibung |
|----------|----------|-------------|
| `app.trello.poll-interval-ms` | `30000` | Polling-Intervall in ms |
| `app.claude.model` | `claude-sonnet-4-6` | Claude Modell |
| `app.claude.max-tokens` | `4096` | Max. Tokens in der Antwort |
| `app.apply.base-path` | `./generated` | Zielverzeichnis für generierte Dateien |

---

## Starten

**Mit lokalem Profil** (empfohlen für Entwicklung):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

**Mit Umgebungsvariablen:**

```bash
TRELLO_API_KEY=xxx TRELLO_API_TOKEN=yyy TRELLO_BOARD_ID=zzz CLAUDE_API_KEY=aaa \
  mvn spring-boot:run
```

**Als JAR:**

```bash
mvn clean package -DskipTests
java -jar target/trello-claude-bot-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Der Server startet auf Port `8080` (kein öffentlicher Endpunkt erforderlich).

---

## Projektstruktur

```
src/main/java/com/example/trelloclaudebot/
├── TrelloClaudeBotApplication.java
├── config/
│   ├── AppProperties.java            # Typisierte Konfiguration (Trello, Claude, Apply)
│   └── WebClientConfig.java          # WebClient Beans (Claude + Trello)
├── service/
│   ├── TrelloPollingService.java     # @Scheduled Polling des Trello-Boards
│   ├── TaskOrchestratorService.java  # Verarbeitungsfluss
│   ├── PromptBuilder.java            # Strukturierter Code-Generierungs-Prompt
│   └── ApplyEngine.java              # Parst FILE-Blöcke, schreibt Dateien
├── client/
│   ├── ClaudeClient.java             # Anthropic Messages API
│   └── TrelloClient.java             # Trello REST API (Polling + Kommentar)
└── dto/
    ├── internal/
    │   ├── InternalTask.java         # Internes Task-Objekt
    │   └── ApplyResult.java          # Ergebnis je geschriebener Datei (WRITTEN/SKIPPED/ERROR)
    ├── trello/                       # Trello Action DTOs
    └── claude/                       # Claude Request/Response DTOs
```

---

## Verarbeitete Trello-Events

| Action-Type  | Beschreibung        |
|--------------|---------------------|
| `createCard` | Neue Karte angelegt |
| `updateCard` | Karte bearbeitet    |

Alle anderen Events werden stillschweigend ignoriert. Die Liste ist in `TrelloPollingService` konfigurierbar.

---

## Sicherheit

- API Keys werden **nicht** ins Repository eingecheckt — nur `${ENV_VAR}`-Platzhalter
- `application-local.yml` ist in `.gitignore` eingetragen
- `ApplyEngine` prüft jeden Pfad auf Directory-Traversal (`../`-Angriffe werden blockiert)

---

## Nächste Schritte

- [ ] Asynchrone Verarbeitung (`@Async`) damit der Polling-Thread nicht blockiert
- [ ] Konfigurierbare Prompts pro Board-Liste (z.B. "Backlog" → Code, "Analyse" → Review)
- [ ] Persistenz: bereits verarbeitete Action-IDs speichern (verhindert Doppelverarbeitung nach Neustart)
- [ ] Docker-Support
- [ ] Unit-Tests für `ApplyEngine` (Regex, Pfad-Validierung)
