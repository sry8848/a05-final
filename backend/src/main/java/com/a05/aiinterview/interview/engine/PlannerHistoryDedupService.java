package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class PlannerHistoryDedupService {

    public PlannerOutput deduplicate(PlannerOutput output,
                                     List<PositionSkillDomain> allowedDomains,
                                     List<PlannerInput.HistoryInterviewItem> historyInterviews) {
        PlannerOutput safeOutput = output != null ? output : PlannerOutput.builder().build();
        List<PlannerOutput.DomainPlan> safeDomains = safeOutput.getDomains() != null ? safeOutput.getDomains() : List.of();
        if (safeDomains.isEmpty()) {
            return safeOutput;
        }

        Set<String> denySet = buildDenySet(historyInterviews);
        if (denySet.isEmpty()) {
            return safeOutput;
        }

        Map<String, PositionSkillDomain> allowedByCode = buildAllowedByCode(allowedDomains);
        List<PlannerOutput.DomainPlan> dedupedDomains = safeDomains.stream()
                .map(domain -> deduplicateDomain(domain, allowedByCode.get(domain.getDomainCode()), denySet))
                .toList();

        return PlannerOutput.builder()
                .planningReasoning(safeOutput.getPlanningReasoning())
                .domains(dedupedDomains)
                .experienceItems(safeOutput.getExperienceItems() != null ? safeOutput.getExperienceItems() : List.of())
                .build();
    }

    private PlannerOutput.DomainPlan deduplicateDomain(PlannerOutput.DomainPlan domain,
                                                       PositionSkillDomain allowedDomain,
                                                       Set<String> denySet) {
        List<String> originalFocusPoints = PlannerFocusPointSupport.sanitizeFocusPoints(domain.getFocusPoints());
        List<String> filteredFocusPoints = originalFocusPoints.stream()
                .filter(focusPoint -> !denySet.contains(PlannerFocusPointSupport.normalizeForHistoryMatch(focusPoint)))
                .toList();
        if (!filteredFocusPoints.isEmpty()) {
            return copyDomain(domain, filteredFocusPoints);
        }

        List<String> fallbackFocusPoints = PlannerFocusPointSupport.buildFallbackFocusPoints(
                        allowedDomain == null ? null : allowedDomain.getDescription())
                .stream()
                .filter(focusPoint -> !denySet.contains(PlannerFocusPointSupport.normalizeForHistoryMatch(focusPoint)))
                .distinct()
                .toList();
        if (!fallbackFocusPoints.isEmpty()) {
            return copyDomain(domain, fallbackFocusPoints);
        }
        if (!originalFocusPoints.isEmpty()) {
            return copyDomain(domain, List.of(originalFocusPoints.get(0)));
        }
        return copyDomain(domain, List.of());
    }

    private PlannerOutput.DomainPlan copyDomain(PlannerOutput.DomainPlan domain, List<String> focusPoints) {
        return PlannerOutput.DomainPlan.builder()
                .domainCode(domain.getDomainCode())
                .domainName(domain.getDomainName())
                .focusPoints(focusPoints)
                .build();
    }

    private Set<String> buildDenySet(List<PlannerInput.HistoryInterviewItem> historyInterviews) {
        if (historyInterviews == null || historyInterviews.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> denySet = new LinkedHashSet<>();
        for (PlannerInput.HistoryInterviewItem historyInterview : historyInterviews) {
            if (historyInterview == null || historyInterview.getCoveredKnowledgePoints() == null) {
                continue;
            }
            for (String coveredKnowledgePoint : historyInterview.getCoveredKnowledgePoints()) {
                String normalized = PlannerFocusPointSupport.normalizeForHistoryMatch(coveredKnowledgePoint);
                if (normalized != null) {
                    denySet.add(normalized);
                }
            }
        }
        return denySet;
    }

    private Map<String, PositionSkillDomain> buildAllowedByCode(List<PositionSkillDomain> allowedDomains) {
        Map<String, PositionSkillDomain> allowedByCode = new LinkedHashMap<>();
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return allowedByCode;
        }
        for (PositionSkillDomain allowedDomain : allowedDomains) {
            if (allowedDomain == null || allowedDomain.getDomainCode() == null || allowedDomain.getDomainCode().isBlank()) {
                continue;
            }
            allowedByCode.put(allowedDomain.getDomainCode(), allowedDomain);
        }
        return allowedByCode;
    }
}
