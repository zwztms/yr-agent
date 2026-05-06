package com.yragent.app.service;

import com.yragent.domain.stage.TaskExecutionContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskSessionService {

    private final ConcurrentHashMap<String, TaskExecutionContext> sessions = new ConcurrentHashMap<>();

    private final TaskApplicationService taskApplicationService;

    public TaskSessionService(TaskApplicationService taskApplicationService) {
        this.taskApplicationService = taskApplicationService;
    }

    public TaskExecutionContext createTask(String userInput) {
        TaskExecutionContext context = taskApplicationService.startTask(userInput);
        sessions.put(context.getTaskId(), context);
        return context;
    }

    public TaskExecutionContext submitGateInput(String taskId,
                                                 String understanding,
                                                 String risk,
                                                 List<String> confirmedCodes) {
        TaskExecutionContext context = sessions.get(taskId);
        if (context == null) {
            throw new NoSuchElementException("任务不存在: " + taskId);
        }
        TaskExecutionContext updated = taskApplicationService.submitGateInputAndContinue(
                context, understanding, risk, confirmedCodes);
        sessions.put(taskId, updated);
        return updated;
    }

    public TaskExecutionContext getTask(String taskId) {
        TaskExecutionContext context = sessions.get(taskId);
        if (context == null) {
            throw new NoSuchElementException("任务不存在: " + taskId);
        }
        return context;
    }

    public List<TaskExecutionContext> listTasks() {
        return List.copyOf(sessions.values());
    }
}
