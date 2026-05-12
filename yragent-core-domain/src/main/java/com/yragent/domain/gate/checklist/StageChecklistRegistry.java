package com.yragent.domain.gate.checklist;

import com.yragent.domain.stage.StageType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StageChecklistRegistry {

    private static final Map<StageType, List<GateCheckItem>> registry = new ConcurrentHashMap<>();

    static {
        registry.put(StageType.GOAL_DEFINITION, List.of(
                new GateCheckItem("GD-1", "目标理解",
                        "你是否清楚本次任务最终要产出什么？",
                        List.of("目标", "产出", "创建", "任务")),
                new GateCheckItem("GD-2", "约束遵守",
                        "你是否了解当前项目的目录限制和运行环境限制？",
                        List.of("路径", "限制", "约束", "沙盒")),
                new GateCheckItem("GD-3", "风险识别",
                        "你认为本次任务最大的风险因素是什么？",
                        List.of("风险", "失败", "覆盖", "回滚"))
        ));

        registry.put(StageType.CLARIFY_GOAL, List.of(
                new GateCheckItem("CG-1", "回答具体",
                        "你对AI提问的回答是否足够具体？（不含'应该''大概'等模糊词）",
                        List.of("具体", "明确", "确定")),
                new GateCheckItem("CG-2", "目标修正",
                        "澄清后的目标分析与澄清前相比有实质变化吗？",
                        List.of("修改", "调整", "澄清", "更新", "变化"))
        ));

        registry.put(StageType.PLANNING, List.of(
                new GateCheckItem("PL-1", "方案认知",
                        "你是否清楚计划中每一步要做什么，步骤之间有什么依赖关系？",
                        List.of("计划", "步骤", "架构", "依赖", "顺序")),
                new GateCheckItem("PL-2", "工具边界",
                        "你是否知道每一步要使用哪个工具？有没有需要额外权限的操作？",
                        List.of("工具", "write_file", "read_file", "run_command", "权限")),
                new GateCheckItem("PL-3", "风险识别",
                        "你是否能预见到至少一个可能的失败场景和对应的兜底方案？",
                        List.of("风险", "失败", "兜底", "回滚", "备份"))
        ));

        registry.put(StageType.GATE_CONFIRM, List.of(
                new GateCheckItem("GC-1", "门禁理解",
                        "你是否理解门禁机制的作用不是限制，而是确认认知对齐？",
                        List.of("门禁", "确认", "理解", "对齐")),
                new GateCheckItem("GC-2", "授权边界",
                        "你是否清楚本次确认后，AI 将获得哪些操作的执行权？",
                        List.of("授权", "执行", "操作", "工具", "权限"))
        ));

        registry.put(StageType.EXECUTION, List.of(
                new GateCheckItem("EX-1", "高风险拦截",
                        "执行计划中是否包含需要额外确认的危险操作？",
                        List.of("删除", "格式化", "install", "危险", "覆盖", "rm"))
        ));

        registry.put(StageType.VERIFICATION, List.of(
                new GateCheckItem("VF-1", "验证理解",
                        "你是否看了验证结果，对其中标记的失败项有自己的判断？",
                        List.of("验证", "失败", "通过", "结果", "检查"))
        ));

        registry.put(StageType.REVIEW, List.of(
                new GateCheckItem("RV-1", "完成判断",
                        "REVIEW 阶段判断的 projectComplete 状态你同意吗？",
                        List.of("完成", "审查", "总结", "遗留")),
                new GateCheckItem("RV-2", "遗留问题",
                        "如果有未完成的工作，你是否清楚下一轮的重点？",
                        List.of("下一轮", "继续", "待办", "剩余"))
        ));
    }

    public static List<GateCheckItem> forStage(StageType stage) {
        if (stage == null) return List.of();
        return registry.getOrDefault(stage, List.of());
    }
}
