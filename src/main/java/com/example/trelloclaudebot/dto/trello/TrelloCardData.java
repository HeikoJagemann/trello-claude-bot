package com.example.trelloclaudebot.dto.trello;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrelloCardData {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("desc")
    private String desc;

    public String getId()   { return id; }
    public String getName() { return name; }
    public String getDesc() { return desc; }

    public void setId(String id)     { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDesc(String desc) { this.desc = desc; }
}
