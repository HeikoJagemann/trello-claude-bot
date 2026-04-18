package com.example.trelloclaudebot.dto.internal;

/**
 * Internes Task-Objekt – entkoppelt vom Trello-Payload-Format.
 */
public class InternalTask {

    private final String cardId;
    private final String title;
    private final String description;
    private final String actionType;

    public InternalTask(String cardId, String title, String description, String actionType) {
        this.cardId      = cardId;
        this.title       = title;
        this.description = description;
        this.actionType  = actionType;
    }

    public String getCardId()      { return cardId; }
    public String getTitle()       { return title; }
    public String getDescription() { return description; }
    public String getActionType()  { return actionType; }

    @Override
    public String toString() {
        return "InternalTask{cardId='%s', title='%s', actionType='%s'}"
                .formatted(cardId, title, actionType);
    }
}
