package com.example.trelloclaudebot.controller;

import com.example.trelloclaudebot.dto.trello.TrelloWebhookPayload;
import com.example.trelloclaudebot.service.TaskOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/trello")
public class TrelloWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TrelloWebhookController.class);

    private final TaskOrchestratorService orchestratorService;

    public TrelloWebhookController(TaskOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    /**
     * Trello sendet einen HEAD Request zur Webhook-URL-Validierung.
     * Muss mit HTTP 200 beantwortet werden.
     */
    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> validateWebhook() {
        log.debug("Trello Webhook Validierung (HEAD)");
        return ResponseEntity.ok().build();
    }

    /**
     * Empfängt Trello-Events und startet die Verarbeitung.
     */
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<Void> handleWebhook(@RequestBody TrelloWebhookPayload payload) {
        log.info("Trello Webhook empfangen: Action-Type={}",
                payload.getAction() != null ? payload.getAction().getType() : "unbekannt");

        orchestratorService.process(payload);

        return ResponseEntity.ok().build();
    }
}
