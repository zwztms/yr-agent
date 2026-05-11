package com.yragent.workflow;

import com.yragent.domain.workflow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private final Map<String, WorkflowNode> registry = new ConcurrentHashMap<>();

    public void register(String name, WorkflowNode node) {
        registry.put(name, node);
        log.info("Registered workflow: {}", name);
    }

    public WorkflowResult execute(String workflowName, WorkflowContext context) {
        WorkflowNode node = registry.get(workflowName);
        if (node == null) {
            return WorkflowResult.fail(workflowName, "工作流未注册: " + workflowName);
        }
        log.info("Starting workflow: {}", workflowName);
        WorkflowResult result = node.execute(context);
        log.info("Workflow {} completed: {}", workflowName, result.isSuccess() ? "OK" : "FAILED");
        return result;
    }

    public WorkflowNode buildSequential(String name, List<WorkflowNode> children) {
        return new SequentialWorkflow(name, children);
    }

    public WorkflowNode buildParallel(String name, List<WorkflowNode> children) {
        return new ParallelWorkflow(name, children);
    }

    public WorkflowNode buildLoop(String name, List<WorkflowNode> children, int maxIterations) {
        return new LoopWorkflow(name, children, maxIterations);
    }
}
