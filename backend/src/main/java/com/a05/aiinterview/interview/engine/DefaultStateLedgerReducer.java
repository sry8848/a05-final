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
 * 新账本 reducer。
 * 只维护最小运行态：当前焦点、当前 item、已覆盖点、候选点和知识域状态。
 */
@Component
public class DefaultStateLedgerReducer implements StateLedgerReducer {

    @Override
    public Map<String, Object> reduce(Map<String, Object> oldLedger,
                                      LedgerMutation mutation,
                                      String attemptId,
                                      Long evidenceQuestionId) {
        Map<String, Object> ledger = deepCopyLedger(oldLedger);
        ledger.put("last_attempt_id", attemptId);

        putIfNotBlank(ledger, "current_focus", firstNonBlank(mutation.getNextFocus(), mutation.getCurrentFocus()));
        putIfNotBlank(ledger, "active_item_key", firstNonBlank(mutation.getNextItemKey(), mutation.getCurrentItemKey()));
        putIfNotBlank(ledger, "active_item_type", firstNonBlank(mutation.getNextItemType(), mutation.getCurrentItemType()));
        putIfNotBlank(ledger, "active_item_name", firstNonBlank(mutation.getNextItemName(), mutation.getCurrentItemName()));

        mergeStringListField(ledger, "covered_points", mutation.getNewCoveredPoints());
        mergeQuestionFamily(ledger, mutation.getQuestionFamilyId());
        mergeCoveredDomains(ledger, mutation.getNewCoveredDomains());
        updateDomainStates(ledger, mutation, evidenceQuestionId);
        return ledger;
    }

    private void mergeQuestionFamily(Map<String, Object> ledger, String questionFamilyId) {
        if (questionFamilyId == null || questionFamilyId.isBlank()) {
            return;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>(toStringList(ledger.get("recent_question_families")));
        values.add(questionFamilyId.trim());
        ledger.put("recent_question_families", new ArrayList<>(values));
    }

    private void mergeCoveredDomains(Map<String, Object> ledger,
                                     List<LedgerMutation.CoveredDomainByCode> coveredDomains) {
        if (coveredDomains == null || coveredDomains.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> values = toCoveredDomainMap(ledger.get("covered_domains"));
        for (LedgerMutation.CoveredDomainByCode coveredDomain : coveredDomains) {
            if (coveredDomain == null || coveredDomain.getDomainCode() == null || coveredDomain.getDomainCode().isBlank()) {
                continue;
            }
            values.put(coveredDomain.getDomainCode().trim(), buildCoveredDomainEntry(
                    coveredDomain.getDomainCode(),
                    coveredDomain.getDomainName()
            ));
        }
        ledger.put("covered_domains", new ArrayList<>(values.values()));
    }

    @SuppressWarnings("unchecked")
    private void updateDomainStates(Map<String, Object> ledger,
                                    LedgerMutation mutation,
                                    Long evidenceQuestionId) {
        Object rawStates = ledger.get("domain_states");
        if (!(rawStates instanceof List<?> states)) {
            return;
        }
        List<Map<String, Object>> copiedStates = new ArrayList<>();
        for (Object stateObj : states) {
            if (stateObj instanceof Map<?, ?> state) {
                copiedStates.add(new LinkedHashMap<>((Map<String, Object>) state));
            }
        }

        for (Map<String, Object> state : copiedStates) {
            String domainCode = asString(state.get("domainCode"));

            if (Objects.equals(domainCode, mutation.getCurrentDomainCode()) && evidenceQuestionId != null) {
                LinkedHashSet<Long> refs = new LinkedHashSet<>(toLongList(state.get("evidenceRefs")));
                refs.add(evidenceQuestionId);
                state.put("evidenceRefs", new ArrayList<>(refs));
                if (!isCovered(state) && !mutation.isSkipCurrentQuestion()) {
                    state.put("status", DomainStatus.IN_PROGRESS.getValue());
                    state.put("saturated", false);
                }
            }

            if (mutation.getNewCoveredDomains() == null) {
                continue;
            }
            for (LedgerMutation.CoveredDomainByCode coveredDomain : mutation.getNewCoveredDomains()) {
                if (coveredDomain == null) {
                    continue;
                }
                if (Objects.equals(domainCode, coveredDomain.getDomainCode())) {
                    state.put("status", DomainStatus.COVERED.getValue());
                    state.put("saturated", true);
                }
            }
        }

        ledger.put("domain_states", copiedStates);
    }

    private boolean isCovered(Map<String, Object> state) {
        return DomainStatus.COVERED.getValue().equalsIgnoreCase(asString(state.get("status")));
    }

    private void mergeStringListField(Map<String, Object> ledger, String key, List<String> additions) {
        LinkedHashSet<String> values = new LinkedHashSet<>(toStringList(ledger.get(key)));
        if (additions != null) {
            additions.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(values::add);
        }
        ledger.put(key, new ArrayList<>(values));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyLedger(Map<String, Object> ledger) {
        Map<String, Object> copy = ledger == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ledger);
        copy.put("covered_domains", new ArrayList<>(toCoveredDomainMap(copy.get("covered_domains")).values()));
        copy.put("covered_points", new ArrayList<>(toStringList(copy.get("covered_points"))));
        copy.put("recent_question_families", new ArrayList<>(toStringList(copy.get("recent_question_families"))));
        copy.put("quota_state", new LinkedHashMap<>(QuotaStateSupport.ensureQuotaState(copy, List.of())));
        copy.put("decision_fallback_state", new LinkedHashMap<>(DecisionFallbackStateSupport.ensureState(copy)));
        Object domainStates = copy.get("domain_states");
        if (domainStates instanceof List<?> rawDomainStates) {
            List<Map<String, Object>> cloned = new ArrayList<>();
            for (Object item : rawDomainStates) {
                if (item instanceof Map<?, ?> map) {
                    cloned.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            copy.put("domain_states", cloned);
        } else {
            copy.put("domain_states", new ArrayList<>());
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> toCoveredDomainMap(Object value) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (!(value instanceof List<?> rawList)) {
            return result;
        }
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) rawMap);
                String domainCode = asString(map.get("domainCode")).trim();
                if (domainCode.isBlank()) {
                    continue;
                }
                result.put(domainCode, buildCoveredDomainEntry(domainCode, asString(map.get("domainName"))));
            }
        }
        return result;
    }

    private Map<String, Object> buildCoveredDomainEntry(String domainCode, String domainName) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("domainCode", domainCode == null ? "" : domainCode.trim());
        entry.put("domainName", domainName == null ? "" : domainName.trim());
        return entry;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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
            String text = String.valueOf(item).trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private List<Long> toLongList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : rawList) {
            Long longValue = toLong(item);
            if (longValue != null) {
                result.add(longValue);
            }
        }
        return result;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int toInt(Object value) {
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

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
