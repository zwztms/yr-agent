package com.yragent.domain.tool;

import java.util.List;

public class ToolSelectionDecision {

    private final List<ToolCapability> allowedTools;
    private final String summary;

    public ToolSelectionDecision(List<ToolCapability> allowedTools, String summary) {
        this.allowedTools = allowedTools;
        this.summary = summary;
    }

    public List<ToolCapability> getAllowedTools() {
        return allowedTools;
    }

    public String getSummary() {
        return summary;
    }
}
