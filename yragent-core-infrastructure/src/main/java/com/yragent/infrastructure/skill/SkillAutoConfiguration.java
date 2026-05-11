package com.yragent.infrastructure.skill;

import com.yragent.domain.skill.SkillDefinition;
import com.yragent.domain.skill.SkillLoader;
import com.yragent.domain.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SkillAutoConfiguration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SkillAutoConfiguration.class);
    private final SkillLoader skillLoader;
    private final SkillRegistry skillRegistry;

    public SkillAutoConfiguration(SkillLoader skillLoader, SkillRegistry skillRegistry) {
        this.skillLoader = skillLoader;
        this.skillRegistry = skillRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SkillDefinition> skills = skillLoader.loadAll();
        for (SkillDefinition skill : skills) {
            skillRegistry.register(skill);
        }
        log.info("Registered {} skills: {}", skills.size(),
                skills.stream().map(SkillDefinition::getName).toList());
    }
}
