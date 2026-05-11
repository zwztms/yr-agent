package com.yragent.domain.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class LoopWorkflow implements WorkflowNode {
    private static final Logger log = LoggerFactory.getLogger(LoopWorkflow.class);
    private final String name;
    private final List<WorkflowNode> children;
    private final int maxIterations;

    public LoopWorkflow(String name, List<WorkflowNode> children, int maxIterations) {
        this.name = name;
        this.children = children != null ? List.copyOf(children) : List.of();
        this.maxIterations = Math.max(1, maxIterations);
    }

    @Override
    public String getName() { return name; }

    @Override
    public WorkflowResult execute(WorkflowContext context) {
        List<String> allOutputs = new ArrayList<>();
        int iteration = 0;

        while (iteration < maxIterations) {
            if (context.isStopped()) {
                return new WorkflowResult(name, true,
                        "循环在第 " + (iteration + 1) + " 轮被停止",
                        allOutputs, null);
            }
            iteration++;
            log.info("[{}] 第 {}/{} 轮", name, iteration, maxIterations);
            context.set(name + ".currentIteration", iteration);

            boolean allPassed = true;
            for (WorkflowNode child : children) {
                WorkflowResult result = child.execute(context);
                context.log(name + "[" + iteration + "] → " + child.getName() + ": "
                        + (result.isSuccess() ? "OK" : "FAIL"));
                if (result.getOutputs() != null) allOutputs.addAll(result.getOutputs());
                if (!result.isSuccess()) {
                    allPassed = false;
                    break;
                }
            }

            // Check if loop should terminate
            Object complete = context.get(name + ".projectComplete");
            if (complete instanceof Boolean b && b) {
                return WorkflowResult.ok(name,
                        "循环完成于第 " + iteration + " 轮", allOutputs);
            }
            if (allPassed && iteration >= maxIterations) {
                return WorkflowResult.ok(name,
                        "达到最大轮次 " + maxIterations + "，停止循环", allOutputs);
            }
        }
        return WorkflowResult.ok(name,
                "循环结束 (" + iteration + " 轮)", allOutputs);
    }

    public int getMaxIterations() { return maxIterations; }
    public List<WorkflowNode> getChildren() { return children; }
}
