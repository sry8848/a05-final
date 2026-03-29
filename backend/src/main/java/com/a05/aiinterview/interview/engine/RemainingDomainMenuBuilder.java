package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.interview.entity.InterviewSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建理论题剩余待考察域菜单。
 */
@Component
public class RemainingDomainMenuBuilder {

    @SuppressWarnings("unchecked")
    public List<EvaluationDecisionInput.RemainingTargetDomain> build(InterviewSession session) {
        if (session == null) {
            return List.of();
        }
        List<EvaluationDecisionInput.RemainingTargetDomain> domains = new ArrayList<>();
        Map<String, String> statusByCode = extractDomainStatusMap(session.getStateLedgerJson());
        Object raw = session.getSyllabusJson() != null ? session.getSyllabusJson().get("domains") : null;
        if (raw instanceof List<?> domainList) {
            for (Object domainObj : domainList) {
                if (!(domainObj instanceof Map<?, ?> domain)) {
                    continue;
                }
                String domainCode = asString(domain.get("domainCode"));
                String internalStatus = statusByCode.getOrDefault(domainCode, "UNASKED");
                if ("COVERED".equalsIgnoreCase(internalStatus)) {
                    continue;
                }
                domains.add(EvaluationDecisionInput.RemainingTargetDomain.builder()
                        .domainCode(domainCode)
                        .domainName(asString(domain.get("domainName")))
                        .focusPoints(toStringList(domain.get("focusPoints")))
                        .build());
            }
        }
        return domains;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractDomainStatusMap(Map<String, Object> stateLedgerJson) {
        Map<String, String> result = new LinkedHashMap<>();
        if (stateLedgerJson == null) {
            return result;
        }
        Object raw = stateLedgerJson.get("domain_states");
        if (!(raw instanceof List<?> states)) {
            return result;
        }
        for (Object stateObj : states) {
            if (!(stateObj instanceof Map<?, ?> state)) {
                continue;
            }
            result.put(asString(state.get("domainCode")), asString(state.get("status")));
        }
        return result;
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

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
