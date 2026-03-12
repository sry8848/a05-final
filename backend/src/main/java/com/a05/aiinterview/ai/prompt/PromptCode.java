package com.a05.aiinterview.ai.prompt;

import java.util.Arrays;

/**
 * Supported prompt codes in the backend.
 */
public enum PromptCode {

    PLANNER("planner"),
    QUESTION_GENERATION("question_generation"),
    QUESTION_GENERATION_STREAM("question_generation_stream"),
    EVALUATION_DECISION("evaluation_decision"),
    REPORT_GENERATION("report_generation"),
    INTRO_REWRITE("intro_rewrite");

    private final String code;

    PromptCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PromptCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported promptCode: " + code));
    }
}
