# Trello Claude Bot

Ein Spring Boot Backend, das Trello-Karten automatisch per KI analysiert und die Ergebnisse als Kommentar zurückschreibt.

## Funktionsweise

```
Trello Webhook  →  TrelloWebhookController
                        │
                        ▼
               TaskOrchestratorService
                        │
              ┌─────────┼──────────┐
              ▼         ▼          ▼
        InternalTask  PromptBuilder  ClaudeClient
                                        │
                                        ▼
                                   TrelloClient
                                   (Kommentar)
```

1. Eine Trello-Karte wird erstellt oder aktualisiert
2. Trello sendet ein Webhook-Event an `POST /webhook/trello`
3. Das Backend extrahiert Kartentitel und Beschreibung
4. Ein strukturierter Prompt wird generiert
5. Claude analysiert die Aufgabe
6. Die Antwort wird als Kommentar auf die Karte geschrieben

## Voraussetzungen

- Java 17+
- Maven 3.8+
- Trello API Key & Token ([hier holen](https://trello.com/power-ups/admin))
- Anthropic API Key ([hier holen](https://console.anthropic.com))

## Konfiguration

`src/main/resources/application.yml` ausfüllen:

```yaml
app:
  trello:
    api-key: DEIN_TRELLO_API_KEY
    api-token: DEIN_TRELLO_API_TOKEN

  claude:
    api-key: DEIN_ANTHROPIC_API_KEY
    model: claude-sonnet-4-6   # optional anpassen
    max-tokens: 1024           # optional anpassen
```

Alternativ als Umgebungsvariablen:

```bash
export APP_TRELLO_API_KEY=...
export APP_TRELLO_API_TOKEN=...
export APP_CLAUDE_API_KEY=...
```

## Starten

```bash
mvn spring-boot:run
```

oder als JAR:

```bash
mvn clean package -DskipTests
java -jar target/trello-claude-bot-0.0.1-SNAPSHOT.jar
```

Der Server startet auf Port `8080`.

## Webhook bei Trello registrieren

Trello benötigt eine öffentlich erreichbare URL. Für lokale Entwicklung empfiehlt sich [ngrok](https://ngrok.com):

```bash
ngrok http 8080
```

Dann den Webhook registrieren:

```bash
curl -s -X POST "https://api.trello.com/1/webhooks" \
  -d "key=DEIN_KEY" \
  -d "token=DEIN_TOKEN" \
  -d "callbackURL=https://DEINE_NGROK_URL/webhook/trello" \
  -d "idModel=DEINE_BOARD_ID" \
  -d "description=Claude Bot"
```

> Die Board-ID findest du in der Trello-URL: `trello.com/b/**BOARD_ID**/...`

## Projektstruktur

```
src/main/java/com/example/trelloclaudebot/
├── TrelloClaudeBotApplication.java
├── config/
│   ├── AppProperties.java          # Typisierte Konfiguration
│   └── WebClientConfig.java        # WebClient Beans (Claude + Trello)
├── controller/
│   └── TrelloWebhookController.java  # POST + HEAD /webhook/trello
├── service/
│   ├── TaskOrchestratorService.java  # Verarbeitungsfluss
│   └── PromptBuilder.java            # Prompt-Generierung
├── client/
│   ├── ClaudeClient.java             # Anthropic Messages API
│   └── TrelloClient.java             # Trello REST API
└── dto/
    ├── internal/InternalTask.java    # Internes Task-Objekt
    ├── trello/                       # Trello Webhook DTOs
    └── claude/                       # Claude API DTOs
```

## Verarbeitete Trello-Events

Aktuell werden nur folgende Action-Types verarbeitet (konfigurierbar in `TaskOrchestratorService`):

| Action-Type  | Beschreibung              |
|--------------|---------------------------|
| `createCard` | Neue Karte angelegt       |
| `updateCard` | Karte bearbeitet          |

Alle anderen Events werden ignoriert.

## Nächste Schritte

- [ ] Asynchrone Verarbeitung (`@Async`) für schnellere Webhook-Antwort
- [ ] HMAC-Signatur-Validierung des Trello Webhooks
- [ ] Konfigurierbare Prompts pro Board oder Liste
- [ ] Persistenz (Task-Historie, Fehlerlog)
- [ ] Docker-Support
