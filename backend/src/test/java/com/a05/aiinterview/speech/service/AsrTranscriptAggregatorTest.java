package com.a05.aiinterview.speech.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AsrTranscriptAggregatorTest {

    @Test
    void onSentence_shouldIgnoreHeartbeat() {
        AsrTranscriptAggregator aggregator = new AsrTranscriptAggregator();

        AsrTranscriptAggregator.ServerEvent event = aggregator.onSentence(
                new AsrTranscriptAggregator.SentenceResult("占位", 0, null, true, false));

        assertNull(event);
        assertEquals(List.of(), aggregator.asrSegments());
    }

    @Test
    void onSentence_shouldEmitInterimWhenSentenceNotEnded() {
        AsrTranscriptAggregator aggregator = new AsrTranscriptAggregator();

        AsrTranscriptAggregator.ServerEvent event = aggregator.onSentence(
                new AsrTranscriptAggregator.SentenceResult("中间字幕", 100, null, false, false));

        assertEquals("interim", event.type());
        assertEquals("中间字幕", event.text());
    }

    @Test
    void finalizeResult_shouldBuildPauseStatsFromNeighborSegmentTimeline() {
        AsrTranscriptAggregator aggregator = new AsrTranscriptAggregator();
        aggregator.onSentence(new AsrTranscriptAggregator.SentenceResult("Redis 缓存击穿", 0, 1000, false, true));
        aggregator.onSentence(new AsrTranscriptAggregator.SentenceResult("可以用互斥锁解决", 4200, 6200, false, true));

        AsrTranscriptAggregator.FinalResult result = aggregator.finalizeResult(2500);

        assertEquals("Redis 缓存击穿 [停顿 3.2s] 可以用互斥锁解决", result.text());
        assertEquals(1, result.pauseStats().get("longPauseCount"));
        assertEquals(3200, result.pauseStats().get("longestPauseMs"));
        assertEquals(2, result.asrSegments().size());
    }
}
