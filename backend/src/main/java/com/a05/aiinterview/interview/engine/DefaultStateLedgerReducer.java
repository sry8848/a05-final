package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.common.enums.DomainStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 默认账本 reducer，按新 decision/domainOutcome 语义推进账本。
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
        decrementTurnBudget(ledger);
        applyStatePatch(ledger, mutation);
        updateFocusTracking(ledger, mutation);
        updateRescueCounters(ledger, mutation);
        updateDomainFollowupCount(ledger, mutation);

        if (isIntro(mutation.getQuestionType())) {
            if (mutation.getActiveProjectId() != null && !mutation.getActiveProjectId().isBlank()) {
                ledger.put("active_project_id", mutation.getActiveProjectId());
            }
            if (mutation.getCurrentFocus() != null && !mutation.getCurrentFocus().isBlank()) {
                ledger.put("current_focus", mutation.getCurrentFocus());
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

    private void applyDomainMutation(Map<String, Object> state,
                                     LedgerMutation mutation,
                                     Long evidenceQuestionId) {
        DomainStatus before = DomainStatus.fromLedgerValue(asString(state.get("status")));
        appendEvidence(state, evidenceQuestionId);

        if (before == DomainStatus.COVERED || before == DomainStatus.CIRCUIT_BROKEN) {
            state.put("status", before.getValue());
            state.put("saturated", true);
            return;
        }

        if ("STRONG".equalsIgnoreCase(asString(mutation.getAnswerVerdict()))) {
            advanceDepth(state, mutation.getCurrentTargetDepth());
        }

        String domainOutcome = asString(mutation.getDomainOutcome()).trim().toLowerCase();
        if (mutation.isSkipCurrentQuestion()) {
            domainOutcome = "circuit_broken";
        }

        switch (domainOutcome) {
            case "covered" -> {
                state.put("status", DomainStatus.COVERED.getValue());
                state.put("saturated", true);
            }
            case "circuit_broken" -> {
                state.put("status", DomainStatus.CIRCUIT_BROKEN.getValue());
                state.put("saturated", true);
            }
            default -> {
                state.put("status", DomainStatus.IN_PROGRESS.getValue());
                state.put("saturated", false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyStatePatch(Map<String, Object> ledger, LedgerMutation mutation) {
        Map<String, Object> patch = mutation.getStatePatch();
        if (patch == null || patch.isEmpty()) {
            return;
        }
        mergeStringField(ledger, "active_project_id", patch, "activeProjectId", "active_project_id");
        mergeStringField(ledger, "current_focus", patch, "currentFocus", "current_focus");
        mergeListField(ledger, "covered_points", patch, "coveredPointsAdd", "covered_points_add");
        mergeListField(ledger, "weak_signals", patch, "weakSignalsAdd", "weak_signals_add");
        mergeListField(ledger, "recent_question_families", patch, "recentQuestionFamiliesAdd", "recent_question_families_add");
    }

    private void mergeStringField(Map<String, Object> ledger,
                                  String ledgerKey,
                                  Map<String, Object> patch,
                                  String... patchKeys) {
        for (String patchKey : patchKeys) {
            Object value = patch.get(patchKey);
            if (value instanceof String str && !str.isBlank()) {
                ledger.put(ledgerKey, str);
                return;
            }
        }
    }

    private void mergeListField(Map<String, Object> ledger,
                                String ledgerKey,
                                Map<String, Object> patch,
                                String... patchKeys) {
        LinkedHashSet<String> values = new LinkedHashSet<>(toStringList(ledger.get(ledgerKey)));
        for (String patchKey : patchKeys) {
            values.addAll(toStringList(patch.get(patchKey)));
        }
        ledger.put(ledgerKey, new ArrayList<>(values));
    }

    private void decrementTurnBudget(Map<String, Object> ledger) {
        Object value = ledger.get("remaining_turn_budget");
        if (!(value instanceof Number n)) {
            return;
        }
        ledger.put("remaining_turn_budget", Math.max(0, n.intValue() - 1));
    }

    private void advanceDepth(Map<String, Object> state, String currentTargetDepth) {
        String oldDepth = normalizeDepthOrNull(asString(state.get("current_depth")));
        String newDepth = maxDepth(oldDepth, normalizeDepthOrNull(currentTargetDepth));
        state.put("current_depth", newDepth == null ? "" : newDepth);
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
        copy.put("covered_points", new ArrayList<>(toStringList(copy.get("covered_points"))));
        copy.put("weak_signals", new ArrayList<>(toStringList(copy.get("weak_signals"))));
        copy.put("recent_question_families", new ArrayList<>(toStringList(copy.get("recent_question_families"))));
        Object rescueCounts = copy.get("rescue_counts_by_domain");
        if (rescueCounts instanceof Map<?, ?> rawRescueCounts) {
            copy.put("rescue_counts_by_domain", new LinkedHashMap<>((Map<String, Object>) rawRescueCounts));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private void updateRescueCounters(Map<String, Object> ledger, LedgerMutation mutation) {
        if (!"rescue".equalsIgnoreCase(asString(mutation.getDecision()))) {
            return;
        }
        ledger.put("rescue_total", toInt(ledger.get("rescue_total")) + 1);
        Object raw = ledger.get("rescue_counts_by_domain");
        Map<String, Object> counts = raw instanceof Map<?, ?> existing
                ? new LinkedHashMap<>((Map<String, Object>) existing)
                : new LinkedHashMap<>();
        String domainCode = asString(mutation.getCurrentDomainCode());
        counts.put(domainCode, toInt(counts.get(domainCode)) + 1);
        ledger.put("rescue_counts_by_domain", counts);
    }

    private void updateFocusTracking(Map<String, Object> ledger, LedgerMutation mutation) {
        String focus = asString(mutation.getFocusPoint()).trim();
        if (focus.isBlank() || isIntro(mutation.getQuestionType())) {
            return;
        }
        String previousFocus = asString(ledger.get("last_focus_point")).trim();
        int nextStreak = Objects.equals(previousFocus, focus)
                ? toInt(ledger.get("current_focus_streak")) + 1
                : 1;
        ledger.put("last_focus_point", focus);
        ledger.put("current_focus_streak", nextStreak);
    }

    /**
     * 更新当前知识域的连续追问计数。
     * 当切换到新知识域时重置计数，同一域追问时递增。
     * 用于限制同一知识域的追问轮数，避免面试官一直深入一个点问不停。
     */
    private void updateDomainFollowupCount(Map<String, Object> ledger, LedgerMutation mutation) {
        String currentDomainCode = asString(mutation.getCurrentDomainCode()).trim();
        if (currentDomainCode.isBlank() || isIntro(mutation.getQuestionType())) {
            return;
        }
        
        String lastDomainCode = asString(ledger.get("current_domain_code")).trim();
        int currentCount = toInt(ledger.get("current_domain_followup_count"));
        
        // 判断是否为追问行为（followup 或 probe）
        String decision = asString(mutation.getDecision()).trim().toLowerCase();
        boolean isFollowupAction = "followup".equals(decision) || "probe".equals(decision);
        
        if (currentDomainCode.equals(lastDomainCode)) {
            // 同一知识域，追问计数+1
            ledger.put("current_domain_followup_count", currentCount + 1);
        } else {
            // 切换到新知识域，重置计数
            // 如果是追问行为开始新域，计数从1开始；否则重置为0
            ledger.put("current_domain_code", currentDomainCode);
            ledger.put("current_domain_followup_count", isFollowupAction ? 1 : 0);
        }
    }

    private boolean isIntro(String questionType) {
        return "INTRO".equalsIgnoreCase(asString(questionType));
    }

    private String normalizeQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return "PRINCIPLE";
        }
        return questionType.trim().toUpperCase();
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

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item == null) {
                continue;
            }
            String str = item.toString().trim();
            if (!str.isBlank()) {
                result.add(str);
            }
        }
        return result;
    }

    private String normalizeDepthOrNull(String depth) {
        if (depth == null || depth.isBlank()) {
            return null;
        }
        String normalized = depth.trim().toUpperCase();
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
