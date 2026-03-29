package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerOutput {

    private String planningReasoning;
    private List<DomainPlan> domains;
    private List<ExperienceItem> experienceItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainPlan {
        private String domainCode;
        private String domainName;
        private List<String> focusPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceItem {
        private String itemType;
        private String itemName;
        private String resumeDescription;
        private List<String> techHooks;
    }
}
