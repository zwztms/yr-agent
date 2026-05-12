package com.yragent.domain.gate.checklist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceValidatorTest {

    @Test
    void shouldPassWhenEvidencePhraseFoundInUserInput() {
        String evidence = "用户明确提到要创建HelloWorld.java";
        String userInput = "我需要在workspace目录下创建一个HelloWorld.java文件";
        assertTrue(EvidenceValidator.isEvidenceValid(evidence, userInput));
    }

    @Test
    void shouldFailWhenEvidencePhraseNotFoundInUserInput() {
        String evidence = "用户明确提到要创建HelloWorld.java";
        String userInput = "我理解了任务目标，会按计划执行";
        assertFalse(EvidenceValidator.isEvidenceValid(evidence, userInput));
    }

    @Test
    void shouldPassWhenAnyKeywordFound() {
        String evidence = "用户确认工具选择 write_file";
        String userInput = "我会使用write_file来创建文件";
        assertTrue(EvidenceValidator.isEvidenceValid(evidence, userInput));
    }

    @Test
    void shouldFailWhenEvidenceIsVague() {
        String evidence = "用户理解正确，回答充分";
        String userInput = "我明白了";
        assertFalse(EvidenceValidator.isEvidenceValid(evidence, userInput));
    }

    @Test
    void shouldFailWhenEvidenceIsTooShort() {
        String evidence = "OK";
        String userInput = "我确认执行";
        assertFalse(EvidenceValidator.isEvidenceValid(evidence, userInput));
    }

    @Test
    void shouldIgnorePrefixWordsInEvidence() {
        String evidence = "开发者明确指出文件路径为src/main/App.java";
        String userInput = "文件放在src/main/App.java";
        assertTrue(EvidenceValidator.isEvidenceValid(evidence, userInput));
    }
}
