package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.common.enums.DomainStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 默认账本 reducer，实现 INTRO 特殊规则和单域状态推进。
 */
@Component
public class DefaultStateLedgerReducer implements StateLedgerReducer {

    @Override
    public Map<String, Object> reduce(Map<String, Object> oldLedger,
                                      LedgerMutation mutation,
                                      String attemptId,
                                      Long evidenceQuestionId) {
        Map<String, Object> ledger = deepCopyLedger(oldLedger);
        incrementMixProgress(ledger, normalizeQuestionType(mutation.getQuestionType()));
        ledger.put("asked_total", toInt(ledger.get("asked_total")) + 1);
        ledger.put("last_attempt_id", attemptId);

        if (isIntro(mutation.getQuestionType())) {
            if (mutation.getActiveProjectId() != null && !mutation.getActiveProjectId().isBlank()) {
                ledger.put("active_project_id", mutation.getActiveProjectId());
            }
            return ledger;
        }

        List<Map<String, Object>> states = extractDomainStates(ledger);
        for (Map<String, Object> state : states) {
            if (!Objects.equals(mutation.getCurrentDomainCode(), String.valueOf(state.get("domain_id")))) {
                continue;
            }
            applyDomainMutation(state, mutation, evidenceQuestionId);
            break;
        }
        ledger.put("domain_states", states);
        return ledger;
    }

    @SuppressWarnings("unchecked")
    private void applyDomainMutation(Map<String, Object> state,
                                     LedgerMutation mutation,
                                     Long evidenceQuestionId) {
        DomainStatus before = DomainStatus.fromLedgerValue(asString(state.get("status")));
        String signal = asString(mutation.getSignal()).trim().toUpperCase(Locale.ROOT);
        appendEvidence(state, evidenceQuestionId);

        if (before == DomainStatus.COVERED) {
            state.put("status", DomainStatus.COVERED.getValue());
            state.put("saturated", true);
            return;
        }

        if ("RETRY_SAME_DOMAIN".equals(signal)) {
            state.put("status", DomainStatus.IN_PROGRESS.getValue());
            state.put("saturated", false);
            return;
        }

        if ("END".equals(signal)) {
            if (mutation.isPassCurrentLevel()) {
                advanceDepth(state, mutation.getCurrentTargetDepth());
            }
            state.put("status", DomainStatus.COVERED.getValue());
            state.put("saturated", true);
            return;
        }

        if (!mutation.isPassCurrentLevel()) {
            state.put("status", DomainStatus.COVERED.getValue());
            state.put("saturated", true);
            return;
        }

        advanceDepth(state, mutation.getCurrentTargetDepth());

        if (mutation.isDeepen()) {
            state.put("status", DomainStatus.IN_PROGRESS.getValue());
            state.put("saturated", false);
            return;
        }

        state.put("status", DomainStatus.COVERED.getValue());
        state.put("saturated", true);
    }

    private void advanceDepth(Map<String, Object> state, String currentTargetDepth) {
        String oldDepth = normalizeDepthOrNull(asString(state.get("current_depth")));
        String newDepth = maxDepth(oldDepth, normalizeDepthOrNull(currentTargetDepth));
        if (newDepth != null) {
            state.put("current_depth", newDepth);
        }
    }

    @SuppressWarnings("unchecked")
    private void appendEvidence(Map<String, Object> state, Long evidenceQuestionId) {
        if (evidenceQuestionId == null) {
            return;
        }
        List<Object> refs = state.get("evidence_refs") instanceof List<?> raw
                ? new ArrayList<>(raw)
                : new ArrayList<>();
        refs.add(evidenceQuestionId);
        state.put("evidence_refs", refs);
    }

    @SuppressWarnings("unchecked")
    private void incrementMixProgress(Map<String, Object> ledger, String questionType) {
        Object mixObj = ledger.get("question_mix_progress");
        Map<String, Object> mix = mixObj instanceof Map<?, ?> raw
                ? new LinkedHashMap<>((Map<String, Object>) raw)
                : new LinkedHashMap<>();
        mix.put(questionType, toInt(mix.get(questionType)) + 1);
        ledger.put("question_mix_progress", mix);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDomainStates(Map<String, Object> ledger) {
        Object statesObj = ledger.get("domain_states");
        if (!(statesObj instanceof List<?> raw)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyLedger(Map<String, Object> ledger) {
        Map<String, Object> copy = ledger == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ledger);
        copy.put("domain_states", extractDomainStates(copy));
        Object mixObj = copy.get("question_mix_progress");
        if (mixObj instanceof Map<?, ?> rawMix) {
            copy.put("question_mix_progress", new LinkedHashMap<>((Map<String, Object>) rawMix));
        }
        return copy;
    }

    private boolean isIntro(String questionType) {
        return "INTRO".equalsIgnoreCase(asString(questionType));
    }

    private String normalizeQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return "PRINCIPLE";
        }
        return questionType.trim().toUpperCase(Locale.ROOT);
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
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

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeDepthOrNull(String depth) {
        if (depth == null || depth.isBlank()) {
            return null;
        }
        String normalized = depth.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("L[1-5]") ? normalized : null;
    }

    private String maxDepth(String left, String right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return depthIndex(left) >= depthIndex(right) ? left : right;
    }

    private int depthIndex(String depth) {
        return depth.charAt(1) - '0';
    }
}
