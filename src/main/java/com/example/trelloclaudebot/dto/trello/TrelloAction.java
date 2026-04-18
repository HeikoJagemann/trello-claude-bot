package com.example.trelloclaudebot.dto.trello;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrelloAction {

    @JsonProperty("type")
    private String type;

    @JsonProperty("data")
    private TrelloActionData data;

    public String           getType() { return type; }
    public TrelloActionData getData() { return data; }

    public void setType(String type)           { this.type = type; }
    public void setData(TrelloActionData data) { this.data = data; }
}
