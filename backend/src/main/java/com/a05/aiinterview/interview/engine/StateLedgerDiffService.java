package com.a05.aiinterview.interview.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 生成账本审计 diff，供 attempt.evaluation_json 与调试日志使用。
 */
@Component
public class StateLedgerDiffService {

    public Map<String, Object> diff(Map<String, Object> oldLedger, Map<String, Object> newLedger) {
        Map<String, Object> diff = new LinkedHashMap<>();
        if (!Objects.equals(oldLedger.get("asked_total"), newLedger.get("asked_total"))) {
            diff.put("asked_total", newLedger.get("asked_total"));
        }
        if (!Objects.equals(oldLedger.get("last_attempt_id"), newLedger.get("last_attempt_id"))) {
            diff.put("last_attempt_id", newLedger.get("last_attempt_id"));
        }
        if (!Objects.equals(oldLedger.get("active_project_id"), newLedger.get("active_project_id"))) {
            diff.put("active_project_id", newLedger.get("active_project_id"));
        }
        if (!Objects.equals(oldLedger.get("question_mix_progress"), newLedger.get("question_mix_progress"))) {
            diff.put("question_mix_progress", newLedger.get("question_mix_progress"));
        }

        List<Map<String, Object>> beforeStates = extractDomainStates(oldLedger.get("domain_states"));
        List<Map<String, Object>> afterStates = extractDomainStates(newLedger.get("domain_states"));
        Map<String, Map<String, Object>> beforeIndex = indexByDomainCode(beforeStates);
        Map<String, Map<String, Object>> afterIndex = indexByDomainCode(afterStates);
        Set<String> changedDomains = new LinkedHashSet<>();
        changedDomains.addAll(beforeIndex.keySet());
        changedDomains.addAll(afterIndex.keySet());

        List<Map<String, Object>> domainDiffs = new ArrayList<>();
        for (String domainCode : changedDomains) {
            Map<String, Object> before = beforeIndex.get(domainCode);
            Map<String, Object> after = afterIndex.get(domainCode);
            if (Objects.equals(before, after)) {
                continue;
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("domain_id", domainCode);
            change.put("before", before);
            change.put("after", after);
            domainDiffs.add(change);
        }
        if (!domainDiffs.isEmpty()) {
            diff.put("domain_states", domainDiffs);
        }
        return diff;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDomainStates(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    private Map<String, Map<String, Object>> indexByDomainCode(List<Map<String, Object>> states) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> state : states) {
            Object code = state.get("domain_id");
            if (code != null) {
                index.put(String.valueOf(code), state);
            }
        }
        return index;
    }
}
