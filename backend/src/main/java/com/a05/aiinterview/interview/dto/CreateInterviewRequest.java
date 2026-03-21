package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Request payload for creating an interview session.
 */
@Data
@Schema(description = "Create interview request")
public class CreateInterviewRequest {

    @NotBlank(message = "targetRole is required")
    @Schema(description = "Target role enum", example = "JAVA_BACKEND", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetRole;

    @NotBlank(message = "experienceLevel is required")
    @Schema(description = "Experience level enum", example = "SENIOR", requiredMode = Schema.RequiredMode.REQUIRED)
    private String experienceLevel;

    @NotBlank(message = "mode is required")
    @Schema(description = "Interview mode: practice / professional", example = "practice", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mode;

    @Schema(description = "Job description text", example = "Responsible for Spring Boot services")
    private String jobDescription;

    @Schema(description = "Resume id from resume center", example = "9001")
    private Long resumeId;

    @Schema(description = "Optional focus topics for practice mode")
    private String focusTopics;

    @Schema(description = "Whether to remember this setting", example = "true")
    private Boolean rememberSettings;

    @Schema(description = "Think time limit in seconds", example = "30")
    private Integer thinkTimeLimitSeconds;

    @Schema(description = "Answer time limit in seconds", example = "180")
    private Integer answerTimeLimitSeconds;

    @Schema(description = "Single-question mode max questions override (optional)", example = "1")
    private Integer maxQuestions;

    @Schema(description = "Single-question mode seed question stem (optional)")
    private String singleQuestionStem;

    @Schema(description = "Single-question mode question type (optional)", example = "PRINCIPLE")
    private String singleQuestionType;

    @Schema(description = "Single-question mode domain name (optional)", example = "前端基础")
    private String singleQuestionDomainName;

    @Schema(description = "Single-question mode expected key points (optional)")
    private List<String> singleQuestionExpectedPoints;

}
