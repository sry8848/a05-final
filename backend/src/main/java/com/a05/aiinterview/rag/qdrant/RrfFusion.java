package com.a05.aiinterview.rag.qdrant;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java RRF 融合工具。
 *
 * <p>输入多路排序后的 questionId 列表，按 Reciprocal Rank Fusion 计算总分。
 * 当前只做 questionId 级别融合，不绑定具体候选对象，便于单测与审计。
 */
@Component
public class RrfFusion {

    private static final int DEFAULT_RRF_K = 60;

    public List<FusedScore> fuse(List<List<String>> rankedQuestionIdLists, int limit) {
        if (rankedQuestionIdLists == null || rankedQuestionIdLists.isEmpty() || limit <= 0) {
            return List.of();
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        for (List<String> rankedIds : rankedQuestionIdLists) {
            if (rankedIds == null || rankedIds.isEmpty()) {
                continue;
            }
            for (int index = 0; index < rankedIds.size(); index++) {
                String questionId = normalize(rankedIds.get(index));
                if (questionId.isBlank()) {
                    continue;
                }
                int rank = index + 1;
                scores.merge(questionId, 1.0d / (DEFAULT_RRF_K + rank), Double::sum);
            }
        }

        if (scores.isEmpty()) {
            return List.of();
        }

        List<FusedScore> fused = new ArrayList<>(scores.size());
        scores.forEach((questionId, score) -> fused.add(new FusedScore(questionId, score)));
        fused.sort(Comparator
                .comparingDouble(FusedScore::score).reversed()
                .thenComparing(FusedScore::questionId));
        if (fused.size() <= limit) {
            return List.copyOf(fused);
        }
        return List.copyOf(fused.subList(0, limit));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record FusedScore(String questionId, double score) {
    }
}
