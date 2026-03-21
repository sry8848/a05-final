package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.ai.dto.ReportGenerationOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report AI 输出 DTO 契约测试。
 *
 * <p>覆盖三条路径：
 * <ol>
 *   <li>正常输出 → 解析成功，{@code overallScore} 在范围内、
 *       {@code skillDomainScores} 覆盖所有知识域</li>
 *   <li>缺少必填字段 → 兜底默认值填入（score 为 0、domainScores 为空列表）</li>
 *   <li>字段类型漂移 → 异常捕获 + 降级逻辑触发</li>
 * </ol>
 */
@DisplayName("Report 契约测试")
class ReportGenerationContractTest {

    private AiOutputContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiOutputContractValidator(new ObjectMapper());
    }

    // ─────────────────────── 路径1：正常输出 ────────────────────────────────

    @Test
    @DisplayName("正常 JSON → overallScore 在 [0,100]，skillDomainScores 非空，summary 有内容")
    void normalOutput_shouldParseSuccessfully() {
        String json = """
                {
                  "overallScore": 78.5,
                  "summary": "候选人整体表现良好，基础扎实，高并发场景有提升空间。",
                  "strengths": ["基础知识掌握扎实", "表达逻辑清晰"],
                  "weaknesses": ["高并发实践不足"],
                  "improvementSuggestions": ["深入学习 JUC 源码"],
                  "comprehensiveRadarScores": [
                    {
                      "dimensionKey": "fundamentals",
                      "dimensionName": "基础原理掌握",
                      "score": 81.0
                    },
                    {
                      "dimensionKey": "engineering_practice",
                      "dimensionName": "工程实践与项目落地",
                      "score": 76.0
                    }
                  ],
                  "skillDomainScores": [
                    {
                      "domainCode": "jvm",
                      "domainName": "JVM 原理",
                      "score": 82.0,
                      "commentary": "对 GC 算法掌握较好，分代模型描述清晰。"
                    },
                    {
                      "domainCode": "concurrency",
                      "domainName": "并发编程",
                      "score": 70.0,
                      "commentary": "AQS 原理描述不够深入。"
                    }
                  ]
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output).isNotNull();
        assertThat(output.getOverallScore()).isEqualByComparingTo(new BigDecimal("78.5"));
        assertThat(output.getSummary()).contains("候选人");
        assertThat(output.getStrengths()).hasSize(2);
        assertThat(output.getComprehensiveRadarScores()).hasSize(2);
        assertThat(output.getComprehensiveRadarScores().get(0).getDimensionKey()).isEqualTo("fundamentals");
        assertThat(output.getSkillDomainScores()).hasSize(2);
        assertThat(output.getSkillDomainScores().get(0).getDomainCode()).isEqualTo("jvm");
        assertThat(output.getSkillDomainScores().get(0).getScore())
                .isEqualByComparingTo(new BigDecimal("82.0"));
    }

    @Test
    @DisplayName("overallScore 为整数 → 正确解析为 BigDecimal")
    void normalOutput_integerScore_shouldParseCorrectly() {
        String json = """
                {
                  "overallScore": 85,
                  "summary": "表现优秀。",
                  "skillDomainScores": []
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output.getOverallScore()).isEqualByComparingTo(new BigDecimal("85"));
    }

    @Test
    @DisplayName("overallScore 边界值 0 和 100 均合法")
    void normalOutput_boundaryScores_shouldBeValid() {
        String jsonMin = """
                { "overallScore": 0, "summary": "很差", "skillDomainScores": [] }
                """;
        String jsonMax = """
                { "overallScore": 100, "summary": "满分", "skillDomainScores": [] }
                """;

        ReportGenerationOutput minOutput = validator.parseAndValidateReport(jsonMin);
        ReportGenerationOutput maxOutput = validator.parseAndValidateReport(jsonMax);

        assertThat(minOutput.getOverallScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(maxOutput.getOverallScore()).isEqualByComparingTo(new BigDecimal("100"));
    }

    // ─────────────────────── 路径2：缺少必填字段 ─────────────────────────────

    @Test
    @DisplayName("overallScore 为 null → 兜底为 0，不抛出异常")
    void missingOverallScore_shouldFallbackToZero() {
        String json = """
                {
                  "summary": "缺分数的报告",
                  "skillDomainScores": []
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output.getOverallScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("overallScore 超出范围（负数）→ 截断修正为 0")
    void outOfRangeScore_negative_shouldBeClampedToZero() {
        String json = """
                {
                  "overallScore": -10,
                  "summary": "无效分数",
                  "skillDomainScores": []
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output.getOverallScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("overallScore 超出范围（超过 100）→ 截断修正为 100")
    void outOfRangeScore_over100_shouldBeClampedTo100() {
        String json = """
                {
                  "overallScore": 150,
                  "summary": "超范围分数",
                  "skillDomainScores": []
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output.getOverallScore()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("skillDomainScores 为 null → 填入空列表兜底")
    void missingSkillDomainScores_shouldFallbackToEmptyList() {
        String json = """
                {
                  "overallScore": 75.0,
                  "summary": "缺域得分的报告"
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output.getSkillDomainScores()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("practice 模式允许 comprehensiveRadarScores 为 null")
    void missingComprehensiveRadarScores_shouldRemainNull() {
        String json = """
                {
                  "overallScore": 75.0,
                  "summary": "练习模式报告",
                  "skillDomainScores": []
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output.getComprehensiveRadarScores()).isNull();
    }

    @Test
    @DisplayName("summary 为 null → 填入占位符兜底")
    void missingSummary_shouldFallbackToPlaceholder() {
        String json = """
                {
                  "overallScore": 72.0,
                  "skillDomainScores": []
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output.getSummary()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("完全空 JSON 对象 → 所有必填字段均兜底")
    void emptyJsonObject_shouldFallbackAllRequiredFields() {
        ReportGenerationOutput output = validator.parseAndValidateReport("{}");

        assertThat(output.getOverallScore()).isNotNull().isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(output.getSkillDomainScores()).isNotNull();
        assertThat(output.getSummary()).isNotBlank();
    }

    // ─────────────────────── 路径3：字段类型漂移 ─────────────────────────────

    @Test
    @DisplayName("overallScore 为字符串（类型漂移）→ 捕获异常，返回降级对象 score=0")
    void typeDrift_scoreAsString_shouldReturnFallback() {
        String json = """
                {
                  "overallScore": "优秀",
                  "summary": "类型错误的分数",
                  "skillDomainScores": []
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output).isNotNull();
        assertThat(output.getOverallScore()).isNotNull();
    }

    @Test
    @DisplayName("skillDomainScores 为字符串而非数组（类型漂移）→ 捕获异常，返回降级对象")
    void typeDrift_domainScoresAsString_shouldReturnFallback() {
        String json = """
                {
                  "overallScore": 80.0,
                  "summary": "域得分类型错误",
                  "skillDomainScores": "should-be-array"
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output).isNotNull();
        assertThat(output.getSkillDomainScores()).isNotNull();
    }

    @Test
    @DisplayName("SkillDomainScore.score 为字符串（类型漂移）→ 捕获异常，返回降级对象")
    void typeDrift_domainScoreValueAsString_shouldReturnFallback() {
        String json = """
                {
                  "overallScore": 80.0,
                  "summary": "域内分数类型错误",
                  "skillDomainScores": [
                    { "domainCode": "jvm", "score": "八十分" }
                  ]
                }
                """;

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output).isNotNull();
        assertThat(output.getOverallScore()).isNotNull();
        assertThat(output.getSkillDomainScores()).isNotNull();
    }

    @Test
    @DisplayName("非法 JSON 格式 → 捕获解析异常，返回降级对象")
    void invalidJson_shouldReturnFallback() {
        String json = "<<invalid>>";

        ReportGenerationOutput output = validator.parseAndValidateReport(json);

        assertThat(output).isNotNull();
        assertThat(output.getOverallScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(output.getSkillDomainScores()).isNotNull();
    }

    // ─────────────────────── 直接调用 validate() ────────────────────────────

    @Test
    @DisplayName("validate(null) → 返回降级对象，score=0，domains 为空列表")
    void validateNull_shouldReturnFallback() {
        ReportGenerationOutput output = validator.validateReport(null);

        assertThat(output).isNotNull();
        assertThat(output.getOverallScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(output.getSkillDomainScores()).isNotNull().isEmpty();
        assertThat(output.getSummary()).isNotBlank();
    }
}
