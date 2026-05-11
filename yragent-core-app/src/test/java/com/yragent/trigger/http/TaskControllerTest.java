package com.yragent.trigger.http;

import com.yragent.service.TaskSessionService;
import com.yragent.domain.gate.GateReviewNote;
import com.yragent.domain.gate.GateStatus;
import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        TaskSessionService taskSessionService() {
            return new StubTaskSessionService();
        }
    }

    @Test
    void shouldSubmitTaskAndReturnPendingDecisions() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskDescription\": \"在workspace中创建README.md\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andExpect(jsonPath("$.currentStage").value("GATE_CONFIRM"))
                .andExpect(jsonPath("$.pendingDecisions").isArray())
                .andExpect(jsonPath("$.pendingDecisions[0].code").value("goal.confirm"))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void shouldSubmitGateInputAndContinuePipeline() throws Exception {
        mockMvc.perform(post("/api/tasks/test-task-001/gate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "understanding": "需要在workspace创建README.md，理解当前处于门禁阶段。",
                                  "risk": "文件写入属于MUTATING操作，已确认路径限定在workspace内。",
                                  "confirmedCodes": ["all"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("REVIEW"))
                .andExpect(jsonPath("$.pendingDecisions").isEmpty())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void shouldReturnTaskStatus() throws Exception {
        mockMvc.perform(get("/api/tasks/test-task-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("test-task-001"));
    }

    @Test
    void shouldReturn404ForUnknownTask() throws Exception {
        mockMvc.perform(get("/api/tasks/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ---- 内部桩（Spring Bean） ----

    static class StubTaskSessionService extends TaskSessionService {

        private final TaskExecutionContext testContext;

        StubTaskSessionService() {
            super(null);
            testContext = new TaskExecutionContext();
            testContext.setTaskId("test-task-001");
            testContext.setUserInput("在workspace中创建README.md");
            testContext.setCurrentStage(StageType.GATE_CONFIRM);
            testContext.replacePendingDecisions(List.of(
                    new PendingDecision(PendingDecisionType.CONFIRMATION,
                            "goal.confirm", "确认目标", "是否确认任务目标？", true),
                    new PendingDecision(PendingDecisionType.CONFIRMATION,
                            "risk.confirm", "确认风险", "是否确认风险授权？", true)
            ));
            testContext.setCurrentStageSummary("GATE_CONFIRM 阶段");
            testContext.setGateReviewNote(new GateReviewNote(
                    GateStatus.BLOCKED, "需要确认", List.of(), List.of()));
        }

        @Override
        public TaskExecutionContext createTask(String userInput) {
            return testContext;
        }

        @Override
        public TaskExecutionContext submitGateInput(String taskId, String understanding,
                                                     String risk, List<String> confirmedCodes) {
            if (taskId.equals("nonexistent")) {
                throw new NoSuchElementException("任务不存在: " + taskId);
            }
            testContext.setCurrentStage(StageType.REVIEW);
            testContext.replacePendingDecisions(List.of());
            return testContext;
        }

        @Override
        public TaskExecutionContext getTask(String taskId) {
            if (taskId.equals("nonexistent")) {
                throw new NoSuchElementException("任务不存在: " + taskId);
            }
            return testContext;
        }
    }
}
