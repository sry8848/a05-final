package com.a05.aiinterview.ai.prompt;

import java.util.Map;

public interface PromptTemplateService {

    RenderedPrompt render(String promptCode, String promptVersion, Map<String, Object> variables);

    PromptTemplateMetadata loadMetadata(String promptCode);
}
