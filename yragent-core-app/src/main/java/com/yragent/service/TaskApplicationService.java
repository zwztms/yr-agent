package com.yragent.service;

import com.yragent.orchestrator.StageOrchestrator;
import com.yragent.domain.gate.DeveloperUnderstanding;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskApplicationService {

    private final StageOrchestrator stageOrchestrator;

    public TaskApplicationService(StageOrchestrator stageOrchestrator) {
        this.stageOrchestrator = stageOrchestrator;
    }

    public TaskExecutionContext startTask(String userInput) {
        return stageOrchestrator.start(userInput);
    }

    public TaskExecutionContext submitGateInputAndContinue(TaskExecutionContext context,
                                                           String understandingSummary,
                                                           String riskSummary,
                                                           List<String> confirmedDecisionCodes) {
        if ((understandingSummary != null && !understandingSummary.isBlank())
                || (riskSummary != null && !riskSummary.isBlank())) {
            context.setDeveloperUnderstanding(new DeveloperUnderstanding(understandingSummary, riskSummary));
        }
        for (String confirmedDecisionCode : confirmedDecisionCodes) {
            context.confirmDecision(confirmedDecisionCode);
        }
        // 重新从门禁阶段执行，门禁通过后会继续进入后续阶段。
        return stageOrchestrator.resumeFrom(context, StageType.GATE_CONFIRM);
    }
}
