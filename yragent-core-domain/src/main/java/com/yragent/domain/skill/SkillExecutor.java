package com.yragent.domain.skill;

public interface SkillExecutor {
    SkillResult execute(SkillDefinition skill, SkillContext context);

    // Simple result record
    record SkillResult(boolean success, String output, String error) {
        public static SkillResult ok(String output) {
            return new SkillResult(true, output, null);
        }
        public static SkillResult fail(String error) {
            return new SkillResult(false, null, error);
        }
    }
}
