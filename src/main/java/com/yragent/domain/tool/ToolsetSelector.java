package com.yragent.domain.tool;

import com.yragent.domain.policy.PolicyDecision;
import com.yragent.domain.policy.PolicyDecisionType;
import com.yragent.domain.policy.PolicyEngine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ToolsetSelector {

    private final ToolRegistry toolRegistry;
    private final PolicyEngine policyEngine;

    public ToolsetSelector(ToolRegistry toolRegistry, PolicyEngine policyEngine) {
        this.toolRegistry = toolRegistry;
        this.policyEngine = policyEngine;
    }

    public ToolSelectionDecision selectForPlanning() {
        List<ToolCapability> allowed = toolRegistry.listAll().stream()
                .filter(tool -> {
                    PolicyDecision decision = policyEngine.evaluateTool(tool);
                    return decision.getType() == PolicyDecisionType.ALLOW
                            || decision.getType() == PolicyDecisionType.REQUIRE_APPROVAL;
                })
                .toList();
        return new ToolSelectionDecision(allowed, "Planning toolset selected.");
    }
}
