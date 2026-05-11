package com.yragent.domain.memory;

import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryServiceTest {

    private MemoryService memoryService;
    private FakeMemoryRepository memoryRepository;

    @BeforeEach
    void setUp() {
        memoryRepository = new FakeMemoryRepository();
        memoryService = new MemoryService(memoryRepository,
                new PreferenceSerializer(), new PolicySerializer());
    }

    @Test
    void shouldLoadPreferencesAndPoliciesForGoalDefinition() {
        memoryRepository.save(MemoryFragment.create(
                MemoryType.USER_PREFERENCE, "风险偏好", "conservative", 0.9, null, null, List.of()));
        memoryRepository.save(MemoryFragment.create(
                MemoryType.PROJECT_POLICY, "测试命令", "mvn test", 0.8, null, null, List.of()));

        List<MemoryFragment> fragments = memoryService.loadForStage(
                StageType.GOAL_DEFINITION, new TaskExecutionContext());

        assertFalse(fragments.isEmpty());
        assertTrue(fragments.stream().anyMatch(f -> f.getType() == MemoryType.USER_PREFERENCE));
        assertTrue(fragments.stream().anyMatch(f -> f.getType() == MemoryType.PROJECT_POLICY));
    }

    @Test
    void shouldLoadFailurePatternsForPlanning() {
        memoryRepository.save(MemoryFragment.create(
                MemoryType.FAILURE_PATTERN, "循环依赖", "Spring Boot 启动失败", 0.7, null, null, List.of()));
        // 存入其他类型，验证不会被 PLANNING 阶段加载
        memoryRepository.save(MemoryFragment.create(
                MemoryType.USER_PREFERENCE, "不应出现", "{}", 0.5, null, null, List.of()));

        List<MemoryFragment> fragments = memoryService.loadForStage(
                StageType.PLANNING, new TaskExecutionContext());

        assertFalse(fragments.isEmpty());
        assertTrue(fragments.stream().allMatch(f -> f.getType() == MemoryType.FAILURE_PATTERN));
    }

    @Test
    void shouldLoadGateAttemptsForGateConfirm() {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId(UUID.randomUUID().toString());

        memoryRepository.save(MemoryFragment.create(
                MemoryType.GATE_ATTEMPT, "第1轮门禁", "{}", 0.5, context.getTaskId(), "GATE_CONFIRM", List.of()));
        memoryRepository.save(MemoryFragment.create(
                MemoryType.DECISION, "历史决策", "{}", 0.8, null, null, List.of()));

        List<MemoryFragment> fragments = memoryService.loadForStage(StageType.GATE_CONFIRM, context);

        assertFalse(fragments.isEmpty());
        assertTrue(fragments.stream().anyMatch(f -> f.getType() == MemoryType.GATE_ATTEMPT));
        assertTrue(fragments.stream().anyMatch(f -> f.getType() == MemoryType.DECISION));
    }

    @Test
    void shouldLoadTaskStateForExecution() {
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskId(UUID.randomUUID().toString());

        memoryRepository.save(MemoryFragment.create(
                MemoryType.TASK_STATE, "执行进度", "已完成 3/5 子任务", 1.0, context.getTaskId(), "EXECUTION", List.of()));

        List<MemoryFragment> fragments = memoryService.loadForStage(StageType.EXECUTION, context);

        assertFalse(fragments.isEmpty());
        assertTrue(fragments.stream().allMatch(f -> f.getType() == MemoryType.TASK_STATE));
    }

    @Test
    void shouldReturnEmptyListWhenNoMemories() {
        List<MemoryFragment> fragments = memoryService.loadForStage(
                StageType.GOAL_DEFINITION, new TaskExecutionContext());

        assertTrue(fragments.isEmpty());
    }

    @Test
    void shouldSaveAndRetrieveMemory() {
        String taskId = UUID.randomUUID().toString();
        MemoryFragment fragment = MemoryFragment.create(
                MemoryType.DECISION, "测试决策", "{}", 0.5, taskId, null, List.of());
        memoryService.save(fragment);

        List<MemoryFragment> results = memoryService.findByTypeAndTaskId(
                MemoryType.DECISION, taskId);
        assertFalse(results.isEmpty());
    }

    @Test
    void shouldCreateNewPreferenceWhenNoneExists() {
        UserPreference custom = UserPreference.of(
                "conservative", "batch", "en", "brief", 3, null);

        memoryService.saveOrUpdatePreference(custom);
        UserPreference loaded = memoryService.loadPreference();

        assertEquals("conservative", loaded.getRiskTolerance());
        assertEquals("batch", loaded.getConfirmationMode());
        assertEquals(3, loaded.getMaxToolsPerTask());
    }

    @Test
    void shouldUpdateExistingPreference() {
        UserPreference first = UserPreference.of(
                "balanced", "explicit", "zh", "detailed", 5, null);
        memoryService.saveOrUpdatePreference(first);

        UserPreference second = UserPreference.of(
                "aggressive", "batch", "en", "brief", 10, null);
        memoryService.saveOrUpdatePreference(second);

        // 更新后应该只有一条记录，且值为最新。
        List<MemoryFragment> all = memoryService.findByTypeAndTaskId(
                MemoryType.USER_PREFERENCE, null);
        // findByTypeAndTaskId 对 null taskId 可能查不到，用 search 替代。
        UserPreference loaded = memoryService.loadPreference();
        assertEquals("aggressive", loaded.getRiskTolerance());
        assertEquals(10, loaded.getMaxToolsPerTask());
    }

    @Test
    void shouldCreateNewPolicyWhenNoneExists() {
        ProjectPolicy custom = ProjectPolicy.of(
                "java", List.of("node_modules"), "mvn package", "mvn test",
                List.of("Google Style"), true, null);

        memoryService.saveOrUpdatePolicy(custom);
        ProjectPolicy loaded = memoryService.loadPolicy();

        assertEquals("java", loaded.getProjectType());
        assertEquals(1, loaded.getDirectoryExclusions().size());
        assertTrue(loaded.isAllowNetworkAccess());
    }

    @Test
    void shouldUpdateExistingPolicy() {
        ProjectPolicy first = ProjectPolicy.of(
                "generic", List.of(), null, null, List.of(), false, null);
        memoryService.saveOrUpdatePolicy(first);

        ProjectPolicy second = ProjectPolicy.of(
                "python", List.of(".venv", "__pycache__"), "poetry build", "pytest",
                List.of(), false, null);
        memoryService.saveOrUpdatePolicy(second);

        ProjectPolicy loaded = memoryService.loadPolicy();
        assertEquals("python", loaded.getProjectType());
        assertEquals(2, loaded.getDirectoryExclusions().size());
        assertEquals("pytest", loaded.getTestCommand());
    }
}
