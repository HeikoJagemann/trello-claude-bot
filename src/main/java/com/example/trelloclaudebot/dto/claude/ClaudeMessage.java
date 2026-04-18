package com.example.trelloclaudebot.dto.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClaudeMessage {

    @JsonProperty("role")
    private final String role;

    @JsonProperty("content")
    private final String content;

    public ClaudeMessage(String role, String content) {
        this.role    = role;
        this.content = content;
    }

    public String getRole()    { return role; }
    public String getContent() { return content; }
}
