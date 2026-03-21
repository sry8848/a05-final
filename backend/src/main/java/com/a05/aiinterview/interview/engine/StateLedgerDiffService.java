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
 * 生成账本审计 diff，供 attempt.evaluationJson 与调试日志使用。
 */
@Component
public class StateLedgerDiffService {

    public Map<String, Object> diff(Map<String, Object> oldLedger, Map<String, Object> newLedger) {
        Map<String, Object> diff = new LinkedHashMap<>();
        copyIfChanged(diff, "asked_total", oldLedger, newLedger);
        copyIfChanged(diff, "last_attempt_id", oldLedger, newLedger);
        copyIfChanged(diff, "active_item_key", oldLedger, newLedger);
        copyIfChanged(diff, "active_item_type", oldLedger, newLedger);
        copyIfChanged(diff, "active_item_name", oldLedger, newLedger);
        copyIfChanged(diff, "current_focus", oldLedger, newLedger);
        copyIfChanged(diff, "covered_domains", oldLedger, newLedger);
        copyIfChanged(diff, "covered_points", oldLedger, newLedger);
        copyIfChanged(diff, "recent_question_families", oldLedger, newLedger);

        List<Map<String, Object>> beforeStates = extractDomainStates(oldLedger.get("domain_states"));
        List<Map<String, Object>> afterStates = extractDomainStates(newLedger.get("domain_states"));
        Map<String, Map<String, Object>> beforeIndex = indexByDomainCode(beforeStates);
        Map<String, Map<String, Object>> afterIndex = indexByDomainCode(afterStates);
        Set<String> changedDomainCodes = new LinkedHashSet<>();
        changedDomainCodes.addAll(beforeIndex.keySet());
        changedDomainCodes.addAll(afterIndex.keySet());

        List<Map<String, Object>> stateChanges = new ArrayList<>();
        for (String domainCode : changedDomainCodes) {
            Map<String, Object> before = beforeIndex.get(domainCode);
            Map<String, Object> after = afterIndex.get(domainCode);
            if (Objects.equals(before, after)) {
                continue;
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("domainCode", domainCode);
            change.put("before", before);
            change.put("after", after);
            stateChanges.add(change);
        }
        if (!stateChanges.isEmpty()) {
            diff.put("domain_states", stateChanges);
        }
        return diff;
    }

    private void copyIfChanged(Map<String, Object> diff,
                               String key,
                               Map<String, Object> oldLedger,
                               Map<String, Object> newLedger) {
        Object oldValue = oldLedger != null ? oldLedger.get(key) : null;
        Object newValue = newLedger != null ? newLedger.get(key) : null;
        if (!Objects.equals(oldValue, newValue)) {
            diff.put(key, newValue);
        }
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
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> state : states) {
            Object domainCode = state.get("domainCode");
            if (domainCode != null) {
                result.put(String.valueOf(domainCode), state);
            }
        }
        return result;
    }
}
