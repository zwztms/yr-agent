package com.yragent.domain.gate;

public class PendingDecision {

    private final PendingDecisionType type;
    private final String code;
    private final String title;
    private final String description;
    private final boolean required;

    public PendingDecision(PendingDecisionType type, String code, String title, String description, boolean required) {
        this.type = type;
        this.code = code;
        this.title = title;
        this.description = description;
        this.required = required;
    }

    public PendingDecisionType getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }
}
