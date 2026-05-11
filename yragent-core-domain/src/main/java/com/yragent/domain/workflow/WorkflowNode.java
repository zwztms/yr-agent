package com.yragent.domain.workflow;

@FunctionalInterface
public interface WorkflowNode {
    WorkflowResult execute(WorkflowContext context);

    default String getName() {
        return getClass().getSimpleName();
    }
}
