package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PlannerDomainNormalizationService {

    static final int MIN_PLANNER_DOMAINS = 5;
    static final int MAX_PLANNER_DOMAINS = 8;

    public NormalizationResult normalize(PlannerOutput output, List<PositionSkillDomain> allowedDomains) {
        PlannerOutput safeOutput = output != null ? output : PlannerOutput.builder().build();
        List<PositionSkillDomain> sortedAllowedDomains = sortAllowedDomains(allowedDomains);
        DomainLookup lookup = DomainLookup.from(sortedAllowedDomains);

        List<PlannerOutput.DomainPlan> rawDomains = safeOutput.getDomains() != null
                ? safeOutput.getDomains()
                : List.of();
        List<PlannerOutput.DomainPlan> normalizedDomains = new ArrayList<>();
        List<String> droppedDomains = new ArrayList<>();
        List<String> backfilledDomains = new ArrayList<>();
        LinkedHashSet<String> seenDomainCodes = new LinkedHashSet<>();

        for (PlannerOutput.DomainPlan rawDomain : rawDomains) {
            if (rawDomain == null || normalizedDomains.size() >= MAX_PLANNER_DOMAINS) {
                continue;
            }
            PositionSkillDomain matched = lookup.resolve(rawDomain);
            if (matched == null) {
                droppedDomains.add(toDomainSummary(rawDomain.getDomainCode(), rawDomain.getDomainName()));
                continue;
            }
            String canonicalCode = matched.getDomainCode();
            if (!seenDomainCodes.add(canonicalCode)) {
                continue;
            }
            normalizedDomains.add(PlannerOutput.DomainPlan.builder()
                    .domainCode(canonicalCode)
                    .domainName(matched.getDomainName())
                    .focusPoints(sanitizeFocusPoints(rawDomain.getFocusPoints()))
                    .build());
        }

        int minimumDomains = Math.min(MIN_PLANNER_DOMAINS, sortedAllowedDomains.size());
        if (normalizedDomains.size() < minimumDomains) {
            for (PositionSkillDomain allowedDomain : sortedAllowedDomains) {
                if (normalizedDomains.size() >= minimumDomains || normalizedDomains.size() >= MAX_PLANNER_DOMAINS) {
                    break;
                }
                if (!seenDomainCodes.add(allowedDomain.getDomainCode())) {
                    continue;
                }
                backfilledDomains.add(allowedDomain.getDomainCode());
                normalizedDomains.add(PlannerOutput.DomainPlan.builder()
                        .domainCode(allowedDomain.getDomainCode())
                        .domainName(allowedDomain.getDomainName())
                        .focusPoints(buildFallbackFocusPoints(allowedDomain.getDescription()))
                        .build());
            }
        }

        PlannerOutput normalizedOutput = PlannerOutput.builder()
                .planningReasoning(safeOutput.getPlanningReasoning())
                .domains(normalizedDomains)
                .experienceItems(safeOutput.getExperienceItems() != null ? safeOutput.getExperienceItems() : List.of())
                .build();
        return new NormalizationResult(
                normalizedOutput,
                droppedDomains,
                backfilledDomains,
                rawDomains.size(),
                normalizedDomains.size()
        );
    }

    private List<PositionSkillDomain> sortAllowedDomains(List<PositionSkillDomain> allowedDomains) {
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return List.of();
        }
        return allowedDomains.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(PositionSkillDomain::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(PositionSkillDomain::getDomainCode, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<String> sanitizeFocusPoints(List<String> focusPoints) {
        return PlannerFocusPointSupport.sanitizeFocusPoints(focusPoints);
    }

    private List<String> buildFallbackFocusPoints(String description) {
        return PlannerFocusPointSupport.buildFallbackFocusPoints(description);
    }

    private String toDomainSummary(String domainCode, String domainName) {
        return "domainCode=" + Objects.toString(domainCode, "")
                + ", domainName=" + Objects.toString(domainName, "");
    }

    private record DomainLookup(Map<String, PositionSkillDomain> byCode) {

        static DomainLookup from(List<PositionSkillDomain> domains) {
            Map<String, PositionSkillDomain> byCode = new LinkedHashMap<>();
            for (PositionSkillDomain domain : domains) {
                if (domain.getDomainCode() != null && !domain.getDomainCode().isBlank()) {
                    byCode.put(domain.getDomainCode(), domain);
                }
            }
            return new DomainLookup(byCode);
        }

        PositionSkillDomain resolve(PlannerOutput.DomainPlan plannerDomain) {
            String rawCode = plannerDomain.getDomainCode();
            return rawCode == null || rawCode.isBlank()
                    ? null
                    : byCode.get(rawCode.trim());
        }
    }

    public record NormalizationResult(PlannerOutput normalizedOutput,
                                      List<String> droppedDomains,
                                      List<String> backfilledDomains,
                                      int rawDomainsCount,
                                      int finalDomainsCount) {
    }
}
