package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCatalog;
import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.ai.contract.StrategyDefinition;
import com.a05.aiinterview.ai.contract.StrategyLimit;
import com.a05.aiinterview.interview.entity.InterviewQuestion;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * quota_state 账本辅助工具。
 */
public final class QuotaStateSupport {

    public static final String LEDGER_KEY = "quota_state";
    public static final String SAME_POINT_CONTINUE = "samePointContinue";
    public static final String SAME_DOMAIN_CONTINUE = "sameDomainContinue";
    public static final String SAME_PROJECT_POINT_CONTINUE = "sameProjectPointContinue";
    public static final String SAME_PROJECT_CONTINUE = "sameProjectContinue";
    public static final String PRINCIPLE_TOTAL = "principleTotal";
    public static final String PROJECT_TOTAL = "projectTotal";
    public static final String SCENARIO_TOTAL = "scenarioTotal";
    public static final String BEHAVIORAL_TOTAL = "behavioralTotal";
    private static final Set<StrategyCode> SAME_POINT_FOLLOW_UP_STRATEGIES = EnumSet.of(
            StrategyCode.S_P_VERIFY,
            StrategyCode.S_P_DEEP_LINK,
            StrategyCode.S_P_VARIANT,
            StrategyCode.S_J_RECONSTRUCT,
            StrategyCode.S_J_RESPONSIBILITY,
            StrategyCode.S_J_PRESSURE,
            StrategyCode.S_J_TRADEOFF,
            StrategyCode.S_J_GUARDRAILS,
            StrategyCode.S_J_EVOLUTION,
            StrategyCode.S_S_FOLLOW_DIAGNOSE,
            StrategyCode.S_S_FOLLOW_RESPONSE,
            StrategyCode.S_S_FOLLOW_TRADEOFF,
            StrategyCode.S_S_FOLLOW_GUARDRAILS,
            StrategyCode.S_B_FOLLOW_DECISION,
            StrategyCode.S_B_FOLLOW_REFLECTION,
            StrategyCode.S_B_FOLLOW_CONFLICT,
            StrategyCode.S_B_FOLLOW_TRANSFER
    );

    private QuotaStateSupport() {
    }

