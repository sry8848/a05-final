package com.a05.aiinterview.speech.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AsrTranscriptAggregator {

    private final List<Segment> segments = new ArrayList<>();

    public ServerEvent onSentence(SentenceResult sentence) {
        if (sentence == null || sentence.heartbeat()) {
            return null;
        }
        if (!sentence.sentenceEnd()) {
            return new ServerEvent("interim", sentence.text(), null, null);
        }
        Segment segment = new Segment(sentence.text(), safeTime(sentence.beginTime()), safeTime(sentence.endTime()));
        segments.add(segment);
        return new ServerEvent("segment_final", segment.text(), segment.beginTime(), segment.endTime());
    }

    public FinalResult finalizeResult() {
        return finalizeResult(1);
    }

    public FinalResult finalizeResult(int pauseThresholdMs) {
        if (segments.isEmpty()) {
            return new FinalResult("", Map.of("wpm", 0, "longPauseCount", 0, "longestPauseMs", 0), List.of());
        }
        StringBuilder taggedText = new StringBuilder();
        int longPauseCount = 0;
        int longestPauseMs = 0;
        int totalChars = 0;

        for (int i = 0; i < segments.size(); i++) {
            Segment current = segments.get(i);
            taggedText.append(current.text());
            totalChars += current.text().length();
            if (i < segments.size() - 1) {
                Segment next = segments.get(i + 1);
                int pauseMs = Math.max(0, next.beginTime() - current.endTime());
                if (pauseMs >= pauseThresholdMs) {
                    longPauseCount++;
                    longestPauseMs = Math.max(longestPauseMs, pauseMs);
                    taggedText.append(" [停顿 ").append(String.format("%.1f", pauseMs / 1000.0)).append("s] ");
                }
            }
        }

        int lastEndTime = segments.get(segments.size() - 1).endTime();
        int wpm = lastEndTime > 0 ? (int) Math.round(totalChars / (lastEndTime / 1000.0 / 60.0)) : 0;
        Map<String, Object> pauseStats = new LinkedHashMap<>();
        pauseStats.put("wpm", wpm);
        pauseStats.put("longPauseCount", longPauseCount);
        pauseStats.put("longestPauseMs", longestPauseMs);

        List<Map<String, Object>> asrSegments = segments.stream()
                .map(segment -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("text", segment.text());
                    item.put("beginTime", segment.beginTime());
                    item.put("endTime", segment.endTime());
                    return item;
                })
                .toList();
        return new FinalResult(taggedText.toString(), pauseStats, asrSegments);
    }

    public List<Map<String, Object>> asrSegments() {
        return finalizeResult().asrSegments();
    }

    private int safeTime(Integer value) {
        return value == null ? 0 : value;
    }

    public record SentenceResult(String text, Integer beginTime, Integer endTime, boolean heartbeat, boolean sentenceEnd) {}

    public record ServerEvent(String type, String text, Integer beginTime, Integer endTime) {}

    public record FinalResult(String text, Map<String, Object> pauseStats, List<Map<String, Object>> asrSegments) {}

    private record Segment(String text, int beginTime, int endTime) {}
}
