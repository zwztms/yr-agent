package com.yragent.web;

import com.yragent.app.service.TaskSessionService;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.web.dto.GateInputRequest;
import com.yragent.web.dto.TaskStatusResponse;
import com.yragent.web.dto.TaskSubmitRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskSessionService taskSessionService;

    public TaskController(TaskSessionService taskSessionService) {
        this.taskSessionService = taskSessionService;
    }

    @PostMapping
    public ResponseEntity<TaskStatusResponse> submitTask(@RequestBody TaskSubmitRequest request) {
        TaskExecutionContext context = taskSessionService.createTask(request.taskDescription());
        return ResponseEntity.ok(TaskStatusResponse.from(context));
    }

    @GetMapping
    public ResponseEntity<List<TaskStatusResponse>> listTasks() {
        List<TaskStatusResponse> tasks = taskSessionService.listTasks().stream()
                .map(TaskStatusResponse::from)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/{taskId}/gate")
    public ResponseEntity<TaskStatusResponse> submitGateInput(
            @PathVariable String taskId,
            @RequestBody GateInputRequest request) {
        TaskExecutionContext context = taskSessionService.submitGateInput(
                taskId,
                request.understanding(),
                request.risk(),
                request.confirmedCodes() != null ? request.confirmedCodes() : List.of());
        return ResponseEntity.ok(TaskStatusResponse.from(context));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        TaskExecutionContext context = taskSessionService.getTask(taskId);
        return ResponseEntity.ok(TaskStatusResponse.from(context));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(e.getMessage());
    }
}
