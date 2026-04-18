package com.example.trelloclaudebot.dto.trello;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrelloWebhookPayload {

    @JsonProperty("action")
    private TrelloAction action;

    public TrelloAction getAction()              { return action; }
    public void setAction(TrelloAction action)   { this.action = action; }
}
