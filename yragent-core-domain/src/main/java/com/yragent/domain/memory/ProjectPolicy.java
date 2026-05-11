package com.yragent.domain.memory;

import java.time.Instant;
import java.util.List;

// 项目策略：跨任务保留的项目级规则。不可变对象。
public class ProjectPolicy {

    private final String projectType;
    private final List<String> directoryExclusions;
    private final String buildCommand;
    private final String testCommand;
    private final List<String> codeStyleNotes;
    private final boolean allowNetworkAccess;
    private final String savedAt;

    private ProjectPolicy(String projectType, List<String> directoryExclusions,
                          String buildCommand, String testCommand,
                          List<String> codeStyleNotes, boolean allowNetworkAccess,
                          String savedAt) {
        this.projectType = projectType;
        this.directoryExclusions = directoryExclusions;
        this.buildCommand = buildCommand;
        this.testCommand = testCommand;
        this.codeStyleNotes = codeStyleNotes;
        this.allowNetworkAccess = allowNetworkAccess;
        this.savedAt = savedAt;
    }

    public static ProjectPolicy defaults() {
        return new ProjectPolicy(
                "generic",
                List.of(),
                null,
                null,
                List.of(),
                false,
                Instant.now().toString()
        );
    }

    public static ProjectPolicy of(String projectType, List<String> directoryExclusions,
                                   String buildCommand, String testCommand,
                                   List<String> codeStyleNotes, boolean allowNetworkAccess,
                                   String savedAt) {
        return new ProjectPolicy(projectType, directoryExclusions,
                buildCommand, testCommand, codeStyleNotes,
                allowNetworkAccess, savedAt);
    }

    public String getProjectType() {
        return projectType;
    }

    public List<String> getDirectoryExclusions() {
        return directoryExclusions;
    }

    public String getBuildCommand() {
        return buildCommand;
    }

    public String getTestCommand() {
        return testCommand;
    }

    public List<String> getCodeStyleNotes() {
        return codeStyleNotes;
    }

    public boolean isAllowNetworkAccess() {
        return allowNetworkAccess;
    }

    public String getSavedAt() {
        return savedAt;
    }
}
