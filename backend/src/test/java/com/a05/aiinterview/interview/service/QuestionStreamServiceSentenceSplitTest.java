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
}
