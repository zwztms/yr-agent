package com.yragent.domain.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ParallelWorkflow implements WorkflowNode {
    private static final Logger log = LoggerFactory.getLogger(ParallelWorkflow.class);
    private final String name;
    private final List<WorkflowNode> children;
    private final ExecutorService executor;

    public ParallelWorkflow(String name, List<WorkflowNode> children) {
        this(name, children, Executors.newCachedThreadPool());
    }

    public ParallelWorkflow(String name, List<WorkflowNode> children, ExecutorService executor) {
        this.name = name;
        this.children = children != null ? List.copyOf(children) : List.of();
        this.executor = executor;
    }

    @Override
    public String getName() { return name; }

    @Override
    public WorkflowResult execute(WorkflowContext context) {
        if (context.isStopped()) {
            return WorkflowResult.fail(name, "工作流被停止: " + context.getStopReason());
        }
        List<CompletableFuture<WorkflowResult>> futures = children.stream()
                .map(child -> CompletableFuture.supplyAsync(() -> {
                    log.info("[{}] 并行执行: {}", name, child.getName());
                    return child.execute(context);
                }, executor))
                .toList();

        List<WorkflowResult> results;
        try {
            results = futures.stream()
                    .map(f -> {
                        try { return f.get(60, TimeUnit.SECONDS); }
                        catch (Exception e) { return WorkflowResult.fail("unknown", e.getMessage()); }
                    })
                    .toList();
        } catch (Exception e) {
            return WorkflowResult.fail(name, "并行执行异常: " + e.getMessage());
        }

        List<String> allOutputs = results.stream()
                .flatMap(r -> r.getOutputs().stream())
                .collect(Collectors.toList());
        boolean allSuccess = results.stream().allMatch(WorkflowResult::isSuccess);
        long failedCount = results.stream().filter(r -> !r.isSuccess()).count();

        return new WorkflowResult(name, allSuccess,
                String.format("并行执行完成 (%d/%d 成功)", children.size() - failedCount, children.size()),
                allOutputs,
                allSuccess ? null : failedCount + " 个子任务失败");
    }

    public List<WorkflowNode> getChildren() { return children; }
}
