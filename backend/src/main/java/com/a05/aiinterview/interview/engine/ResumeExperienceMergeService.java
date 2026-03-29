package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class ResumeExperienceMergeService {

    private static final Pattern ITEM_HEADER_PATTERN = Pattern.compile("^(.+?)\\s*[|｜]\\s*.+$");
    private static final Pattern DATE_HINT_PATTERN = Pattern.compile("(19|20)\\d{2}[./-]\\d{1,2}.*");
    private static final List<String> GENERIC_HOOK_PREFIXES = List.of("项目描述", "项目简介", "项目背景", "描述", "背景");

    public PlannerOutput mergeIntoPlannerOutput(String resumeText, PlannerOutput output) {
        PlannerOutput safeOutput = output != null ? output : PlannerOutput.builder().build();
        return PlannerOutput.builder()
                .planningReasoning(safeOutput.getPlanningReasoning())
                .domains(safeOutput.getDomains() != null ? safeOutput.getDomains() : List.of())
                .experienceItems(merge(resumeText, safeOutput.getExperienceItems()))
                .build();
    }

    public List<PlannerOutput.ExperienceItem> merge(String resumeText, List<PlannerOutput.ExperienceItem> plannerItems) {
        List<PlannerOutput.ExperienceItem> safePlannerItems = plannerItems != null ? plannerItems : List.of();
        List<PlannerOutput.ExperienceItem> extractedResumeItems = extractResumeExperienceItems(resumeText);
        if (extractedResumeItems.isEmpty()) {
            return safePlannerItems;
        }

        Map<String, PlannerOutput.ExperienceItem> extractedByName = new LinkedHashMap<>();
        for (PlannerOutput.ExperienceItem extractedResumeItem : extractedResumeItems) {
            extractedByName.put(normalizeItemName(extractedResumeItem.getItemName()), extractedResumeItem);
        }

        List<PlannerOutput.ExperienceItem> merged = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (PlannerOutput.ExperienceItem plannerItem : safePlannerItems) {
            if (plannerItem == null) {
                continue;
            }
            String key = normalizeItemName(plannerItem.getItemName());
            PlannerOutput.ExperienceItem extracted = extractedByName.get(key);
            if (extracted != null) {
                merged.add(mergePlannerWithResume(plannerItem, extracted));
                if (!key.isBlank()) {
                    seen.add(key);
                }
            }
        }

        for (PlannerOutput.ExperienceItem extractedResumeItem : extractedResumeItems) {
            String key = normalizeItemName(extractedResumeItem.getItemName());
            if (!key.isBlank() && seen.contains(key)) {
                continue;
            }
            merged.add(extractedResumeItem);
        }
        return merged;
    }

    private PlannerOutput.ExperienceItem mergePlannerWithResume(PlannerOutput.ExperienceItem plannerItem,
                                                                PlannerOutput.ExperienceItem extractedResumeItem) {
        if (plannerItem == null) {
            return extractedResumeItem;
        }
        if (extractedResumeItem == null) {
            return sanitize(plannerItem);
        }
        List<String> plannerHooks = sanitizeHooks(plannerItem.getTechHooks());
        List<String> extractedHooks = sanitizeHooks(extractedResumeItem.getTechHooks());
        return PlannerOutput.ExperienceItem.builder()
                .itemType(firstNonBlank(plannerItem.getItemType(), extractedResumeItem.getItemType(), "PROJECT"))
                .itemName(firstNonBlank(plannerItem.getItemName(), extractedResumeItem.getItemName()))
                .resumeDescription(firstNonBlank(plannerItem.getResumeDescription(), extractedResumeItem.getResumeDescription()))
                .techHooks(!plannerHooks.isEmpty() ? plannerHooks : extractedHooks)
                .build();
    }

    private PlannerOutput.ExperienceItem sanitize(PlannerOutput.ExperienceItem item) {
        return PlannerOutput.ExperienceItem.builder()
                .itemType(firstNonBlank(item.getItemType(), "PROJECT"))
                .itemName(firstNonBlank(item.getItemName(), ""))
                .resumeDescription(firstNonBlank(item.getResumeDescription(), ""))
                .techHooks(sanitizeHooks(item.getTechHooks()))
                .build();
    }

    private List<PlannerOutput.ExperienceItem> extractResumeExperienceItems(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return List.of();
        }
        List<ExtractedItem> extractedItems = new ArrayList<>();
        String sectionType = "";
        ExtractedItem current = null;
        for (String rawLine : resumeText.split("\\r?\\n")) {
            String line = trim(rawLine);
            if (line.isBlank()) {
                continue;
            }
            String nextSectionType = detectSectionType(line);
            if (!nextSectionType.isBlank()) {
                if (current != null) {
                    extractedItems.add(current);
                    current = null;
                }
                sectionType = nextSectionType;
                continue;
            }
            if (isTopLevelHeading(line)) {
                if (current != null) {
                    extractedItems.add(current);
                    current = null;
                }
                sectionType = "";
                continue;
            }
            if (sectionType.isBlank()) {
                continue;
            }
            if (isExperienceHeader(line)) {
                if (current != null) {
                    extractedItems.add(current);
                }
                current = new ExtractedItem(sectionType, extractItemName(line));
                continue;
            }
            if (current != null) {
                current.detailLines.add(line);
            }
        }
        if (current != null) {
            extractedItems.add(current);
        }

        return extractedItems.stream()
                .map(this::toPlannerExperienceItem)
                .filter(Objects::nonNull)
                .toList();
    }

    private PlannerOutput.ExperienceItem toPlannerExperienceItem(ExtractedItem item) {
        if (item == null || item.itemName.isBlank()) {
            return null;
        }
        List<String> detailLines = item.detailLines.stream()
                .map(this::trim)
                .filter(line -> !line.isBlank())
                .toList();
        String resumeDescription = String.join(" ", detailLines);
        List<String> techHooks = buildTechHooks(detailLines);
        return PlannerOutput.ExperienceItem.builder()
                .itemType(item.itemType)
                .itemName(item.itemName)
                .resumeDescription(resumeDescription)
                .techHooks(techHooks)
                .build();
    }

    private List<String> buildTechHooks(List<String> detailLines) {
        LinkedHashSet<String> hooks = new LinkedHashSet<>();
        for (String detailLine : detailLines) {
            String line = trim(detailLine);
            if (line.isBlank()) {
                continue;
            }
            String hook = extractHook(line);
            if (!hook.isBlank()) {
                hooks.add(hook);
            }
            if (hooks.size() >= 3) {
                break;
            }
        }
        return new ArrayList<>(hooks);
    }

    private String extractHook(String line) {
        int colonIndex = findColonIndex(line);
        if (colonIndex > 0) {
            String prefix = trim(line.substring(0, colonIndex));
            if (isGenericHookPrefix(prefix)) {
                return "";
            }
            return prefix;
        }
        return line.length() <= 24 ? line : "";
    }

    private boolean isGenericHookPrefix(String prefix) {
        return GENERIC_HOOK_PREFIXES.stream().anyMatch(prefix::equals);
    }

    private int findColonIndex(String line) {
        int zh = line.indexOf('：');
        int en = line.indexOf(':');
        if (zh < 0) {
            return en;
        }
        if (en < 0) {
            return zh;
        }
        return Math.min(zh, en);
    }

    private boolean isExperienceHeader(String line) {
        return ITEM_HEADER_PATTERN.matcher(line).matches() && DATE_HINT_PATTERN.matcher(line).find();
    }

    private String extractItemName(String line) {
        int pipeIndex = Math.max(line.indexOf('|'), line.indexOf('｜'));
        if (pipeIndex < 0) {
            return trim(line);
        }
        return trim(line.substring(0, pipeIndex));
    }

    private String detectSectionType(String line) {
        if (line.contains("项目经历")) {
            return "PROJECT";
        }
        if (line.contains("实习经历")) {
            return "INTERNSHIP";
        }
        return "";
    }

    private boolean isTopLevelHeading(String line) {
        return line.contains("教育背景")
                || line.contains("专业技能")
                || line.contains("校园经历")
                || line.contains("荣誉")
                || line.contains("获奖")
                || line.contains("自我评价")
                || line.contains("证书")
                || line.contains("技能")
                || line.contains("竞赛");
    }

    private List<String> sanitizeHooks(List<String> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String hook : hooks) {
            String trimmed = trim(hook);
            if (!trimmed.isBlank()) {
                sanitized.add(trimmed);
            }
        }
        return new ArrayList<>(sanitized);
    }

    private String normalizeItemName(String itemName) {
        return trim(itemName)
                .replace('\u3000', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String trimmed = trim(value);
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return "";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ExtractedItem {
        private final String itemType;
        private final String itemName;
        private final List<String> detailLines = new ArrayList<>();

        private ExtractedItem(String itemType, String itemName) {
            this.itemType = itemType;
            this.itemName = itemName == null ? "" : itemName.trim();
        }
    }
}
