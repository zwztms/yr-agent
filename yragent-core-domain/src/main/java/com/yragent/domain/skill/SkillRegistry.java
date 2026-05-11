package com.yragent.domain.skill;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SkillRegistry {
    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();

    public void register(SkillDefinition skill) {
        skills.put(skill.getName(), skill);
    }

    public Optional<SkillDefinition> get(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public List<SkillDefinition> listAll() {
        return List.copyOf(skills.values());
    }

    public List<SkillDefinition> findByRiskLevel(String riskLevel) {
        return skills.values().stream()
                .filter(s -> s.getRiskLevel().equalsIgnoreCase(riskLevel))
                .toList();
    }
}
