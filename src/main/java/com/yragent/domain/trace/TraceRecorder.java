package com.yragent.domain.trace;

import com.yragent.domain.stage.StageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(TraceRecorder.class);

    public void recordStageStart(String taskId, StageType stageType) {
        log.info("trace stage start, taskId={}, stage={}", taskId, stageType);
    }

    public void recordStageFinish(String taskId, StageType stageType, boolean passed, String summary) {
        log.info("trace stage finish, taskId={}, stage={}, passed={}, summary={}", taskId, stageType, passed, summary);
    }
}
