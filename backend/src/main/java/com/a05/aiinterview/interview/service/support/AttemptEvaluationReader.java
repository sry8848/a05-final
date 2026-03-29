package com.a05.aiinterview.interview.service.support;

import com.a05.aiinterview.interview.entity.InterviewAttempt;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Attempt 评估快照读取工具。
 * 仅负责 latest final attempt 选择和 evaluationJson 字段安全读取。
 */
public final class AttemptEvaluationReader {

    private AttemptEvaluationReader() {
    }

    /**
     * 从 attempts 中选择最新 final attempt。
     * 规则：createdAt 最新；若 createdAt 相同，取 id 最大。
     */
    public static Optional<InterviewAttempt> selectLatestFinalAttempt(Collection<InterviewAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return Optional.empty();
        }

        Comparator<InterviewAttempt> comparator = Comparator
                .comparing(InterviewAttempt::getCreatedAt,
                        Comparator.nullsFirst(LocalDateTime::compareTo))
                .thenComparing(InterviewAttempt::getId, Comparator.nullsFirst(Long::compareTo));

        return attempts.stream()
                .filter(a -> a != null && Boolean.TRUE.equals(a.getIsFinal()))
                .max(comparator);
    }

    /**
     * 读取显式单题分值。
     * 只接受 Number 类型；其他类型（含字符串）一律返回 null。
     */
    public static BigDecimal readScore(Map<String, Object> evaluationJson) {
        if (evaluationJson == null) {
            return null;
        }
        Object rawScore = evaluationJson.get("score");
        if (!(rawScore instanceof Number)) {
            return null;
        }
        if (rawScore instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(rawScore.toString());
    }

    /**
     * 读取评语字段。
     * 仅接受非空白字符串。
     */
    public static String readCommentary(Map<String, Object> evaluationJson) {
        if (evaluationJson == null) {
            return null;
        }
        Object rawCommentary = evaluationJson.get("commentary");
        if (!(rawCommentary instanceof String text)) {
            return null;
        }
        return text.isBlank() ? null : text;
    }
}
