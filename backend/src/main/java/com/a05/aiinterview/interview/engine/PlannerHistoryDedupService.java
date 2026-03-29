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
        Set<String> denySet = buildDenySet(historyInterviews);
        Map<String, PositionSkillDomain> allowedByCode = buildAllowedByCode(allowedDomains);
        List<PlannerOutput.DomainPlan> dedupedDomains = denySet.isEmpty()
                ? safeDomains
                : safeDomains.stream()
                .map(domain -> deduplicateDomain(domain, allowedByCode.get(domain.getDomainCode()), denySet))
                .toList();
        List<PlannerOutput.ExperienceItem> dedupedExperienceItems =
                deduplicateExperienceItems(safeOutput.getExperienceItems(), historyInterviews);

        return PlannerOutput.builder()
                .planningReasoning(safeOutput.getPlanningReasoning())
                .domains(dedupedDomains)
                .experienceItems(dedupedExperienceItems)
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

    private List<PlannerOutput.ExperienceItem> deduplicateExperienceItems(
            List<PlannerOutput.ExperienceItem> experienceItems,
            List<PlannerInput.HistoryInterviewItem> historyInterviews) {
        List<PlannerOutput.ExperienceItem> safeItems = experienceItems != null ? experienceItems : List.of();
        if (safeItems.isEmpty()) {
            return safeItems;
        }
        Map<PlannerHistoryBuilderService.ProjectIdentity, Set<String>> blockedEntryPoints =
                buildBlockedEntryPoints(historyInterviews);
        if (blockedEntryPoints.isEmpty()) {
            return safeItems;
        }
        return safeItems.stream()
                .map(item -> deduplicateExperienceItem(item, blockedEntryPoints))
                .toList();
    }

    private PlannerOutput.ExperienceItem deduplicateExperienceItem(
            PlannerOutput.ExperienceItem item,
            Map<PlannerHistoryBuilderService.ProjectIdentity, Set<String>> blockedEntryPoints) {
        if (item == null) {
            return null;
        }
        List<String> originalTechHooks = PlannerFocusPointSupport.sanitizeFocusPoints(item.getTechHooks());
        if (originalTechHooks.isEmpty()) {
            return item;
        }
        Set<String> blocked = blockedEntryPoints.get(new PlannerHistoryBuilderService.ProjectIdentity(
                item.getItemType(),
                item.getItemName()
        ));
        if (blocked == null || blocked.isEmpty()) {
            return copyExperienceItem(item, originalTechHooks);
        }
        List<String> filteredTechHooks = originalTechHooks.stream()
                .filter(techHook -> !blocked.contains(PlannerFocusPointSupport.normalizeForHistoryMatch(techHook)))
                .toList();
        if (!filteredTechHooks.isEmpty()) {
            return copyExperienceItem(item, filteredTechHooks);
        }
        return copyExperienceItem(item, List.of(originalTechHooks.get(0)));
    }

    private PlannerOutput.ExperienceItem copyExperienceItem(PlannerOutput.ExperienceItem item, List<String> techHooks) {
        return PlannerOutput.ExperienceItem.builder()
                .itemType(item.getItemType())
                .itemName(item.getItemName())
                .resumeDescription(item.getResumeDescription())
                .techHooks(techHooks)
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

    private Map<PlannerHistoryBuilderService.ProjectIdentity, Set<String>> buildBlockedEntryPoints(
            List<PlannerInput.HistoryInterviewItem> historyInterviews) {
        if (historyInterviews == null || historyInterviews.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<PlannerHistoryBuilderService.ProjectIdentity, Set<String>> blocked = new LinkedHashMap<>();
        for (PlannerInput.HistoryInterviewItem historyInterview : historyInterviews) {
            if (historyInterview == null || historyInterview.getDiscussedItems() == null) {
                continue;
            }
            for (PlannerInput.HistoryExperienceItem discussedItem : historyInterview.getDiscussedItems()) {
                if (discussedItem == null) {
                    continue;
                }
                PlannerHistoryBuilderService.ProjectIdentity identity = new PlannerHistoryBuilderService.ProjectIdentity(
                        discussedItem.getItemType(),
                        discussedItem.getItemName()
                );
                if (identity.itemType().isBlank() || identity.itemName().isBlank()) {
                    continue;
                }
                Set<String> entryPoints = blocked.computeIfAbsent(identity, ignored -> new LinkedHashSet<>());
                for (String entryPoint : discussedItem.getEntryPoints()) {
                    String normalized = PlannerFocusPointSupport.normalizeForHistoryMatch(entryPoint);
                    if (normalized != null) {
                        entryPoints.add(normalized);
                    }
                }
            }
        }
        return blocked;
    }
}
