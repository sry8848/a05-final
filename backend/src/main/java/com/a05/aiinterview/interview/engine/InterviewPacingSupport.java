package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyLimit;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.common.enums.ExperienceLevel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 面试节奏配置真源。
 * 统一管理不同经验等级下的 max_questions 与策略限额上限。
 */
public final class InterviewPacingSupport {

    public static final String MAX_QUESTIONS_KEY = "max_questions";

    private static final Profile INTERN = profile(14, 1, 2, 1, 2, 3, 2, 1, 1);
    private static final Profile FRESH_GRAD = profile(14, 1, 2, 1, 2, 3, 2, 1, 1);
    private static final Profile JUNIOR = profile(15, 1, 2, 1, 3, 3, 3, 2, 1);
    private static final Profile MIDDLE = profile(16, 1, 3, 2, 3, 3, 3, 3, 1);
    private static final Profile SENIOR = profile(17, 1, 3, 2, 4, 2, 4, 4, 1);

    private static final Map<String, Profile> BY_LEVEL = Map.of(
            ExperienceLevel.INTERN.name(), INTERN,
            ExperienceLevel.FRESH_GRAD.name(), FRESH_GRAD,
            ExperienceLevel.JUNIOR.name(), JUNIOR,
            ExperienceLevel.MIDDLE.name(), MIDDLE,
            ExperienceLevel.SENIOR.name(), SENIOR
    );

    private InterviewPacingSupport() {
    }

    public static int maxQuestions(String experienceLevel) {
        return resolve(experienceLevel).maxQuestions();
    }

    public static int maxFor(String experienceLevel, StrategyLimit limit) {
        return resolve(experienceLevel).maxFor(limit);
    }

    public static Map<String, EvaluationDecisionInput.QuotaSnapshotItem> buildQuotaSnapshot(
            String experienceLevel,
            Map<String, Object> quotaState) {
        Profile profile = resolve(experienceLevel);
        Map<String, Object> normalizedQuotaState = quotaState == null ? Map.of() : quotaState;
        Map<String, EvaluationDecisionInput.QuotaSnapshotItem> snapshot = new LinkedHashMap<>();
        for (StrategyLimit limit : StrategyLimit.values()) {
            snapshot.put(limit.ledgerKey(), EvaluationDecisionInput.QuotaSnapshotItem.builder()
                    .used(QuotaStateSupport.toInt(normalizedQuotaState.get(limit.ledgerKey())))
                    .max(profile.maxFor(limit))
                    .build());
        }
        return snapshot;
    }

    private static Profile resolve(String experienceLevel) {
        String normalized = ExperienceLevel.normalizeStoredValue(experienceLevel);
        return BY_LEVEL.getOrDefault(normalized, JUNIOR);
    }

    private static Profile profile(int maxQuestions,
                                   int samePoint,
                                   int sameDomain,
                                   int sameProjectPoint,
                                   int sameProject,
                                   int principle,
                                   int project,
                                   int scenario,
                                   int behavioral) {
        Map<StrategyLimit, Integer> limits = new LinkedHashMap<>();
        limits.put(StrategyLimit.SAME_POINT_CONTINUE, samePoint);
        limits.put(StrategyLimit.SAME_DOMAIN_CONTINUE, sameDomain);
        limits.put(StrategyLimit.SAME_PROJECT_POINT_CONTINUE, sameProjectPoint);
        limits.put(StrategyLimit.SAME_PROJECT_CONTINUE, sameProject);
        limits.put(StrategyLimit.PRINCIPLE_TOTAL, principle);
        limits.put(StrategyLimit.PROJECT_TOTAL, project);
        limits.put(StrategyLimit.SCENARIO_TOTAL, scenario);
        limits.put(StrategyLimit.BEHAVIORAL_TOTAL, behavioral);
        return new Profile(maxQuestions, Map.copyOf(limits));
    }

    private record Profile(int maxQuestions, Map<StrategyLimit, Integer> limits) {
        private int maxFor(StrategyLimit limit) {
            return limits.getOrDefault(limit, 0);
        }
    }
}
