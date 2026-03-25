package com.a05.aiinterview.interview.service.support;

import com.a05.aiinterview.ai.dto.ReportGenerationOutput;
import com.a05.aiinterview.common.dto.RadarDimensionScoreDto;
import com.a05.aiinterview.interview.entity.InterviewReport;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试综合分统一口径支持。
 */
public final class InterviewOverallScoreSupport {

    private static final String KEY_FUNDAMENTALS = "fundamentals";
    private static final String KEY_ENGINEERING_PRACTICE = "engineering_practice";
    private static final String KEY_SCENARIO_TRADEOFF = "scenario_tradeoff";
    private static final String KEY_DEBUGGING = "debugging";
    private static final String KEY_COMMUNICATION = "communication";

    private static final List<String> REQUIRED_KEYS = List.of(
            KEY_FUNDAMENTALS,
            KEY_ENGINEERING_PRACTICE,
            KEY_SCENARIO_TRADEOFF,
            KEY_DEBUGGING,
            KEY_COMMUNICATION
    );

    private static final BigDecimal WEIGHT_FUNDAMENTALS = new BigDecimal("0.25");
    private static final BigDecimal WEIGHT_ENGINEERING_PRACTICE = new BigDecimal("0.20");
    private static final BigDecimal WEIGHT_SCENARIO_TRADEOFF = new BigDecimal("0.20");
    private static final BigDecimal WEIGHT_DEBUGGING = new BigDecimal("0.20");
    private static final BigDecimal WEIGHT_COMMUNICATION = new BigDecimal("0.15");

    private InterviewOverallScoreSupport() {
    }

    public static BigDecimal resolveOverallScore(InterviewReport report) {
        if (report == null) {
            return null;
        }
        return resolveOverallScore(report.getOverallScore(), report.getComprehensiveRadarScores());
    }

    public static BigDecimal resolveOverallScore(ReportGenerationOutput output) {
        if (output == null) {
            return null;
        }
        BigDecimal calculated = calculateWeightedScore(extractDimensions(output.getComprehensiveRadarScores()));
        return calculated != null ? calculated : output.getOverallScore();
    }

    public static BigDecimal resolveOverallScore(BigDecimal storedScore, Map<String, Object> radarScoreMap) {
        BigDecimal calculated = calculateWeightedScore(extractDimensions(radarScoreMap));
        return calculated != null ? calculated : storedScore;
    }

    public static Map<String, Object> toRadarScoreMap(
            List<ReportGenerationOutput.ComprehensiveRadarScore> radarScores) {
        if (radarScores == null) {
            return null;
        }
        Map<String, Object> radarScoreMap = new LinkedHashMap<>();
        radarScoreMap.put("dimensions", radarScores.stream().map(item -> {
            Map<String, Object> dimension = new LinkedHashMap<>();
            dimension.put("dimensionKey", item.getDimensionKey());
            dimension.put("dimensionName", item.getDimensionName());
            dimension.put("score", item.getScore());
            return dimension;
        }).toList());
        return radarScoreMap;
    }

    public static List<Map<String, Object>> toDimensionMaps(List<RadarDimensionScoreDto> dimensions) {
        if (dimensions == null) {
            return List.of();
        }
        return dimensions.stream().map(item -> {
            Map<String, Object> dimension = new LinkedHashMap<>();
            dimension.put("dimensionKey", item.getDimensionKey());
            dimension.put("dimensionName", item.getDimensionName());
            dimension.put("score", item.getScore());
            return dimension;
        }).toList();
    }

    public static List<RadarDimensionScoreDto> extractDimensions(Map<String, Object> radarScoreMap) {
        if (radarScoreMap == null) {
            return List.of();
        }
        Object dimensionsObj = radarScoreMap.get("dimensions");
        if (!(dimensionsObj instanceof List<?> dimensions)) {
            return List.of();
        }
        return dimensions.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> RadarDimensionScoreDto.builder()
                        .dimensionKey(toStr(item.get("dimensionKey")))
                        .dimensionName(toStr(item.get("dimensionName")))
                        .score(toDecimal(item.get("score")))
                        .build())
                .toList();
    }

    private static List<RadarDimensionScoreDto> extractDimensions(
            List<ReportGenerationOutput.ComprehensiveRadarScore> radarScores) {
        if (radarScores == null) {
            return List.of();
        }
        return radarScores.stream()
                .map(item -> RadarDimensionScoreDto.builder()
                        .dimensionKey(item.getDimensionKey())
                        .dimensionName(item.getDimensionName())
                        .score(item.getScore())
                        .build())
                .toList();
    }

    private static BigDecimal calculateWeightedScore(List<RadarDimensionScoreDto> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return null;
        }

        Map<String, BigDecimal> scoreMap = new HashMap<>();
        for (RadarDimensionScoreDto dimension : dimensions) {
            if (dimension == null || !StringUtils.hasText(dimension.getDimensionKey()) || dimension.getScore() == null) {
                continue;
            }
            scoreMap.put(dimension.getDimensionKey(), dimension.getScore());
        }
        if (!scoreMap.keySet().containsAll(REQUIRED_KEYS)) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        total = total.add(scoreMap.get(KEY_FUNDAMENTALS).multiply(WEIGHT_FUNDAMENTALS));
        total = total.add(scoreMap.get(KEY_ENGINEERING_PRACTICE).multiply(WEIGHT_ENGINEERING_PRACTICE));
        total = total.add(scoreMap.get(KEY_SCENARIO_TRADEOFF).multiply(WEIGHT_SCENARIO_TRADEOFF));
        total = total.add(scoreMap.get(KEY_DEBUGGING).multiply(WEIGHT_DEBUGGING));
        total = total.add(scoreMap.get(KEY_COMMUNICATION).multiply(WEIGHT_COMMUNICATION));
        return total.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
