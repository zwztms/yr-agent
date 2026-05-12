package com.yragent.domain.gate.checklist;

import com.yragent.domain.stage.StageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StageChecklistRegistryTest {

    @Test
    void shouldHaveChecklistForAllSevenStages() {
        for (StageType stage : StageType.values()) {
            List<GateCheckItem> items = StageChecklistRegistry.forStage(stage);
            assertNotNull(items, stage + " should have checklist");
            assertFalse(items.isEmpty(), stage + " checklist should not be empty");
        }
    }

    @Test
    void shouldReturnEmptyForNullStage() {
        List<GateCheckItem> items = StageChecklistRegistry.forStage(null);
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    void planningStageShouldHaveCoreDimensions() {
        List<GateCheckItem> items = StageChecklistRegistry.forStage(StageType.PLANNING);
        List<String> dimensions = items.stream().map(GateCheckItem::dimension).toList();
        assertTrue(dimensions.contains("方案认知"));
        assertTrue(dimensions.contains("工具边界"));
        assertTrue(dimensions.contains("风险识别"));
    }

    @Test
    void everyItemShouldHaveCompleteFields() {
        for (StageType stage : StageType.values()) {
            for (GateCheckItem item : StageChecklistRegistry.forStage(stage)) {
                assertNotNull(item.id(), "id null in " + stage);
                assertNotNull(item.dimension(), "dimension null in " + stage);
                assertNotNull(item.keywords(), "keywords null in " + stage);
                assertFalse(item.id().isBlank());
                assertFalse(item.dimension().isBlank());
            }
        }
    }
}