    public static Map<String, Object> initialQuotaState() {
        Map<String, Object> quotaState = new LinkedHashMap<>();
        quotaState.put(SAME_POINT_CONTINUE, 0);
        quotaState.put(SAME_DOMAIN_CONTINUE, 0);
        quotaState.put(SAME_PROJECT_POINT_CONTINUE, 0);
        quotaState.put(SAME_PROJECT_CONTINUE, 0);
        quotaState.put(PRINCIPLE_TOTAL, 0);
        quotaState.put(PROJECT_TOTAL, 0);
        quotaState.put(SCENARIO_TOTAL, 0);
        quotaState.put(BEHAVIORAL_TOTAL, 0);
        return quotaState;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> ensureQuotaState(Map<String, Object> ledger,
                                                       List<InterviewQuestion> existingQuestions) {
        Map<String, Object> quotaState = initialQuotaState();
        backfillTotals(quotaState, existingQuestions);
        if (ledger != null) {
            Object rawQuotaState = ledger.get(LEDGER_KEY);
            if (rawQuotaState instanceof Map<?, ?> rawMap) {
                Map<String, Object> copied = new LinkedHashMap<>((Map<String, Object>) rawMap);
                overwriteCounter(quotaState, copied, SAME_POINT_CONTINUE);
                overwriteCounter(quotaState, copied, SAME_DOMAIN_CONTINUE);
                overwriteCounter(quotaState, copied, SAME_PROJECT_POINT_CONTINUE);
                overwriteCounter(quotaState, copied, SAME_PROJECT_CONTINUE);
                overwriteCounter(quotaState, copied, PRINCIPLE_TOTAL);
                overwriteCounter(quotaState, copied, PROJECT_TOTAL);
                overwriteCounter(quotaState, copied, SCENARIO_TOTAL);
                overwriteCounter(quotaState, copied, BEHAVIORAL_TOTAL);
            }
        }
        return quotaState;
    }

    public static Map<String, Object> advance(Map<String, Object> existingQuotaState,
                                              String currentQuestionType,
                                              StrategyCode strategyCode,
                                              String targetQuestionType) {
        Map<String, Object> quotaState = normalize(existingQuotaState);

        String currentType = normalizeQuestionType(currentQuestionType);
        StrategyDefinition strategy = strategyCode == null
                ? null
                : StrategyCatalog.find(strategyCode.code()).orElse(null);

        switch (currentType) {
            case "PRINCIPLE" -> clearProjectCounters(quotaState);
            case "PROJECT_DEEP_DIVE" -> clearPrincipleCounters(quotaState);
            default -> {
                clearPrincipleCounters(quotaState);
                clearProjectCounters(quotaState);
            }
        }

        if (strategy != null) {
            applyQuotaPolicy(quotaState, currentType, strategy);
            updateSamePointContinue(quotaState, strategyCode);
            return quotaState;
        }

        incrementTypeTotal(quotaState, normalizeQuestionType(targetQuestionType));
        quotaState.put(SAME_POINT_CONTINUE, 0);
        return quotaState;
    }

    public static boolean isSamePointFollowUp(StrategyCode strategyCode) {
        return strategyCode != null && SAME_POINT_FOLLOW_UP_STRATEGIES.contains(strategyCode);
    }

    private static void incrementTypeTotal(Map<String, Object> quotaState, String targetQuestionType) {
        switch (targetQuestionType) {
            case "PRINCIPLE" -> increment(quotaState, PRINCIPLE_TOTAL);
            case "PROJECT_DEEP_DIVE" -> increment(quotaState, PROJECT_TOTAL);
            case "SCENARIO" -> increment(quotaState, SCENARIO_TOTAL);
            case "BEHAVIORAL" -> increment(quotaState, BEHAVIORAL_TOTAL);
            default -> {
                // WRAPUP or unknown types do not advance totals.
            }
        }
    }

    private static void updateSamePointContinue(Map<String, Object> quotaState, StrategyCode strategyCode) {
        if (isSamePointFollowUp(strategyCode)) {
            increment(quotaState, SAME_POINT_CONTINUE);
            return;
        }
        quotaState.put(SAME_POINT_CONTINUE, 0);
    }

    private static void applyQuotaPolicy(Map<String, Object> quotaState,
                                         String currentQuestionType,
                                         StrategyDefinition strategy) {
        if (strategy.quotaUpdatePolicy().clearSourceContinuousCounters()) {
            clearContinuousCountersForSource(quotaState, currentQuestionType);
        }
        for (StrategyLimit limit : strategy.quotaUpdatePolicy().resets()) {
            if (limit == StrategyLimit.SAME_POINT_CONTINUE) {
                continue;
            }
            quotaState.put(limit.ledgerKey(), 0);
        }
        for (StrategyLimit limit : strategy.quotaUpdatePolicy().increments()) {
            if (limit == StrategyLimit.SAME_POINT_CONTINUE) {
                continue;
            }
            increment(quotaState, limit.ledgerKey());
        }
    }

    private static void clearContinuousCountersForSource(Map<String, Object> quotaState, String currentQuestionType) {
        switch (currentQuestionType) {
            case "PRINCIPLE" -> clearPrincipleCounters(quotaState);
            case "PROJECT_DEEP_DIVE" -> clearProjectCounters(quotaState);
            default -> {
                clearPrincipleCounters(quotaState);
                clearProjectCounters(quotaState);
            }
        }
    }

    private static void backfillTotals(Map<String, Object> quotaState, List<InterviewQuestion> existingQuestions) {
        if (existingQuestions == null) {
            return;
        }
        for (InterviewQuestion question : existingQuestions) {
            if (question == null) {
                continue;
            }
            incrementTypeTotal(quotaState, normalizeQuestionType(question.getQuestionType()));
        }
    }

    private static void overwriteCounter(Map<String, Object> quotaState, Map<String, Object> rawQuotaState, String key) {
        if (rawQuotaState.containsKey(key)) {
            quotaState.put(key, toInt(rawQuotaState.get(key)));
        }
    }

    private static Map<String, Object> normalize(Map<String, Object> existingQuotaState) {
        Map<String, Object> quotaState = initialQuotaState();
        if (existingQuotaState == null) {
            return quotaState;
        }
        overwriteCounter(quotaState, existingQuotaState, SAME_POINT_CONTINUE);
        overwriteCounter(quotaState, existingQuotaState, SAME_DOMAIN_CONTINUE);
        overwriteCounter(quotaState, existingQuotaState, SAME_PROJECT_POINT_CONTINUE);
        overwriteCounter(quotaState, existingQuotaState, SAME_PROJECT_CONTINUE);
        overwriteCounter(quotaState, existingQuotaState, PRINCIPLE_TOTAL);
        overwriteCounter(quotaState, existingQuotaState, PROJECT_TOTAL);
        overwriteCounter(quotaState, existingQuotaState, SCENARIO_TOTAL);
        overwriteCounter(quotaState, existingQuotaState, BEHAVIORAL_TOTAL);
        return quotaState;
    }

    private static void clearPrincipleCounters(Map<String, Object> quotaState) {
        quotaState.put(SAME_DOMAIN_CONTINUE, 0);
    }

    private static void clearProjectCounters(Map<String, Object> quotaState) {
        quotaState.put(SAME_PROJECT_POINT_CONTINUE, 0);
        quotaState.put(SAME_PROJECT_CONTINUE, 0);
    }

    private static void increment(Map<String, Object> quotaState, String key) {
        quotaState.put(key, toInt(quotaState.get(key)) + 1);
    }

    public static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String normalizeQuestionType(String questionType) {
        return questionType == null ? "" : questionType.trim().toUpperCase(Locale.ROOT);
    }
}
