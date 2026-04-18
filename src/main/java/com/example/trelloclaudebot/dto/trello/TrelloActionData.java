package com.example.trelloclaudebot.dto.trello;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrelloActionData {

    @JsonProperty("card")
    private TrelloCardData card;

    @JsonProperty("list")
    private TrelloListData list;

    public TrelloCardData getCard() { return card; }
    public TrelloListData getList() { return list; }

    public void setCard(TrelloCardData card) { this.card = card; }
    public void setList(TrelloListData list) { this.list = list; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrelloListData {
        @JsonProperty("name")
        private String name;

        public String getName()          { return name; }
        public void setName(String name) { this.name = name; }
    }
}
