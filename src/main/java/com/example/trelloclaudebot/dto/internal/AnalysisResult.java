package com.example.trelloclaudebot.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Strukturiertes Ergebnis einer Backlog-Analyse durch Claude Code CLI.
 * Wird aus dem JSON-Output des Analyse-Prompts geparst.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResult {

    @JsonProperty("storyPoints")
    private int storyPoints;

    @JsonProperty("analyse")
    private String analyse = "";

    @JsonProperty("begruendung")
    private String begruendung = "";

    @JsonProperty("akzeptanzkriterien")
    private List<String> akzeptanzkriterien = new ArrayList<>();

    @JsonProperty("risiken")
    private List<String> risiken = new ArrayList<>();

    public int            getStoryPoints()        { return storyPoints; }
    public String         getAnalyse()            { return analyse; }
    public String         getBegruendung()        { return begruendung; }
    public List<String>   getAkzeptanzkriterien() { return akzeptanzkriterien; }
    public List<String>   getRisiken()            { return risiken; }

    public void setStoryPoints(int storyPoints)                       { this.storyPoints = storyPoints; }
    public void setAnalyse(String analyse)                             { this.analyse = analyse; }
    public void setBegruendung(String begruendung)                     { this.begruendung = begruendung; }
    public void setAkzeptanzkriterien(List<String> akzeptanzkriterien) { this.akzeptanzkriterien = akzeptanzkriterien; }
    public void setRisiken(List<String> risiken)                       { this.risiken = risiken; }
}
