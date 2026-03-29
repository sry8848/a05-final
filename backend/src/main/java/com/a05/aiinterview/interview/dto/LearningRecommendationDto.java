package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Learning recommendations response for report page.
 */
@Data
@Schema(description = "Learning recommendations")
public class LearningRecommendationDto {

    @Schema(description = "Interview session id", example = "2001")
    private Long sessionId;

    @Schema(description = "Recommendation status: generating / ready / disabled", example = "ready")
    private String recommendationStatus;

    @Schema(description = "Generated at")
    private LocalDateTime generatedAt;

    @Schema(description = "Recommendation sections")
    private List<Section> sections = new ArrayList<>();

    public static LearningRecommendationDto generating(Long sessionId) {
        LearningRecommendationDto dto = new LearningRecommendationDto();
        dto.setSessionId(sessionId);
        dto.setRecommendationStatus("generating");
        dto.setSections(List.of());
        return dto;
    }

    public static LearningRecommendationDto disabled(Long sessionId) {
        LearningRecommendationDto dto = new LearningRecommendationDto();
        dto.setSessionId(sessionId);
        dto.setRecommendationStatus("disabled");
        dto.setSections(List.of());
        return dto;
    }

    public static LearningRecommendationDto ready(Long sessionId, List<Section> sections) {
        LearningRecommendationDto dto = new LearningRecommendationDto();
        dto.setSessionId(sessionId);
        dto.setRecommendationStatus("ready");
        dto.setGeneratedAt(LocalDateTime.now());
        dto.setSections(sections == null ? List.of() : sections);
        return dto;
    }

    @Data
    @Schema(description = "Recommendation section")
    public static class Section {

        @Schema(description = "Section key", example = "immediate")
        private String sectionKey;

        @Schema(description = "Section title", example = "立即补强")
        private String sectionTitle;

        @Schema(description = "Section items")
        private List<Item> items = new ArrayList<>();
    }

    @Data
    @Schema(description = "Recommendation item")
    public static class Item {

        @Schema(description = "Item id", example = "immediate-1")
        private String itemId;

        @Schema(description = "Title")
        private String title;

        @Schema(description = "Recommendation reason")
        private String reason;

        @Schema(description = "Resource type", example = "practice")
        private String resourceType;

        @Schema(description = "Domain code", example = "java_concurrency")
        private String domainCode;

        @Schema(description = "Domain name", example = "Java 并发")
        private String domainName;

        @Schema(description = "Estimated minutes", example = "30")
        private Integer estimatedMinutes;

        @Schema(description = "Resource link")
        private String link;
    }
}
