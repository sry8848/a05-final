package com.a05.aiinterview.ai.config;

import com.a05.aiinterview.ai.prompt.PromptCode;
import com.a05.aiinterview.ai.prompt.PromptTemplateMetadata;
import com.a05.aiinterview.ai.prompt.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Validates prompt version configuration against template metadata during startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptConfigurationValidator implements SmartInitializingSingleton {

    private final PromptProperties promptProperties;
    private final PromptTemplateService promptTemplateService;

    @Override
    public void afterSingletonsInstantiated() {
        for (PromptCode promptCode : PromptCode.values()) {
            validate(promptCode);
        }
    }

    private void validate(PromptCode promptCode) {
        String configuredVersion = promptProperties.resolveVersion(promptCode);
        PromptTemplateMetadata metadata = promptTemplateService.loadMetadata(promptCode.code());
        if (!promptCode.code().equals(metadata.promptCode())) {
            throw new IllegalStateException("Prompt 模板元数据不匹配: configuredCode=" + promptCode.code()
                    + ", templateCode=" + metadata.promptCode()
                    + ", source=" + metadata.sourcePath());
        }
        if (!configuredVersion.equals(metadata.promptVersion())) {
            throw new IllegalStateException("Prompt 配置版本不匹配: promptCode=" + promptCode.code()
                    + ", configured=" + configuredVersion
                    + ", template=" + metadata.promptVersion()
                    + ", source=" + metadata.sourcePath());
        }
        log.info("Prompt 配置校验通过, promptCode={}, promptVersion={}, source={}",
                promptCode.code(), configuredVersion, metadata.sourcePath());
    }
}
