package com.a05.aiinterview.ai.config;

import com.a05.aiinterview.ai.prompt.PromptCode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Prompt version configuration for each prompt code.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.prompt.version")
public class PromptProperties {

    private String planner = "v2";
    private String questionGenerationStream = "v2";
    private String evaluationDecision = "v2";
    private String reportGeneration = "v1";
    private String introRewrite = "v2";
    private String questionDetailEvaluation = "v1";

    public String resolveVersion(String promptCode) {
        return resolveVersion(PromptCode.fromCode(promptCode));
    }

    public String resolveVersion(PromptCode promptCode) {
        return switch (promptCode) {
            case PLANNER -> planner;
            case QUESTION_GENERATION_STREAM -> questionGenerationStream;
            case EVALUATION_DECISION -> evaluationDecision;
            case REPORT_GENERATION -> reportGeneration;
            case INTRO_REWRITE -> introRewrite;
            case QUESTION_DETAIL_EVALUATION -> questionDetailEvaluation;
        };
    }

    public Map<PromptCode, String> asVersionMap() {
        Map<PromptCode, String> versions = new EnumMap<>(PromptCode.class);
        for (PromptCode promptCode : PromptCode.values()) {
            versions.put(promptCode, resolveVersion(promptCode));
        }
        return Map.copyOf(versions);
    }
}
