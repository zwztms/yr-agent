package com.yragent.infrastructure.skill;

import com.yragent.domain.skill.SkillContext;
import com.yragent.domain.skill.SkillDefinition;
import org.springframework.stereotype.Component;

@Component
public class SkillPromptRenderer {

    public String render(SkillDefinition skill, SkillContext context) {
        String instruction = skill.getInstruction();
        // Replace {variableName} placeholders with context values
        for (var entry : context.getVariables().entrySet()) {
            instruction = instruction.replace("{" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return instruction;
    }
}
