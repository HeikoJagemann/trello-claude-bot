package com.example.trelloclaudebot.dto.internal;

import java.util.Collections;
import java.util.List;

/**
 * Internes Task-Objekt – entkoppelt vom Trello-Payload-Format.
 */
public class InternalTask {

    private final String       cardId;
    private final String       title;
    private final String       description;
    private final String       actionType;
    private final String       listName;
    private final List<String> akzeptanzkriterien;

    public InternalTask(String cardId, String title, String description,
                        String actionType, String listName) {
        this(cardId, title, description, actionType, listName, Collections.emptyList());
    }

    public InternalTask(String cardId, String title, String description,
                        String actionType, String listName, List<String> akzeptanzkriterien) {
        this.cardId             = cardId;
        this.title              = title;
        this.description        = description;
        this.actionType         = actionType;
        this.listName           = listName;
        this.akzeptanzkriterien = akzeptanzkriterien != null ? akzeptanzkriterien : Collections.emptyList();
    }

    public String       getCardId()             { return cardId; }
    public String       getTitle()              { return title; }
    public String       getDescription()        { return description; }
    public String       getActionType()         { return actionType; }
    public String       getListName()           { return listName; }
    public List<String> getAkzeptanzkriterien() { return akzeptanzkriterien; }

    @Override
    public String toString() {
        return "InternalTask{cardId='%s', title='%s', list='%s', actionType='%s', akzeptanzkriterien=%d}"
                .formatted(cardId, title, listName, actionType, akzeptanzkriterien.size());
    }
}
