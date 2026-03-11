package com.a05.aiinterview.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Prompt version configuration for each prompt code.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.prompt.version")
public class PromptProperties {

    private String planner = "v1";
    private String questionGeneration = "v1";
    private String questionGenerationStream = "v1";
    private String evaluationDecision = "v1";
    private String reportGeneration = "v1";
    private String introRewrite = "v1";

    public String resolveVersion(String promptCode) {
        return switch (promptCode) {
            case "planner" -> planner;
            case "question_generation" -> questionGeneration;
            case "question_generation_stream" -> questionGenerationStream;
            case "evaluation_decision" -> evaluationDecision;
            case "report_generation" -> reportGeneration;
            case "intro_rewrite" -> introRewrite;
            default -> throw new IllegalArgumentException("Unsupported promptCode: " + promptCode);
        };
    }
}