package com.a05.aiinterview.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSyllabus {

    private String planningReasoning;
    private List<SyllabusDomain> domains;
    private List<SyllabusExperienceItem> experienceItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyllabusDomain {
        private Long domainId;
        private String domainCode;
        private String domainName;
        private List<String> focusPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyllabusExperienceItem {
        private String itemKey;
        private String itemType;
        private String itemName;
        private String resumeDescription;
        private List<String> techHooks;
    }
}
