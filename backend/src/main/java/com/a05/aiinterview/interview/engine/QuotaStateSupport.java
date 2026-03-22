package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.interview.entity.InterviewQuestion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
                                              String finalDecision,
                                              String nextQuestionType) {
        Map<String, Object> quotaState = normalize(existingQuotaState);

        String currentType = normalizeQuestionType(currentQuestionType);
        String decision = finalDecision == null ? "" : finalDecision.trim();

        switch (currentType) {
            case "PRINCIPLE" -> {
                clearProjectCounters(quotaState);
                applyPrincipleCounters(quotaState, decision);
            }
            case "PROJECT_DEEP_DIVE" -> {
                clearPrincipleCounters(quotaState);
                applyProjectCounters(quotaState, decision);
            }
            case "SCENARIO", "BEHAVIORAL", "INTRO" -> {
                clearPrincipleCounters(quotaState);
                clearProjectCounters(quotaState);
                // 连续计数在这些题型下统一清零。
            }
            default -> {
                clearPrincipleCounters(quotaState);
                clearProjectCounters(quotaState);
                // 未知题型不推进连续计数，只保留总额更新。
            }
        }

        incrementTypeTotal(quotaState, normalizeQuestionType(nextQuestionType));
        return quotaState;
    }

    private static void applyPrincipleCounters(Map<String, Object> quotaState, String decision) {
        switch (decision) {
            case "引导和验证", "变式" -> {
                increment(quotaState, SAME_POINT_CONTINUE);
                increment(quotaState, SAME_DOMAIN_CONTINUE);
            }
            case "深入到强关联点", "平移到同知识域知识点" -> {
                quotaState.put(SAME_POINT_CONTINUE, 0);
                increment(quotaState, SAME_DOMAIN_CONTINUE);
            }
            default -> {
                quotaState.put(SAME_POINT_CONTINUE, 0);
                quotaState.put(SAME_DOMAIN_CONTINUE, 0);
            }
        }
    }

    private static void applyProjectCounters(Map<String, Object> quotaState, String decision) {
        switch (decision) {
            case "引导还原", "真实情景", "责任定位", "压测", "做权衡", "兜底与观测", "演进与复盘" -> {
                increment(quotaState, SAME_PROJECT_POINT_CONTINUE);
                increment(quotaState, SAME_PROJECT_CONTINUE);
            }
            case "收敛并项目外扩", "切换项目要点" -> {
                quotaState.put(SAME_PROJECT_POINT_CONTINUE, 0);
                increment(quotaState, SAME_PROJECT_CONTINUE);
            }
            default -> {
                quotaState.put(SAME_PROJECT_POINT_CONTINUE, 0);
                quotaState.put(SAME_PROJECT_CONTINUE, 0);
            }
        }
    }

    private static void incrementTypeTotal(Map<String, Object> quotaState, String nextQuestionType) {
        switch (nextQuestionType) {
            case "PRINCIPLE" -> increment(quotaState, PRINCIPLE_TOTAL);
            case "PROJECT_DEEP_DIVE" -> increment(quotaState, PROJECT_TOTAL);
            case "SCENARIO" -> increment(quotaState, SCENARIO_TOTAL);
            case "BEHAVIORAL" -> increment(quotaState, BEHAVIORAL_TOTAL);
            default -> {
                // WRAPUP or unknown types do not advance totals.
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
        quotaState.put(SAME_POINT_CONTINUE, 0);
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
