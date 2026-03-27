package com.a05.aiinterview;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationProfileConfigTest {

    @Test
    void sharedConfigShouldNotHardcodeActiveProfile() throws IOException {
        String yaml = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertFalse(
                yaml.contains("active: local"),
                "application.yml should not hardcode spring.profiles.active; IDEA should select local explicitly"
        );
    }

    @Test
    void sharedConfigShouldPreferBailianApiKeyAndCompatibleDefaults() throws IOException {
        String yaml = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(
                yaml.contains("${AI_BAILIAN_API_KEY:${OPENAI_API_KEY:sk-mock}}"),
                "application.yml should prefer AI_BAILIAN_API_KEY and fall back to OPENAI_API_KEY"
        );
        assertTrue(
                yaml.contains("${OPENAI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode}"),
                "application.yml should default to DashScope compatible-mode base URL"
        );
        assertTrue(
                yaml.contains("${OPENAI_MODEL:qwen-plus}"),
                "application.yml should default to qwen-plus for shared config"
        );
    }
}
