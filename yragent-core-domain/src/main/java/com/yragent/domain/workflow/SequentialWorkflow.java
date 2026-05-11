package com.yragent.domain.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SequentialWorkflow implements WorkflowNode {
    private static final Logger log = LoggerFactory.getLogger(SequentialWorkflow.class);
    private final String name;
    private final List<WorkflowNode> children;

    public SequentialWorkflow(String name, List<WorkflowNode> children) {
        this.name = name;
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    @Override
    public String getName() { return name; }

    @Override
    public WorkflowResult execute(WorkflowContext context) {
        List<String> outputs = new ArrayList<>();
        for (WorkflowNode child : children) {
            if (context.isStopped()) {
                return WorkflowResult.fail(name, "工作流被停止: " + context.getStopReason());
            }
            log.info("[{}] 执行子节点: {}", name, child.getName());
            WorkflowResult result = child.execute(context);
            context.log(name + " → " + child.getName() + ": " + (result.isSuccess() ? "OK" : "FAIL"));
            if (result.getOutputs() != null) outputs.addAll(result.getOutputs());
            if (!result.isSuccess()) {
                return WorkflowResult.fail(name, "子节点 [" + child.getName() + "] 失败: " + result.getError());
            }
        }
        return WorkflowResult.ok(name, "串行执行完成 (" + children.size() + " 个子节点)", outputs);
    }

    public List<WorkflowNode> getChildren() { return children; }
}
