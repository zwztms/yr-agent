package com.yragent.domain.memory;

import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryRepository memoryRepository;
    private final PreferenceSerializer preferenceSerializer;
    private final PolicySerializer policySerializer;

    public MemoryService(MemoryRepository memoryRepository,
                         PreferenceSerializer preferenceSerializer,
                         PolicySerializer policySerializer) {
        this.memoryRepository = memoryRepository;
        this.preferenceSerializer = preferenceSerializer;
        this.policySerializer = policySerializer;
    }

    public List<MemoryFragment> loadForStage(StageType stageType, TaskExecutionContext context) {
        List<MemoryFragment> fragments = new ArrayList<>();
        String taskId = context.getTaskId();

        switch (stageType) {
            case GOAL_DEFINITION:
                fragments.addAll(loadZone(MemoryZone.PREFERENCE, 10));
                break;
            case CLARIFY_GOAL:
                fragments.addAll(loadZone(MemoryZone.PREFERENCE, 5));
                break;
            case PLANNING:
                fragments.addAll(loadZone(MemoryZone.EXPERIENCE, 10));
                fragments.addAll(loadZone(MemoryZone.DECISION, 5));
                break;
            case GATE_CONFIRM:
                fragments.addAll(loadZone(MemoryZone.DECISION, 10));
                if (taskId != null) {
                    fragments.addAll(memoryRepository.findByZoneAndTaskId(
                            MemoryZone.DECISION, taskId));
                }
                break;
            case EXECUTION:
                fragments.addAll(loadZone(MemoryZone.ENTITY, 5));
                fragments.addAll(loadZone(MemoryZone.EXPERIENCE, 10));
                if (taskId != null) {
                    fragments.addAll(memoryRepository.findByZoneAndTaskId(
                            MemoryZone.ENTITY, taskId));
                }
                break;
            case VERIFICATION:
                fragments.addAll(loadZone(MemoryZone.PREFERENCE, 5));
                fragments.addAll(loadZone(MemoryZone.EXPERIENCE, 5));
                break;
            case REVIEW:
                fragments.addAll(loadZone(MemoryZone.DECISION, 10));
                fragments.addAll(loadZone(MemoryZone.EXPERIENCE, 10));
                break;
            default:
                break;
        }
        log.debug("loadForStage stage={}: {} fragments", stageType, fragments.size());
        return fragments;
    }

    private List<MemoryFragment> loadZone(MemoryZone zone, int limit) {
        return memoryRepository.findByZone(zone, limit);
    }

    public MemoryFragment save(MemoryFragment fragment) {
        memoryRepository.save(fragment);
        return fragment;
    }

    public List<MemoryFragment> findByTypeAndTaskId(MemoryType type, String taskId) {
        return memoryRepository.findByTypeAndTaskId(type, taskId);
    }

    public List<MemoryFragment> search(String keyword, MemoryType type, int limit) {
        return memoryRepository.searchByKeyword(keyword, type, limit);
    }

    public List<MemoryFragment> searchFts(String query, MemoryZone zone, int limit) {
        return memoryRepository.searchFts(query, zone, limit);
    }

    public List<MemoryFragment> searchFts(String query, int limit) {
        return memoryRepository.searchFts(query, limit);
    }

    public void captureStageOutput(TaskExecutionContext context, StageType stage, StageResult result) {
        String taskId = context.getTaskId();
        if (taskId == null) return;

        try {
            switch (stage) {
                case GOAL_DEFINITION:
                    if (context.getGoalAnalysis() != null) {
                        var g = context.getGoalAnalysis();
                        String content = "taskType=" + g.taskType()
                                + ", goals=" + String.join(";", g.goals())
                                + ", confidence=" + g.confidence();
                        save(MemoryFragment.create(MemoryType.DECISION,
                                "目标分析: " + truncate(context.getUserInput(), 50),
                                content, 0.7, taskId, stage.name(),
                                List.of("目标分析", "auto-captured")));
                    }
                    break;
                case PLANNING:
                    if (context.getPlanDocument() != null) {
                        var p = context.getPlanDocument();
                        String content = "overview=" + truncate(p.getOverview(), 200)
                                + ", complexity=" + p.getEstimatedComplexity()
                                + ", steps=" + p.getSteps().size();
                        save(MemoryFragment.create(MemoryType.DECISION,
                                "计划: " + truncate(context.getUserInput(), 50),
                                content, 0.7, taskId, stage.name(),
                                List.of("计划", "auto-captured")));
                    }
                    break;
                case EXECUTION:
                    if (context.getExecutionResult() != null) {
                        var e = context.getExecutionResult();
                        String content = "completedSteps=" + e.getCompletedSteps()
                                + ", failedSteps=" + e.getFailedSteps()
                                + ", output=" + truncate(e.getOutputSummary(), 200);
                        save(MemoryFragment.create(MemoryType.DECISION,
                                "执行结果: " + truncate(context.getUserInput(), 50),
                                content, 0.7, taskId, stage.name(),
                                List.of("执行", "auto-captured")));
                    }
                    break;
                case VERIFICATION:
                    if (context.getVerificationResult() != null
                            && !context.getVerificationResult().isPassed()) {
                        save(MemoryFragment.create(MemoryType.FAILURE_PATTERN,
                                "验证失败: " + truncate(context.getUserInput(), 50),
                                "issues=" + String.join(";", context.getVerificationResult().getIssues()),
                                0.8, taskId, stage.name(),
                                List.of("验证", "失败", "auto-captured")));
                    }
                    break;
                case REVIEW:
                    if (!context.getStageNotes().isEmpty()) {
                        String summary = String.join("; ", context.getStageNotes());
                        save(MemoryFragment.create(MemoryType.DECISION,
                                "审查完成: " + truncate(context.getUserInput(), 50),
                                truncate(summary, 500),
                                0.6, taskId, stage.name(),
                                List.of("审查", "auto-captured")));
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.warn("captureStageOutput failed for stage={}: {}", stage, e.getMessage());
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    public UserPreference loadPreference() {
        List<MemoryFragment> fragments = memoryRepository.findByType(MemoryType.USER_PREFERENCE, 1);
        if (fragments.isEmpty()) {
            return UserPreference.defaults();
        }
        try {
            return preferenceSerializer.deserialize(fragments.get(0).getContent());
        } catch (Exception e) {
            log.warn("反序列化开发者偏好失败，使用默认值", e);
            return UserPreference.defaults();
        }
    }

    public ProjectPolicy loadPolicy() {
        List<MemoryFragment> fragments = memoryRepository.findByType(MemoryType.PROJECT_POLICY, 1);
        if (fragments.isEmpty()) {
            return ProjectPolicy.defaults();
        }
        try {
            return policySerializer.deserialize(fragments.get(0).getContent());
        } catch (Exception e) {
            log.warn("反序列化项目策略失败，使用默认值", e);
            return ProjectPolicy.defaults();
        }
    }

    public MemoryFragment saveOrUpdatePreference(UserPreference preference) {
        String json = preferenceSerializer.serialize(preference);
        List<MemoryFragment> existing = memoryRepository.findByType(MemoryType.USER_PREFERENCE, 1);
        if (existing.isEmpty()) {
            MemoryFragment fragment = MemoryFragment.create(
                    MemoryType.USER_PREFERENCE, "开发者偏好", json, 0.9,
                    null, null,
                    List.of("偏好", preference.getRiskTolerance()));
            memoryRepository.save(fragment);
            return fragment;
        } else {
            MemoryFragment updated = MemoryFragment.restore(
                    existing.get(0).getId(), MemoryType.USER_PREFERENCE,
                    "开发者偏好", json, 0.9,
                    existing.get(0).getCreatedAt(), java.time.Instant.now(),
                    existing.get(0).getTaskId(), existing.get(0).getStage(),
                    List.of("偏好", preference.getRiskTolerance()),
                    MemoryZone.PREFERENCE);
            memoryRepository.update(updated);
            return updated;
        }
    }

    public MemoryFragment saveOrUpdatePolicy(ProjectPolicy policy) {
        String json = policySerializer.serialize(policy);
        List<MemoryFragment> existing = memoryRepository.findByType(MemoryType.PROJECT_POLICY, 1);
        if (existing.isEmpty()) {
            MemoryFragment fragment = MemoryFragment.create(
                    MemoryType.PROJECT_POLICY, "项目策略", json, 0.9,
                    null, null,
                    List.of("策略", policy.getProjectType()));
            memoryRepository.save(fragment);
            return fragment;
        } else {
            MemoryFragment updated = MemoryFragment.restore(
                    existing.get(0).getId(), MemoryType.PROJECT_POLICY,
                    "项目策略", json, 0.9,
                    existing.get(0).getCreatedAt(), java.time.Instant.now(),
                    existing.get(0).getTaskId(), existing.get(0).getStage(),
                    List.of("策略", policy.getProjectType()),
                    MemoryZone.PREFERENCE);
            memoryRepository.update(updated);
            return updated;
        }
    }
}
