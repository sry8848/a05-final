package com.a05.aiinterview.ai;

import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationAiOutput;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationInput;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import com.a05.aiinterview.ai.impl.MockAiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuestionDetailEvaluation output tests")
class QuestionDetailEvaluationOutputTest {

    @Test
    @DisplayName("BeanOutputConverter for AI output should NOT expose start/end in schema")
    void beanOutputConverter_forAiOutput_shouldNotExposeStartEnd() {
        BeanOutputConverter<QuestionDetailEvaluationAiOutput> converter =
                new BeanOutputConverter<>(QuestionDetailEvaluationAiOutput.class);

        String format = converter.getFormat();
        assertThat(format).doesNotContain("\"start\"");
        assertThat(format).doesNotContain("\"end\"");
    }

    @Test
    @DisplayName("BeanOutputConverter should parse AI output JSON without start/end")
    void beanOutputConverter_shouldParseAiOutputJson() {
        BeanOutputConverter<QuestionDetailEvaluationAiOutput> converter =
                new BeanOutputConverter<>(QuestionDetailEvaluationAiOutput.class);

        String json = """
                {
                  "score": 81.5,
                  "commentary": "回答结构较完整，建议补充边界和指标。",
                  "strengthPoints": ["能够说明核心方案"],
                  "weakPoints": ["缺少量化指标"],
                  "evaluatedDomains": [
                    {
                      "domainCode": "java_concurrency",
                      "domainName": "Java 并发",
                      "score": 81.5,
                      "commentary": "基础较好"
                    }
                  ],
                  "highlightedSegments": [
                    {
                      "segment": "主要提升了系统性能",
                      "label": "strength",
                      "comment": "建议补充具体指标"
                    },
                    {
                      "segment": "大概可以应对高并发",
                      "label": "weakness",
                      "comment": "缺少容量评估依据"
                    }
                  ],
                  "highlightedAnnotations": [
                    {
                      "quote": "主要提升了系统性能",
                      "label": "strength",
                      "comment": "建议补充具体指标"
                    }
                  ],
                  "idealAnswerOutline": ["定义目标", "说明方案", "给出结果"],
                  "rewrittenAnswer": "我会先定义目标，再说明方案与结果。"
                }
                """;

        QuestionDetailEvaluationAiOutput output = converter.convert(json);

        assertThat(output).isNotNull();
        assertThat(output.getScore()).isNotNull();
        assertThat(output.getCommentary()).contains("建议补充边界");
        assertThat(output.getHighlightedSegments()).hasSize(2);
        assertThat(output.getHighlightedSegments().get(0).getSegment()).isEqualTo("主要提升了系统性能");
        assertThat(output.getHighlightedSegments().get(0).getLabel()).isEqualTo("strength");
        assertThat(output.getHighlightedSegments().get(0).getComment()).isEqualTo("建议补充具体指标");
        assertThat(output.getHighlightedAnnotations()).hasSize(1);
        assertThat(output.getHighlightedAnnotations().get(0).getQuote()).isEqualTo("主要提升了系统性能");
    }

    @Test
    @DisplayName("BeanOutputConverter should parse question detail evaluation JSON")
    void beanOutputConverter_shouldParseOutputJson() {
        BeanOutputConverter<QuestionDetailEvaluationOutput> converter =
                new BeanOutputConverter<>(QuestionDetailEvaluationOutput.class);

        String json = """
                {
                  "score": 81.5,
                  "commentary": "回答结构较完整，建议补充边界和指标。",
                  "strengthPoints": ["能够说明核心方案"],
                  "weakPoints": ["缺少量化指标"],
                  "evaluatedDomains": [
                    {
                      "domainCode": "java_concurrency",
                      "domainName": "Java 并发",
                      "score": 81.5,
                      "commentary": "基础较好"
                    }
                  ],
                  "highlightedSegments": [
                    {
                      "segment": "主要提升了系统性能",
                      "label": "strength",
                      "comment": "建议补充具体指标"
                    },
                    {
                      "segment": "大概可以应对高并发",
                      "label": "weakness",
                      "comment": "缺少容量评估依据"
                    }
                  ],
                  "highlightedAnnotations": [
                    {
                      "start": 1,
                      "end": 9,
                      "quote": "主要提升了系统性能",
                      "label": "strength",
                      "comment": "建议补充具体指标"
                    }
                  ],
                  "idealAnswerOutline": ["定义目标", "说明方案", "给出结果"],
                  "rewrittenAnswer": "我会先定义目标，再说明方案与结果。"
                }
                """;

        QuestionDetailEvaluationOutput output = converter.convert(json);

        assertThat(output).isNotNull();
        assertThat(output.getScore()).isNotNull();
        assertThat(output.getCommentary()).contains("建议补充边界");
        assertThat(output.getHighlightedSegments()).hasSize(2);
        assertThat(output.getHighlightedSegments().get(0).getSegment()).isEqualTo("主要提升了系统性能");
        assertThat(output.getHighlightedSegments().get(0).getLabel()).isEqualTo("strength");
        assertThat(output.getHighlightedSegments().get(0).getComment()).isEqualTo("建议补充具体指标");
        assertThat(output.getHighlightedAnnotations()).hasSize(1);
        assertThat(output.getHighlightedAnnotations().get(0).getQuote()).isEqualTo("主要提升了系统性能");
        assertThat(output.getHighlightedAnnotations().get(0).getStart()).isEqualTo(1);
    }

    @Test
    @DisplayName("Mock output should be complete and deterministic")
    void mockOutput_shouldBeDeterministic() {
        PromptProperties promptProperties = new PromptProperties();
        MockAiClient mockAiClient = new MockAiClient(promptProperties);
        QuestionDetailEvaluationInput input = QuestionDetailEvaluationInput.builder()
                .interviewId(1L)
                .questionId(2L)
                .positionCode("JAVA_BACKEND")
                .experienceLevel("SENIOR")
                .mode("professional")
                .questionStem("请讲讲你做过的性能优化。")
                .questionType("SCENARIO")
                .domainCode("perf")
                .domainName("性能优化")
                .answerText("我优化了缓存策略。")
                .build();

        QuestionDetailEvaluationOutput first = mockAiClient.callQuestionDetailEvaluation(input).getOutput();
        QuestionDetailEvaluationOutput second = mockAiClient.callQuestionDetailEvaluation(input).getOutput();

        assertThat(first).usingRecursiveComparison().isEqualTo(second);
        assertThat(first.getScore()).isNotNull();
        assertThat(first.getHighlightedAnnotations()).isNotEmpty();
        assertThat(first.getHighlightedAnnotations().get(0).getQuote()).isNotBlank();
        assertThat(first.getHighlightedAnnotations().get(0).getLabel()).isIn("strength", "weakness");
    }
}
