package com.yragent.domain.gate.checklist;

import com.yragent.domain.stage.StageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DimensionCheckerTest {

    @Test
    void shouldPassWhenUserInputCoversDimensionKeywords() {
        List<GateCheckItem> items = StageChecklistRegistry.forStage(StageType.PLANNING);
        GateCheckItem pl2 = items.stream().filter(i -> "PL-2".equals(i.id())).findFirst().orElseThrow();

        String userInput = "我会用write_file工具来创建文件，不需要其他工具";
        assertTrue(DimensionChecker.isDimensionCovered(pl2, userInput));
    }

    @Test
    void shouldFailWhenNoKeywordHit() {
        GateCheckItem item = new GateCheckItem("T-1", "测试维度", "问题?", List.of("write_file", "read_file"));
        String userInput = "我理解了计划，会按步骤执行";
        assertFalse(DimensionChecker.isDimensionCovered(item, userInput));
    }

    @Test
    void shouldFailWhenUserInputTooShort() {
        GateCheckItem item = new GateCheckItem("T-2", "测试维度", "问题?", List.of("工具", "权限"));
        String userInput = "懂了";
        assertFalse(DimensionChecker.isDimensionCovered(item, userInput));
    }

    @Test
    void shouldFailWhenUserIsEvasive() {
        GateCheckItem item = new GateCheckItem("T-3", "测试",
                "问题?", List.of("write_file", "工具"));
        String userInput = "你看着办吧，都可以";
        assertFalse(DimensionChecker.isDimensionCovered(item, userInput));
    }

    @Test
    void shouldHandleNullInputs() {
        GateCheckItem item = new GateCheckItem("T-3", "测试", "问题?", List.of("a"));
        assertFalse(DimensionChecker.isDimensionCovered(item, null));
        assertFalse(DimensionChecker.isDimensionCovered(null, "test"));
    }
}
