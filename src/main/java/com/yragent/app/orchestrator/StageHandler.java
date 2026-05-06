package com.yragent.app.orchestrator;

import com.yragent.domain.stage.StageResult;
import com.yragent.domain.stage.StageType;
import com.yragent.domain.stage.TaskExecutionContext;

public interface StageHandler {

    StageType support();

    StageResult handle(TaskExecutionContext context);
}
