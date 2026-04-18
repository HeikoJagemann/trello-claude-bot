package com.example.trelloclaudebot.dto.claude;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ClaudeRequest {

    @JsonProperty("model")
    private final String model;

    @JsonProperty("max_tokens")
    private final int maxTokens;

    @JsonProperty("messages")
    private final List<ClaudeMessage> messages;

    public ClaudeRequest(String model, int maxTokens, List<ClaudeMessage> messages) {
        this.model     = model;
        this.maxTokens = maxTokens;
        this.messages  = messages;
    }

    public String              getModel()     { return model; }
    public int                 getMaxTokens() { return maxTokens; }
    public List<ClaudeMessage> getMessages()  { return messages; }
}
