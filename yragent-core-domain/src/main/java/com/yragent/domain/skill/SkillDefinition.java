package com.yragent.domain.skill;

import java.util.List;

public class SkillDefinition {
    private final String name;
    private final String description;
    private final String riskLevel;          // READ_ONLY | MUTATING | DANGEROUS
    private final List<String> requiredTools;
    private final String instruction;        // Markdown body content from SKILL.md

    public SkillDefinition(String name, String description, String riskLevel,
                           List<String> requiredTools, String instruction) {
        this.name = name;
        this.description = description;
        this.riskLevel = riskLevel != null ? riskLevel : "MUTATING";
        this.requiredTools = requiredTools != null ? List.copyOf(requiredTools) : List.of();
        this.instruction = instruction;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getRiskLevel() { return riskLevel; }
    public List<String> getRequiredTools() { return requiredTools; }
    public String getInstruction() { return instruction; }
}
