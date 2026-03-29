package com.a05.aiinterview.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PlannerInput {

    private Long interviewId;
    private String positionCode;
    private String positionName;
    private String experienceLevel;
    private String roundType;
    private String mode;
    private String jobDescription;
    private String resumeText;
    private String focusTopics;
    private List<DomainInfo> domains;
    private List<HistoryInterviewItem> historyInterviews;

    @Data
    @Builder
    public static class DomainInfo {
        private String domainCode;
        private String domainName;
    }

    @Data
    @Builder
    public static class HistoryInterviewItem {
        private String roundType;
        private String interviewAt;
        private List<String> coveredKnowledgePoints;
        private List<HistoryExperienceItem> discussedItems;
        private List<String> strongPoints;
        private List<String> weakPoints;
    }

    @Data
    @Builder
    public static class HistoryExperienceItem {
        private String itemType;
        private String itemName;
        private List<String> entryPoints;
    }
}
