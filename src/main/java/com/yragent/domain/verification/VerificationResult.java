package com.yragent.domain.verification;

import java.util.List;

// 验证结果：LLM 对执行输出的评估。不可变对象。
public class VerificationResult {

    private final boolean passed;
    private final List<String> issues;
    private final String summary;
    private final boolean allChecksPassed;

    public VerificationResult(boolean passed, List<String> issues, String summary, boolean allChecksPassed) {
        this.passed = passed;
        this.issues = issues != null ? List.copyOf(issues) : List.of();
        this.summary = summary != null ? summary : "";
        this.allChecksPassed = allChecksPassed;
    }

    public boolean isPassed() {
        return passed;
    }

    public List<String> getIssues() {
        return issues;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isAllChecksPassed() {
        return allChecksPassed;
    }
}
