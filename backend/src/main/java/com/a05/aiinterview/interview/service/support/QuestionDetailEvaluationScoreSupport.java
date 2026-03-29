package com.a05.aiinterview.interview.service.support;

import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;

import java.math.BigDecimal;

/**
 * 单题详细评估分数口径收敛工具。
 */
public final class QuestionDetailEvaluationScoreSupport {

    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

    private QuestionDetailEvaluationScoreSupport() {
    }

    public static QuestionDetailEvaluationOutput clampToPercentageRange(QuestionDetailEvaluationOutput output) {
        if (output == null) {
            return null;
        }
        output.setScore(clamp(output.getScore()));
        if (output.getEvaluatedDomains() != null) {
            output.getEvaluatedDomains().forEach(domain -> {
                if (domain != null) {
                    domain.setScore(clamp(domain.getScore()));
                }
            });
        }
        return output;
    }

    private static BigDecimal clamp(BigDecimal score) {
        if (score == null) {
            return null;
        }
        if (score.compareTo(MIN_SCORE) < 0) {
            return MIN_SCORE;
        }
        if (score.compareTo(MAX_SCORE) > 0) {
            return MAX_SCORE;
        }
        return score;
    }
}
