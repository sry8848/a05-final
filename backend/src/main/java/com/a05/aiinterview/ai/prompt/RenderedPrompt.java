package com.a05.aiinterview.ai.prompt;

import java.util.Set;

public class RenderedPrompt {

    private final String promptCode;
    private final String promptVersion;
    private final String systemPrompt;
    private final String userPrompt;
    private final Set<String> usedVariables;

    public RenderedPrompt(String promptCode,
                          String promptVersion,
                          String systemPrompt,
                          String userPrompt,
                          Set<String> usedVariables) {
        this.promptCode = promptCode;
        this.promptVersion = promptVersion;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.usedVariables = usedVariables;
    }

    public String getPromptCode() {
        return promptCode;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public Set<String> getUsedVariables() {
        return usedVariables;
    }
}