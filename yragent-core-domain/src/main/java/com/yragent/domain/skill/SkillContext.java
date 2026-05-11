package com.yragent.domain.skill;

import java.util.*;

public class SkillContext {
    private final Map<String, Object> variables = new HashMap<>();
    private String workspaceRoot;

    public void setVariable(String key, Object value) { variables.put(key, value); }
    public Object getVariable(String key) { return variables.get(key); }
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> type) { return (T) variables.get(key); }
    public String getWorkspaceRoot() { return workspaceRoot; }
    public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
    public Map<String, Object> getVariables() { return Map.copyOf(variables); }
}
