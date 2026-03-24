package com.a05.aiinterview.interview.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DecisionFallbackStateSupport {

    public static final String LEDGER_KEY = "decision_fallback_state";
    public static final String ROTATION_INDEX = "rotationIndex";
    public static final String CONSECUTIVE_FALLBACK_COUNT = "consecutiveFallbackCount";
    public static final String TOTAL_FALLBACK_COUNT = "totalFallbackCount";

    private DecisionFallbackStateSupport() {
    }

    public static Map<String, Object> initialState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(ROTATION_INDEX, 0);
        state.put(CONSECUTIVE_FALLBACK_COUNT, 0);
        state.put(TOTAL_FALLBACK_COUNT, 0);
        return state;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> ensureState(Map<String, Object> ledger) {
        Map<String, Object> state = initialState();
        if (ledger == null) {
            return state;
        }
        Object raw = ledger.get(LEDGER_KEY);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>((Map<String, Object>) map);
            overwrite(state, copied, ROTATION_INDEX);
            overwrite(state, copied, CONSECUTIVE_FALLBACK_COUNT);
            overwrite(state, copied, TOTAL_FALLBACK_COUNT);
        }
        return state;
    }

    public static Map<String, Object> previewAfterFallback(Map<String, Object> ledger) {
        Map<String, Object> state = ensureState(ledger);
        state.put(ROTATION_INDEX, toInt(state.get(ROTATION_INDEX)) + 1);
        state.put(CONSECUTIVE_FALLBACK_COUNT, toInt(state.get(CONSECUTIVE_FALLBACK_COUNT)) + 1);
        state.put(TOTAL_FALLBACK_COUNT, toInt(state.get(TOTAL_FALLBACK_COUNT)) + 1);
        return state;
    }

    public static void applyPlan(Map<String, Object> ledger, DecisionExecutionPlan plan) {
        Map<String, Object> state = ensureState(ledger);
        if (plan == null) {
            ledger.put(LEDGER_KEY, state);
            return;
        }
        if (plan.getEffectiveDecisionSource() == DecisionExecutionPlan.EffectiveDecisionSource.SYSTEM_FALLBACK) {
            state.put(ROTATION_INDEX, toInt(state.get(ROTATION_INDEX)) + 1);
            state.put(CONSECUTIVE_FALLBACK_COUNT, toInt(state.get(CONSECUTIVE_FALLBACK_COUNT)) + 1);
            state.put(TOTAL_FALLBACK_COUNT, toInt(state.get(TOTAL_FALLBACK_COUNT)) + 1);
        } else if ("CONTINUE".equalsIgnoreCase(plan.getInterviewAction())) {
            state.put(CONSECUTIVE_FALLBACK_COUNT, 0);
        }
        ledger.put(LEDGER_KEY, state);
    }

    public static boolean shouldTerminateAfterFallback(Map<String, Object> previewState) {
        return toInt(previewState.get(CONSECUTIVE_FALLBACK_COUNT)) >= 2
                || toInt(previewState.get(TOTAL_FALLBACK_COUNT)) >= 4;
    }

    private static void overwrite(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, toInt(source.get(key)));
        }
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
}
