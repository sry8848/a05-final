package com.a05.aiinterview.ai.config;

import com.a05.aiinterview.ai.prompt.ClasspathPromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Prompt configuration validator tests")
class PromptConfigurationValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("startup should succeed when configured versions match template versions")
    void startup_shouldSucceedWhenConfiguredVersionsMatch() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PromptConfigurationValidator.class);
        });
    }

    @Test
    @DisplayName("startup should fail fast when prompt version mismatches template metadata")
    void startup_shouldFailFastWhenPromptVersionMismatchesTemplateMetadata() {
        contextRunner
                .withPropertyValues("ai.prompt.version.intro-rewrite=v999")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("intro_rewrite")
                            .hasMessageContaining("configured=v999")
                            .hasMessageContaining("template=v2");
                });
    }

    @Configuration
    @EnableConfigurationProperties(PromptProperties.class)
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ClasspathPromptTemplateService promptTemplateService(ObjectMapper objectMapper) {
            return new ClasspathPromptTemplateService(objectMapper);
        }

        @Bean
        PromptConfigurationValidator promptConfigurationValidator(
                PromptProperties promptProperties,
                ClasspathPromptTemplateService promptTemplateService) {
            return new PromptConfigurationValidator(promptProperties, promptTemplateService);
        }
    }
}
