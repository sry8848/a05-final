package com.a05.aiinterview.interview.dto;

import com.a05.aiinterview.common.dto.RadarDimensionScoreDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试报告响应 DTO。
 * 对应 GET /interviews/{sessionId}/report 接口的返回值。
 * 包含总体评价和逐知识域明细两层数据。
 */
@Data
@Schema(description = "面试报告")
public class InterviewReportDto {

    @Schema(description = "报告 ID", example = "1001")
    private Long reportId;

    @Schema(description = "面试会话 ID", example = "2001")
    private Long sessionId;

    @Schema(description = "岗位编码", example = "JAVA_BACKEND")
    private String targetRole;

    @Schema(description = "面试模式", example = "professional")
    private String mode;

    @Schema(description = "报告生成状态：generating / ready", example = "ready")
    private String reportStatus;

    @Schema(description = "综合得分（0~100）", example = "78.5")
    private BigDecimal overallScore;

    @Schema(description = "总结评语")
    private String summary;

    @Schema(description = "优势列表", example = "[\"基础知识扎实\", \"表达逻辑清晰\"]")
    private List<String> strengths;

    @Schema(description = "薄弱点列表", example = "[\"高并发场景经验有限\"]")
    private List<String> weaknesses;

    @Schema(description = "提升建议列表")
    private List<String> improvementSuggestions;

    @Schema(description = "推荐练习知识点列表（可为 null）")
    private List<String> recommendedTopics;

    @Schema(description = "综合能力雷达，仅专业模式返回")
    private List<RadarDimensionScoreDto> comprehensiveRadarScores;

    @Schema(description = "逐知识域评分明细")
    private List<SkillDomainScoreDto> skillDomainScores;

    @Schema(description = "题目轻量摘要列表")
    private List<QuestionSummaryDto> questions;

    @Schema(description = "报告生成时间")
    private LocalDateTime createdAt;

    // ────────────────────────────────────────────

    /**
     * 单知识域评分明细 DTO。
     */
    @Data
    @Schema(description = "知识域评分明细")
    public static class SkillDomainScoreDto {

        @Schema(description = "知识域编码", example = "java_concurrency")
        private String domainCode;

        @Schema(description = "知识域中文名", example = "Java 并发编程")
        private String domainName;

        @Schema(description = "该知识域得分（0~100）", example = "80.0")
        private BigDecimal score;

        @Schema(description = "实际达到的深度等级", example = "L3")
        private String achievedDepth;

        @Schema(description = "AI 定性点评")
        private String commentary;
    }

    // ────────────────────────────────────────────

    /**
     * 报告页题目轻量摘要 DTO。
     */
    @Data
    @Schema(description = "题目轻量摘要")
    public static class QuestionSummaryDto {

        @Schema(description = "题目 ID", example = "9001")
        private Long questionId;

        @Schema(description = "题号", example = "1")
        private Integer questionNo;

        @Schema(description = "题干")
        private String questionStem;

        @Schema(description = "作答状态：answered / skipped / pending", example = "answered")
        private String status;

        @Schema(description = "单题分数（可为空）", example = "82.5")
        private BigDecimal score;
    }

    // ────────────────────────────────────────────

    /**
     * 从 InterviewReport 实体构建响应 DTO 的工厂方法。
     *
     * @param report 已持久化的报告实体
     * @return 响应 DTO（报告已就绪）
     */
    @SuppressWarnings("unchecked")
    public static InterviewReportDto fromEntity(com.a05.aiinterview.interview.entity.InterviewReport report) {
        InterviewReportDto dto = new InterviewReportDto();
        dto.setReportId(report.getId());
        dto.setSessionId(report.getSessionId());
        dto.setReportStatus("ready");
        dto.setOverallScore(report.getOverallScore());
        dto.setSummary(report.getSummary());
        dto.setStrengths(report.getStrengths());
        dto.setWeaknesses(report.getWeaknesses());
        dto.setImprovementSuggestions(report.getImprovementSuggestions());
        dto.setRecommendedTopics(report.getRecommendedTopics());
        dto.setCreatedAt(report.getCreatedAt());
        if (report.getComprehensiveRadarScores() != null) {
            Object dimensionsObj = report.getComprehensiveRadarScores().get("dimensions");
            if (dimensionsObj instanceof List<?> dimensions) {
                List<RadarDimensionScoreDto> radarScores = dimensions.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .map(m -> {
                            RadarDimensionScoreDto scoreDto = new RadarDimensionScoreDto();
                            scoreDto.setDimensionKey((String) m.get("dimensionKey"));
                            scoreDto.setDimensionName((String) m.get("dimensionName"));
                            Object score = m.get("score");
                            if (score instanceof Number n) {
                                scoreDto.setScore(BigDecimal.valueOf(n.doubleValue()));
                            }
                            return scoreDto;
                        })
                        .toList();
                dto.setComprehensiveRadarScores(radarScores);
            }
        }

        // 将 List<Map<String, Object>> 反序列化为 SkillDomainScoreDto 列表
        if (report.getSkillDomainScores() != null) {
            List<SkillDomainScoreDto> scoreList = report.getSkillDomainScores().stream()
                    .map(m -> {
                        SkillDomainScoreDto s = new SkillDomainScoreDto();
                        s.setDomainCode((String) m.get("domainCode"));
                        s.setDomainName((String) m.get("domainName"));
                        Object score = m.get("score");
                        if (score instanceof Number n) {
                            s.setScore(BigDecimal.valueOf(n.doubleValue()));
                        }
                        s.setAchievedDepth((String) m.get("achievedDepth"));
                        s.setCommentary((String) m.get("commentary"));
                        return s;
                    })
                    .toList();
            dto.setSkillDomainScores(scoreList);
        }
        return dto;
    }

    /**
     * 构建"报告生成中"的占位响应（报告尚未就绪时返回）。
     *
     * @param sessionId 面试会话 ID
     * @return 仅含状态字段的 DTO
     */
    public static InterviewReportDto generating(Long sessionId) {
        InterviewReportDto dto = new InterviewReportDto();
        dto.setSessionId(sessionId);
        dto.setReportStatus("generating");
        return dto;
    }
}
