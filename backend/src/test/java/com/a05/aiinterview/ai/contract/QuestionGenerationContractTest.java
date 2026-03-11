package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.ai.dto.QuestionGenerationOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuestionGeneration AI 输出 DTO 契约测试。
 *
 * <p>覆盖三条路径：
 * <ol>
 *   <li>正常输出 → 解析成功，{@code stem} 非空、{@code targetDepth} 合法</li>
 *   <li>缺少必填字段 → 兜底默认值填入（占位符 stem、空列表 expectedPoints）</li>
 *   <li>字段类型漂移 → 异常捕获 + 降级逻辑触发</li>
 * </ol>
 */
@DisplayName("QuestionGeneration 契约测试")
class QuestionGenerationContractTest {

    private AiOutputContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiOutputContractValidator(new ObjectMapper());
    }

    // ─────────────────────── 路径1：正常输出 ────────────────────────────────

    @Test
    @DisplayName("正常 JSON → stem 非空，expectedPoints 有内容，targetDepth 存在")
    void normalOutput_shouldParseSuccessfully() {
        String json = """
                {
                  "stem": "请描述 Java 线程池的核心参数及其在高并发场景下的调优策略。",
                  "targetSkill": "线程池调优",
                  "expectedPoints": [
                    "核心线程数、最大线程数、队列容量的含义",
                    "拒绝策略的选择",
                    "监控与动态调参"
                  ],
                  "difficulty": "medium",
                  "targetDepth": "L3"
                }
                """;

        QuestionGenerationOutput output = validator.parseAndValidateQuestionGeneration(json);

        assertThat(output).isNotNull();
        assertThat(output.getStem()).contains("线程池");
        assertThat(output.getExpectedPoints()).hasSize(3);
        assertThat(output.getTargetDepth()).isEqualTo("L3");
        assertThat(output.getDifficulty()).isEqualTo("medium");
    }

    @Test
    @DisplayName("正常 JSON → targetSkill 字段正确映射")
    void normalOutput_targetSkill_shouldBeMapped() {
        String json = """
                {
                  "stem": "请介绍 Redis 缓存穿透的原因和解决方案。",
                  "targetSkill": "缓存穿透",
                  "expectedPoints": ["布隆过滤器", "空值缓存"],
                  "difficulty": "medium",
                  "targetDepth": "L2"
                }
                """;

        QuestionGenerationOutput output = validator.parseAndValidateQuestionGeneration(json);

        assertThat(output.getTargetSkill()).isEqualTo("缓存穿透");
        assertThat(output.getStem()).isNotBlank();
    }

    // ─────────────────────── 路径2：缺少必填字段 ─────────────────────────────

    @Test
    @DisplayName("stem 为 null → 填入占位符 stem 兜底，不抛出异常")
    void missingStem_shouldFallbackToPlaceholder() {
        String json = """
                {
                  "targetSkill": "GC 原理",
                  "expectedPoints": ["分代模型"],
                  "difficulty": "hard",
                  "targetDepth": "L4"
                }
                """;

        QuestionGenerationOutput output = validator.parseAndValidateQuestionGeneration(json);

        assertThat(output).isNotNull();
        assertThat(output.getStem()).isNotNull().isNotBlank();
        // 占位符不能是空字符串，必须有内容提示失败原因
        assertThat(output.getStem()).contains("失败");
    }

    @Test
    @DisplayName("stem 为空字符串 → 填入占位符 stem 兜底")
    void blankStem_shouldFallbackToPlaceholder() {
        String json = """
                {
                  "stem": "   ",
                  "expectedPoints": [],
                  "targetDepth": "L2"
                }
                """;

        QuestionGenerationOutput output = validator.parseAndValidateQuestionGeneration(json);

        assertThat(output.getStem()).isNotBlank();
    }

    @Test
    @DisplayName("expectedPoints 为 null → 填入空列表兜底")
    void missingExpectedPoints_shouldFallbackToEmptyList() {
        String json = """
                {
                  "stem": "请描述 Spring Boot 自动配置原理。",
                  "targetSkill": "自动配置",
                  "difficulty": "medium",
                  "targetDepth": "L3"
                }
                """;

        QuestionGenerationOutput output = validator.parseAndValidateQuestionGeneration(json);

        assertThat(output.getExpectedPoints()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("完全空 JSON 对象 → 所有必填字段均兜底")
    void emptyJsonObject_shouldFallbackAllRequiredFields() {
        QuestionGenerationOutput output = validator.parseAndValidateQuestionGeneration("{}");

        assertThat(output.getStem()).isNotBlank();
        assertThat(output.getExpectedPoints()).isNotNull();
    }

    // ─────────────────────── 路径3：字段类型漂移 ─────────────────────────────

    @Test
    @DisplayName("expectedPoints 为字符串而非数组（类型漂移）→ 捕获异常，返回降级对象")
    void typeDrift_expectedPointsAsString_shouldReturnFallback() {
        String json = """
                {
                  "stem": "请介绍 HashMap 底层结构。",
                  "expectedPoints": "数组+链表+红黑树",
                  "targetDepth": "L3"
                }
                """;

        QuestionGenerationOutput output = validator.parseAndValidateQuestionGeneration(json);

        assertThat(output).isNotNull();
        assertThat(output.getStem()).isNotBlank();
        assertThat(output.getExpectedPoints()).isNotNull();
    }

    @Test
    @DisplayName("非法 JSON 格式 → 捕获解析异常，返回降级对象，stem 为占位符")
    void invalidJson_shouldReturnFallbackWithPlaceholderStem() {
        String json = "not-a-json-at-all";

        QuestionGenerationOutput output = validator.parseAndValidateQuestionGeneration(json);

        assertThat(output).isNotNull();
        assertThat(output.getStem()).isNotBlank();
        assertThat(output.getExpectedPoints()).isNotNull();
    }

    // ─────────────────────── 直接调用 validate() ────────────────────────────

    @Test
    @DisplayName("validate(null) → 返回降级对象，stem 为占位符")
    void validateNull_shouldReturnFallback() {
        QuestionGenerationOutput output = validator.validateQuestionGeneration(null);

        assertThat(output).isNotNull();
        assertThat(output.getStem()).isNotBlank();
        assertThat(output.getExpectedPoints()).isNotNull();
    }
}
