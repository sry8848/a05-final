package com.a05.aiinterview.ai.prompt;

public class PromptTemplateParseException extends RuntimeException {

    public PromptTemplateParseException(String message) {
        super(message);
    }

    public PromptTemplateParseException(String message, Throwable cause) {
        super(message, cause);
    }
}