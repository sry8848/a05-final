package com.a05.aiinterview.speech.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsrConstrainedCorrectionServiceTest {

    private final AsrConstrainedCorrectionService service = new AsrConstrainedCorrectionService();

    @Test
    void correct_shouldFixBackendTechnicalTermsWithoutChangingMeaning() {
        AsrConstrainedCorrectionService.CorrectionResult result = service.correct(
                "我主要做ja法后端，后端结果用ruedis做缓存。",
                new AsrConstrainedCorrectionService.CorrectionContext(
                        "backend",
                        "请介绍一下你的 Java 后端项目经历",
                        "熟悉 Java、Redis、Spring Boot"
                )
        );

        assertThat(result.correctedText()).isEqualTo("我主要做Java后端，后端接口用Redis做缓存。");
        assertThat(result.rawText()).isEqualTo("我主要做ja法后端，后端结果用ruedis做缓存。");
        assertThat(result.correctionApplied()).isTrue();
        assertThat(result.changeList()).extracting(AsrConstrainedCorrectionService.CorrectionChange::to)
                .contains("Java", "后端接口", "Redis");
    }

    @Test
    void correct_shouldFixFrontendTechnicalTerms() {
        AsrConstrainedCorrectionService.CorrectionResult result = service.correct(
                "我最近主要在做vew三和typescript项目。",
                new AsrConstrainedCorrectionService.CorrectionContext(
                        "frontend",
                        "你最近做过哪些前端项目？",
                        "熟悉 Vue 3、TypeScript、Vite"
                )
        );

        assertThat(result.correctedText()).isEqualTo("我最近主要在做Vue 3和TypeScript项目。");
        assertThat(result.correctionApplied()).isTrue();
    }

    @Test
    void correct_shouldKeepUncertainContentWhenNoHighConfidenceRuleExists() {
        AsrConstrainedCorrectionService.CorrectionResult result = service.correct(
                "我负责型货端开发与上线。",
                new AsrConstrainedCorrectionService.CorrectionContext(
                        "backend",
                        "说说你的项目经历",
                        "Java 后端开发"
                )
        );

        assertThat(result.correctedText()).isEqualTo("我负责型货端开发与上线。");
        assertThat(result.correctionApplied()).isFalse();
        assertThat(result.changeList()).isEmpty();
    }
}
