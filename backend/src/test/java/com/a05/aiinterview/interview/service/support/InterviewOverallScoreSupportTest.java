package com.a05.aiinterview.interview.service.support;

import com.a05.aiinterview.ai.dto.ReportGenerationOutput;
import com.a05.aiinterview.common.dto.RadarDimensionScoreDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterviewOverallScoreSupportTest {

    @Test
    void resolveOverallScore_shouldUseFixedWeightsWhenAllFiveDimensionsExist() {
        List<RadarDimensionScoreDto> dimensions = List.of(
                RadarDimensionScoreDto.builder().dimensionKey("fundamentals").score(BigDecimal.valueOf(80)).build(),
                RadarDimensionScoreDto.builder().dimensionKey("engineering_practice").score(BigDecimal.valueOf(70)).build(),
                RadarDimensionScoreDto.builder().dimensionKey("scenario_tradeoff").score(BigDecimal.valueOf(60)).build(),
                RadarDimensionScoreDto.builder().dimensionKey("debugging").score(BigDecimal.valueOf(90)).build(),
                RadarDimensionScoreDto.builder().dimensionKey("communication").score(BigDecimal.valueOf(100)).build()
        );

        BigDecimal effective = InterviewOverallScoreSupport.resolveOverallScore(
                BigDecimal.valueOf(61.0),
                Map.of("dimensions", InterviewOverallScoreSupport.toDimensionMaps(dimensions))
        );

        assertEquals(BigDecimal.valueOf(79.0), effective);
    }

    @Test
    void resolveOverallScore_shouldFallbackWhenAnyDimensionIsMissing() {
        BigDecimal effective = InterviewOverallScoreSupport.resolveOverallScore(
                BigDecimal.valueOf(66.5),
                Map.of("dimensions", List.of(
                        Map.of("dimensionKey", "fundamentals", "score", 80),
                        Map.of("dimensionKey", "communication", "score", 90)
                ))
        );

        assertEquals(BigDecimal.valueOf(66.5), effective);
    }

    @Test
    void resolveOverallScoreFromOutput_shouldUseRadarBeforePersistingNewReport() {
        ReportGenerationOutput output = ReportGenerationOutput.builder()
                .overallScore(BigDecimal.valueOf(10))
                .comprehensiveRadarScores(List.of(
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("fundamentals").dimensionName("基础原理掌握").score(BigDecimal.valueOf(80)).build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("engineering_practice").dimensionName("工程实践与项目落地").score(BigDecimal.valueOf(70)).build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("scenario_tradeoff").dimensionName("场景分析与方案取舍").score(BigDecimal.valueOf(60)).build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("debugging").dimensionName("问题定位与排查思路").score(BigDecimal.valueOf(90)).build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("communication").dimensionName("沟通表达与结构化呈现").score(BigDecimal.valueOf(100)).build()
                ))
                .build();

        assertEquals(BigDecimal.valueOf(79.0), InterviewOverallScoreSupport.resolveOverallScore(output));
    }
}
