package com.example.trelloclaudebot.dto.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaudeResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("content")
    private List<ClaudeContentBlock> content;

    public String                    getId()      { return id; }
    public List<ClaudeContentBlock>  getContent() { return content; }

    public void setId(String id)                             { this.id = id; }
    public void setContent(List<ClaudeContentBlock> content) { this.content = content; }

    /**
     * Gibt den Text des ersten content-Blocks zurück (Normalfall bei text-Antworten).
     */
    public String extractText() {
        if (content == null || content.isEmpty()) return "";
        return content.stream()
                .filter(b -> "text".equals(b.getType()))
                .map(ClaudeContentBlock::getText)
                .findFirst()
                .orElse("");
    }
}
