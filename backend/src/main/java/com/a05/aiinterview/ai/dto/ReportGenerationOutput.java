package com.a05.aiinterview.ai.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报告生成 AI 调用结果。
 * 包含总体评价（得分、摘要、优劣势）和逐知识域明细两部分。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code overallScore} - 0~100 综合分，保留 1 位小数</li>
 *   <li>{@code skillDomainScores} - 逐知识域评分，前端用于渲染雷达图</li>
 *   <li>{@code summary} - 2~3 段总结性文字，不超过 300 字</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportGenerationOutput {

    /** 综合得分，0~100，保留 1 位小数 */
    private BigDecimal overallScore;

    /** 总结评语，概括候选人整体表现 */
    private String summary;

    /** 优势列表（3~5 条） */
    private List<String> strengths;

    /** 薄弱点列表（3~5 条） */
    private List<String> weaknesses;

    /** 提升建议列表（3~5 条） */
    private List<String> improvementSuggestions;

    /** 专业模式综合能力雷达，练习模式可为 null。 */
    private List<ComprehensiveRadarScore> comprehensiveRadarScores;

    /**
     * 逐知识域评分明细，前端用于渲染雷达图和各域折叠卡片。
     */
    private List<SkillDomainScore> skillDomainScores;

    /**
     * 单知识域评分明细。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillDomainScore {

        /** 知识域编码 */
        private String domainCode;

        /** 知识域中文名 */
        private String domainName;

        /** 该知识域得分，0~100 */
        private BigDecimal score;

        /** AI 对该知识域表现的定性点评（1~2 句话） */
        private String commentary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComprehensiveRadarScore {
        private String dimensionKey;
        private String dimensionName;
        private BigDecimal score;
    }
}
