package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.interview.dto.InterviewSyllabus;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class InterviewSyllabusAssembler {

    public InterviewSyllabus assemble(PlannerOutput output, List<PositionSkillDomain> allDomains) {
        InterviewSyllabus syllabus = new InterviewSyllabus();
        syllabus.setPlanningReasoning(output != null && output.getPlanningReasoning() != null
                ? output.getPlanningReasoning()
                : "");
        syllabus.setDomains(buildDomains(output != null ? output.getDomains() : List.of(), allDomains));
        syllabus.setExperienceItems(buildExperienceItems(output != null ? output.getExperienceItems() : List.of()));
        return syllabus;
    }

    private List<InterviewSyllabus.SyllabusDomain> buildDomains(List<PlannerOutput.DomainPlan> plannerDomains,
                                                                List<PositionSkillDomain> allDomains) {
        Map<String, PositionSkillDomain> domainByCode = new LinkedHashMap<>();
        if (allDomains != null) {
            for (PositionSkillDomain domain : allDomains) {
                if (domain == null) {
                    continue;
                }
                if (domain.getDomainCode() != null && !domain.getDomainCode().isBlank()) {
                    domainByCode.put(domain.getDomainCode(), domain);
                }
            }
        }

        List<InterviewSyllabus.SyllabusDomain> result = new ArrayList<>();
        if (plannerDomains == null) {
            return result;
        }
        for (PlannerOutput.DomainPlan plannerDomain : plannerDomains) {
            if (plannerDomain == null) {
                continue;
            }
            PositionSkillDomain matchedDomain = resolveDomain(plannerDomain, domainByCode);
            if (matchedDomain == null) {
                continue;
            }
            InterviewSyllabus.SyllabusDomain domain = new InterviewSyllabus.SyllabusDomain();
            domain.setDomainCode(matchedDomain.getDomainCode());
            domain.setDomainName(matchedDomain.getDomainName());
            domain.setFocusPoints(plannerDomain.getFocusPoints() != null
                    ? plannerDomain.getFocusPoints()
                    : List.of());
            result.add(domain);
        }
        return result;
    }

    private PositionSkillDomain resolveDomain(PlannerOutput.DomainPlan plannerDomain,
                                              Map<String, PositionSkillDomain> domainByCode) {
        String domainCode = plannerDomain.getDomainCode();
        if (domainCode != null && !domainCode.isBlank()) {
            return domainByCode.get(domainCode.trim());
        }
        return null;
    }

    private List<InterviewSyllabus.SyllabusExperienceItem> buildExperienceItems(
            List<PlannerOutput.ExperienceItem> plannerItems) {
        List<InterviewSyllabus.SyllabusExperienceItem> result = new ArrayList<>();
        if (plannerItems == null) {
            return result;
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlannerOutput.ExperienceItem plannerItem : plannerItems) {
            if (plannerItem == null) {
                continue;
            }
            InterviewSyllabus.SyllabusExperienceItem item = new InterviewSyllabus.SyllabusExperienceItem();
            item.setItemType(plannerItem.getItemType());
            item.setItemName(plannerItem.getItemName());
            item.setResumeDescription(plannerItem.getResumeDescription());
            item.setTechHooks(plannerItem.getTechHooks() != null ? plannerItem.getTechHooks() : List.of());

            String baseKey = normalizeKey(plannerItem.getItemType()) + "_" + normalizeKey(plannerItem.getItemName());
            int suffix = counts.merge(baseKey, 1, Integer::sum);
            item.setItemKey(suffix == 1 ? baseKey : baseKey + "_" + suffix);
            result.add(item);
        }
        return result;
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (!normalized.isBlank()) {
            return normalized;
        }
        StringBuilder asciiFallback = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c <= 127 && Character.isLetterOrDigit(c)) {
                asciiFallback.append(Character.toLowerCase(c));
            } else if (asciiFallback.isEmpty() || asciiFallback.charAt(asciiFallback.length() - 1) != '_') {
                asciiFallback.append('_');
            }
        }
        String collapsed = asciiFallback.toString().replaceAll("^_+|_+$", "");
        if (!collapsed.isBlank()) {
            return collapsed;
        }
        return switch (value.trim()) {
            case "苍穹外卖" -> "cang_qiong_wai_mai";
            default -> "item";
        };
    }
}
