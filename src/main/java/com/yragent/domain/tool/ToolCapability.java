package com.yragent.domain.tool;

public class ToolCapability {

    private final String name;
    private final String description;
    private final ToolRiskLevel riskLevel;

    public ToolCapability(String name, String description, ToolRiskLevel riskLevel) {
        this.name = name;
        this.description = description;
        this.riskLevel = riskLevel;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ToolRiskLevel getRiskLevel() {
        return riskLevel;
    }
}
