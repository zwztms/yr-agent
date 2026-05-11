package com.yragent.domain.workflow;

import java.util.ArrayList;
import java.util.List;

public class WorkflowResult {
    private final String nodeName;
    private final boolean success;
    private final String summary;
    private final List<String> outputs;
    private final String error;

    public WorkflowResult(String nodeName, boolean success, String summary, List<String> outputs, String error) {
        this.nodeName = nodeName;
        this.success = success;
        this.summary = summary;
        this.outputs = outputs != null ? List.copyOf(outputs) : List.of();
        this.error = error;
    }

    public static WorkflowResult ok(String nodeName, String summary, List<String> outputs) {
        return new WorkflowResult(nodeName, true, summary, outputs, null);
    }

    public static WorkflowResult fail(String nodeName, String error) {
        return new WorkflowResult(nodeName, false, null, List.of(), error);
    }

    public String getNodeName() { return nodeName; }
    public boolean isSuccess() { return success; }
    public String getSummary() { return summary; }
    public List<String> getOutputs() { return outputs; }
    public String getError() { return error; }
}
