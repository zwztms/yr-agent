package com.yragent.tool;

import com.yragent.domain.tool.ToolCapability;
import com.yragent.execution.UnifiedToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolRegistry {

    private final UnifiedToolExecutor unifiedToolExecutor;

    public ToolRegistry(UnifiedToolExecutor unifiedToolExecutor) {
        this.unifiedToolExecutor = unifiedToolExecutor;
    }

    public List<ToolCapability> listAll() {
        return unifiedToolExecutor.getAllTools();
    }
}
