package com.example.trelloclaudebot.dto.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaudeContentBlock {

    @JsonProperty("type")
    private String type;

    @JsonProperty("text")
    private String text;

    public String getType() { return type; }
    public String getText() { return text; }

    public void setType(String type) { this.type = type; }
    public void setText(String text) { this.text = text; }
}
