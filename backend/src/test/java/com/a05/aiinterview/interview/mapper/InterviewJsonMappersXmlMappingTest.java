package com.a05.aiinterview.interview.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Interview JSON mappers XML mapping tests")
class InterviewJsonMappersXmlMappingTest {

    @Test
    @DisplayName("InterviewAttemptMapper selectByAttemptId should use resultMap with JSON typeHandler")
    void interviewAttemptMapper_shouldUseResultMapWithJsonTypeHandler() throws Exception {
        String xml = readResource("mapper/interview/InterviewAttemptMapper.xml");
        assertThat(xml).contains("resultMap id=\"InterviewAttemptResultMap\"");
        assertThat(xml).contains("column=\"evaluation_json\"");
        assertThat(xml).contains("property=\"evaluationJson\"");
        assertThat(xml).contains("column=\"detail_evaluation_status\"");
        assertThat(xml).contains("property=\"detailEvaluationStatus\"");
        assertThat(xml).contains("column=\"detail_evaluation_json\"");
        assertThat(xml).contains("property=\"detailEvaluationJson\"");
        assertThat(xml).contains("typeHandler=\"com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler\"");
        assertThat(xml).contains("select id=\"selectByAttemptId\"");
        assertThat(xml).contains("resultMap=\"InterviewAttemptResultMap\"");
        assertThat(xml).contains("select id=\"selectBySessionId\"");
        assertThat(xml).contains("ORDER BY question_id ASC, created_at DESC, id DESC");
        assertThat(xml).contains("select id=\"selectLatestFinalAttempt\"");
        assertThat(xml).contains("AND is_final = 1");
    }

    @Test
    @DisplayName("InterviewReportMapper selectBySessionId should use resultMap with JSON typeHandlers")
    void interviewReportMapper_shouldUseResultMapWithJsonTypeHandler() throws Exception {
        String xml = readResource("mapper/interview/InterviewReportMapper.xml");
        assertThat(xml).contains("resultMap id=\"InterviewReportResultMap\"");
        assertThat(xml).contains("column=\"skill_domain_scores\"");
        assertThat(xml).contains("column=\"comprehensive_radar_scores\"");
        assertThat(xml).contains("column=\"interactive_transcript_json\"");
        assertThat(xml).contains("typeHandler=\"com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler\"");
        assertThat(xml).contains("select id=\"selectBySessionId\"");
        assertThat(xml).contains("resultMap=\"InterviewReportResultMap\"");
    }

    @Test
    @DisplayName("InterviewQuestionMapper custom select should use resultMap with JSON typeHandlers")
    void interviewQuestionMapper_shouldUseResultMapWithJsonTypeHandler() throws Exception {
        String xml = readResource("mapper/interview/InterviewQuestionMapper.xml");
        assertThat(xml).contains("resultMap id=\"InterviewQuestionResultMap\"");
        assertThat(xml).contains("column=\"domain_code\"");
        assertThat(xml).contains("column=\"secondary_domain_codes\"");
        assertThat(xml).contains("column=\"expected_points\"");
        assertThat(xml).contains("column=\"generation_context_json\"");
        assertThat(xml).contains("typeHandler=\"com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler\"");
        assertThat(xml).contains("select id=\"selectUserFirstIntroQuestions\"");
        assertThat(xml).contains("resultMap=\"InterviewQuestionResultMap\"");
    }

    @Test
    @DisplayName("InterviewSessionMapper should not expose deprecated position domain version field")
    void interviewSessionMapper_shouldNotExposePositionDomainVersion() throws Exception {
        String xml = readResource("mapper/interview/InterviewSessionMapper.xml");
        assertThat(xml).contains("resultMap id=\"InterviewSessionResultMap\"");
        assertThat(xml).contains("state_ledger_json");
        assertThat(xml).contains("syllabus_json");
    }

    private String readResource(String resourcePath) throws Exception {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        assertThat(in)
                .as("缺少 Mapper XML: %s", resourcePath)
                .isNotNull();
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
