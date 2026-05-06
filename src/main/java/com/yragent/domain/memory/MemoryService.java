package com.yragent.domain.memory;

import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import com.yragent.repository.MemoryRepository;
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

    // 按阶段加载记忆，返回此阶段需要的记忆列表。
    // 返回空列表表示该阶段尚无可用的记忆。
    public List<MemoryFragment> loadForStage(StageType stageType, TaskExecutionContext context) {
        List<MemoryFragment> fragments = new ArrayList<>();
        switch (stageType) {
            case GOAL_DEFINITION:
                fragments.addAll(memoryRepository.findByType(MemoryType.USER_PREFERENCE, 10));
                fragments.addAll(memoryRepository.findByType(MemoryType.PROJECT_POLICY, 10));
                break;
            case PLANNING:
                fragments.addAll(memoryRepository.findByType(MemoryType.FAILURE_PATTERN, 10));
                break;
            case GATE_CONFIRM:
                fragments.addAll(memoryRepository.findByType(MemoryType.DECISION, 10));
                if (context.getTaskId() != null) {
                    fragments.addAll(memoryRepository.findByTypeAndTaskId(
                            MemoryType.GATE_ATTEMPT, context.getTaskId()));
                }
                break;
            case EXECUTION:
                if (context.getTaskId() != null) {
                    fragments.addAll(memoryRepository.findByTypeAndTaskId(
                            MemoryType.TASK_STATE, context.getTaskId()));
                }
                break;
            case VERIFICATION:
                fragments.addAll(memoryRepository.findByType(MemoryType.PROJECT_POLICY, 10));
                break;
            case REVIEW:
                fragments.addAll(memoryRepository.findByType(MemoryType.DECISION, 10));
                break;
            default:
                break;
        }
        return fragments;
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

    // 加载最新一份开发者偏好，无记录时返回默认值。
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

    // 加载最新一份项目策略，无记录时返回默认值。
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

    // 保存或更新开发者偏好，始终只保留一条 USER_PREFERENCE 记录。
    public MemoryFragment saveOrUpdatePreference(UserPreference preference) {
        String json = preferenceSerializer.serialize(preference);
        List<MemoryFragment> existing = memoryRepository.findByType(MemoryType.USER_PREFERENCE, 1);
        if (existing.isEmpty()) {
            MemoryFragment fragment = MemoryFragment.create(
                    MemoryType.USER_PREFERENCE,
                    "开发者偏好",
                    json,
                    0.9,
                    null,
                    null,
                    List.of("偏好", preference.getRiskTolerance())
            );
            memoryRepository.save(fragment);
            return fragment;
        } else {
            MemoryFragment updated = MemoryFragment.restore(
                    existing.get(0).getId(),
                    MemoryType.USER_PREFERENCE,
                    "开发者偏好",
                    json,
                    0.9,
                    existing.get(0).getCreatedAt(),
                    java.time.Instant.now(),
                    existing.get(0).getTaskId(),
                    existing.get(0).getStage(),
                    List.of("偏好", preference.getRiskTolerance())
            );
            memoryRepository.update(updated);
            return updated;
        }
    }

    // 保存或更新项目策略，始终只保留一条 PROJECT_POLICY 记录。
    public MemoryFragment saveOrUpdatePolicy(ProjectPolicy policy) {
        String json = policySerializer.serialize(policy);
        List<MemoryFragment> existing = memoryRepository.findByType(MemoryType.PROJECT_POLICY, 1);
        if (existing.isEmpty()) {
            MemoryFragment fragment = MemoryFragment.create(
                    MemoryType.PROJECT_POLICY,
                    "项目策略",
                    json,
                    0.9,
                    null,
                    null,
                    List.of("策略", policy.getProjectType())
            );
            memoryRepository.save(fragment);
            return fragment;
        } else {
            MemoryFragment updated = MemoryFragment.restore(
                    existing.get(0).getId(),
                    MemoryType.PROJECT_POLICY,
                    "项目策略",
                    json,
                    0.9,
                    existing.get(0).getCreatedAt(),
                    java.time.Instant.now(),
                    existing.get(0).getTaskId(),
                    existing.get(0).getStage(),
                    List.of("策略", policy.getProjectType())
            );
            memoryRepository.update(updated);
            return updated;
        }
    }
}
