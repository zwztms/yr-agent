package com.yragent.trigger.cli.command;

import com.yragent.domain.gate.PendingDecision;
import com.yragent.domain.gate.PendingDecisionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunTaskCommandTest {

    private final RunTaskCommand runTaskCommand = new RunTaskCommand(null, null);

    @Test
    void shouldResolveAllIntoAllPendingCodes() {
        List<PendingDecision> pendingDecisions = List.of(
                new PendingDecision(PendingDecisionType.CONFIRMATION, "goal.confirm", "确认任务目标", "desc", true),
                new PendingDecision(PendingDecisionType.CONFIRMATION, "risk.confirm", "确认风险", "desc", true)
        );

        List<String> resolvedCodes = runTaskCommand.resolveConfirmedDecisionCodes("all", pendingDecisions);

        assertEquals(2, resolvedCodes.size());
        assertTrue(resolvedCodes.contains("goal.confirm"));
        assertTrue(resolvedCodes.contains("risk.confirm"));
    }

    @Test
    void shouldRejectUnknownDecisionCode() {
        List<PendingDecision> pendingDecisions = List.of(
                new PendingDecision(PendingDecisionType.CONFIRMATION, "goal.confirm", "确认任务目标", "desc", true)
        );

        List<String> resolvedCodes = runTaskCommand.resolveConfirmedDecisionCodes("unknown.code", pendingDecisions);

        assertTrue(resolvedCodes.isEmpty());
    }
}
