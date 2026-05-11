package com.yragent.domain.skill;

import java.util.List;

public interface SkillLoader {
    List<SkillDefinition> loadAll();
    SkillDefinition load(String skillName);
}
