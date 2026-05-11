package com.yragent.domain.memory;

import java.time.Instant;
import java.util.List;

// 开发者偏好：跨任务保留的开发者个人设置。不可变对象。
public class UserPreference {

    private final String riskTolerance;
    private final String confirmationMode;
    private final String displayLanguage;
    private final String displayDetail;
    private final int maxToolsPerTask;
    private final String savedAt;

    private UserPreference(String riskTolerance, String confirmationMode,
                           String displayLanguage, String displayDetail,
                           int maxToolsPerTask, String savedAt) {
        this.riskTolerance = riskTolerance;
        this.confirmationMode = confirmationMode;
        this.displayLanguage = displayLanguage;
        this.displayDetail = displayDetail;
        this.maxToolsPerTask = maxToolsPerTask;
        this.savedAt = savedAt;
    }

    public static UserPreference defaults() {
        return new UserPreference(
                "balanced",
                "explicit",
                "zh",
                "detailed",
                5,
                Instant.now().toString()
        );
    }

    public static UserPreference of(String riskTolerance, String confirmationMode,
                                    String displayLanguage, String displayDetail,
                                    int maxToolsPerTask, String savedAt) {
        return new UserPreference(riskTolerance, confirmationMode,
                displayLanguage, displayDetail, maxToolsPerTask, savedAt);
    }

    public String getRiskTolerance() {
        return riskTolerance;
    }

    public String getConfirmationMode() {
        return confirmationMode;
    }

    public String getDisplayLanguage() {
        return displayLanguage;
    }

    public String getDisplayDetail() {
        return displayDetail;
    }

    public int getMaxToolsPerTask() {
        return maxToolsPerTask;
    }

    public String getSavedAt() {
        return savedAt;
    }
}
