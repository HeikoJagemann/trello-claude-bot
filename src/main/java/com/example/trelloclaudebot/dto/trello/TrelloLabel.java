package com.example.trelloclaudebot.dto.trello;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrelloLabel {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("color")
    private String color;

    public String getId()    { return id; }
    public String getName()  { return name; }
    public String getColor() { return color; }

    public void setId(String id)       { this.id = id; }
    public void setName(String name)   { this.name = name; }
    public void setColor(String color) { this.color = color; }

    @Override
    public String toString() {
        return "TrelloLabel{id='%s', name='%s', color='%s'}".formatted(id, name, color);
    }
}
