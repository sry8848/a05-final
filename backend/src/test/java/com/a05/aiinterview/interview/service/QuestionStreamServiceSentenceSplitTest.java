package com.a05.aiinterview.interview.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionStreamServiceSentenceSplitTest {

    @Test
    void extractClosedSentences_shouldSplitByConfiguredPunctuation() {
        StringBuilder buffer = new StringBuilder("第一句，第二句！第三句");

        List<String> segments = QuestionStreamService.extractClosedSentences(buffer);

        assertEquals(List.of("第一句，", "第二句！"), segments);
        assertEquals("第三句", buffer.toString());
    }

    @Test
    void flushTrailingSentence_shouldReturnAndClearBuffer() {
        StringBuilder buffer = new StringBuilder("尾句未闭合");

        String trailing = QuestionStreamService.flushTrailingSentence(buffer);

        assertEquals("尾句未闭合", trailing);
        assertEquals("", buffer.toString());
    }

    @Test
    void normalizeDifficultyForStorage_shouldMapLegacyValues() {
        assertEquals("L1", QuestionStreamService.normalizeDifficultyForStorage("easy", "L3"));
        assertEquals("L3", QuestionStreamService.normalizeDifficultyForStorage("medium", "L3"));
        assertEquals("L5", QuestionStreamService.normalizeDifficultyForStorage("hard", "L3"));
        assertEquals("L4", QuestionStreamService.normalizeDifficultyForStorage("L4", "L3"));
        assertEquals("L3", QuestionStreamService.normalizeDifficultyForStorage("unknown", "L3"));
    }

    @Test
    void trimStemsToMaxChars_shouldDropOldestUntilWithinLimit() {
        List<String> trimmed = QuestionStreamService.trimStemsToMaxChars(
                List.of("AAAA", "BBBB", "CCCC"),
                8
        );
        assertEquals(List.of("BBBB", "CCCC"), trimmed);
    }

    @Test
    void trimStemsToMaxChars_singleStemTooLong_shouldDropItInsteadOfTruncating() {
        List<String> trimmed = QuestionStreamService.trimStemsToMaxChars(
                List.of("123456789"),
                4
        );
        assertEquals(List.of(), trimmed);
    }
}
