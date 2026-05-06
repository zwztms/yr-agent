package com.yragent.domain.policy;

import com.yragent.domain.tool.ToolCapability;
import com.yragent.domain.tool.ToolRiskLevel;
import org.springframework.stereotype.Service;

@Service
public class PolicyEngine {

    public PolicyDecision evaluateTool(ToolCapability toolCapability) {
        if (toolCapability.getRiskLevel() == ToolRiskLevel.DANGEROUS) {
            return new PolicyDecision(PolicyDecisionType.REQUIRE_APPROVAL, "Dangerous tool requires approval.");
        }
        if (toolCapability.getRiskLevel() == ToolRiskLevel.MUTATING) {
            return new PolicyDecision(PolicyDecisionType.REQUIRE_APPROVAL, "Mutating tool requires gate confirmation.");
        }
        return new PolicyDecision(PolicyDecisionType.ALLOW, "Read-only tool is allowed.");
    }
}
