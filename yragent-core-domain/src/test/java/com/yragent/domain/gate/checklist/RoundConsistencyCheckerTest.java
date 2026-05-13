package com.yragent.domain.gate.checklist;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoundConsistencyCheckerTest {

    @Test
    void shouldFlagWhenSameScoreButUserInputGrew() {
        List<ItemCoverage> prevScores = List.of(
                new ItemCoverage("R1", "partial", "不足", "需要更多")
        );
        List<ItemCoverage> curScores = List.of(
                new ItemCoverage("R1", "partial", "仍然不足", "需要更多")
        );
        String prevInput = "理解了";
        String curInput = "我理解了架构设计，会用write_file创建文件，文件路径在src/main下";

        List<ItemCoverage> result = RoundConsistencyChecker.check(prevScores, curScores, prevInput, curInput);
        assertEquals("partial", result.get(0).status());
        assertTrue(result.get(0).suggestion().contains("LLM"));
    }

    @Test
    void shouldNotFlagWhenScoreChanged() {
        List<ItemCoverage> prevScores = List.of(
                new ItemCoverage("R1", "partial", "不足", "需要更多")
        );
        List<ItemCoverage> curScores = List.of(
                new ItemCoverage("R1", "covered", "现在够了", "")
        );

        List<ItemCoverage> result = RoundConsistencyChecker.check(prevScores, curScores, "old", "new long input");
        assertEquals("covered", result.get(0).status());
    }

    @Test
    void shouldReturnEmptyWhenNoPreviousScores() {
        List<ItemCoverage> curScores = List.of(
                new ItemCoverage("R1", "partial", "...", "需要更多")
        );
        List<ItemCoverage> result = RoundConsistencyChecker.check(null, curScores, null, "input");
        assertEquals("partial", result.get(0).status());
    }

    @Test
    void shouldNotFlagWhenInputBarelyGrew() {
        List<ItemCoverage> prevScores = List.of(
                new ItemCoverage("R1", "partial", "不够", "需要更多")
        );
        List<ItemCoverage> curScores = List.of(
                new ItemCoverage("R1", "partial", "不够", "需要更多")
        );
        String prevInput = "我理解了基本目标，知道要创建文件";
        String curInput = "我理解了基本目标，知道要创建文件了";

        List<ItemCoverage> result = RoundConsistencyChecker.check(prevScores, curScores, prevInput, curInput);
        assertEquals("partial", result.get(0).status());
        assertFalse(result.get(0).suggestion().contains("LLM"));
    }

    @Test
    void shouldHandleEmptyScores() {
        List<ItemCoverage> result = RoundConsistencyChecker.check(
                List.of(), List.of(), "old", "new long input with more words here too");
        assertTrue(result.isEmpty());
    }
}
