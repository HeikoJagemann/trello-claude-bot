package com.example.trelloclaudebot.dto.trello;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrelloAction {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("date")
    private String date;

    @JsonProperty("data")
    private TrelloActionData data;

    public String           getId()   { return id; }
    public String           getType() { return type; }
    public String           getDate() { return date; }
    public TrelloActionData getData() { return data; }

    public void setId(String id)               { this.id = id; }
    public void setType(String type)           { this.type = type; }
    public void setDate(String date)           { this.date = date; }
    public void setData(TrelloActionData data) { this.data = data; }
}
