package com.yragent.domain.workflow;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorkflowContext {
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private final List<String> executionLog = new ArrayList<>();
    private boolean stopped = false;
    private String stopReason;

    public void set(String key, Object value) { state.put(key, value); }
    public Object get(String key) { return state.get(key); }
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) { return (T) state.get(key); }
    public boolean has(String key) { return state.containsKey(key); }

    public void log(String message) { executionLog.add(message); }
    public List<String> getExecutionLog() { return List.copyOf(executionLog); }

    public void stop(String reason) { this.stopped = true; this.stopReason = reason; }
    public boolean isStopped() { return stopped; }
    public String getStopReason() { return stopReason; }
}
