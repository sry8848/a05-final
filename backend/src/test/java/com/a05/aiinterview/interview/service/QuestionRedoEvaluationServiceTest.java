package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationInput;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import com.a05.aiinterview.interview.entity.QuestionRedoAttempt;
import com.a05.aiinterview.interview.mapper.QuestionRedoAttemptMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionRedoEvaluationServiceTest {

    @Test
    void evaluateByRedoAttemptId_shouldUseFrozenSnapshotAndEmptyRecentContext() {
        AiClient aiClient = mock(AiClient.class);
        QuestionRedoAttemptMapper redoMapper = mock(QuestionRedoAttemptMapper.class);
        QuestionRedoEvaluationService service = new QuestionRedoEvaluationService(
                aiClient,
                redoMapper,
                new ObjectMapper()
        );

        QuestionRedoAttempt attempt = new QuestionRedoAttempt();
        attempt.setId(3001L);
        attempt.setUserId(9L);
        attempt.setSourceSessionId(11L);
        attempt.setSourceQuestionId(22L);
        attempt.setAnswerText("我会按 parse、layout、paint 来回答。");
        attempt.setEvaluationStatus("pending");
        attempt.setSourceSnapshotJson(Map.of(
                "questionStem", "请解释浏览器渲染流水线。",
                "questionType", "PRINCIPLE",
                "domainName", "浏览器原理",
                "positionCode", "FRONTEND",
                "experienceLevel", "JUNIOR",
                "mode", "practice",
                "expectedPoints", List.of("Parse", "Layout", "Paint")
        ));
        attempt.setCreatedAt(LocalDateTime.now());
        when(redoMapper.selectById(3001L)).thenReturn(attempt);
        when(redoMapper.updateById(any())).thenReturn(1);

        QuestionDetailEvaluationOutput output = QuestionDetailEvaluationOutput.builder()
                .score(BigDecimal.valueOf(91))
                .commentary("回答更扎实")
                .strengthPoints(List.of("主流程完整"))
                .weakPoints(List.of("少了性能指标"))
                .highlightedAnnotations(List.of(
                        QuestionDetailEvaluationOutput.HighlightedAnnotation.builder()
                                .quote("parse、layout、paint")
                                .label("strength")
                                .comment("主流程明确")
                                .build()
                ))
                .idealAnswerOutline(List.of("定义", "流程", "优化"))
                .rewrittenAnswer("参考重答")
                .build();
        when(aiClient.callQuestionDetailEvaluation(any())).thenReturn(
                AiCallResult.<QuestionDetailEvaluationOutput>builder().output(output).build()
        );

        service.evaluateByRedoAttemptId(3001L);

        ArgumentCaptor<QuestionDetailEvaluationInput> inputCaptor = ArgumentCaptor.forClass(QuestionDetailEvaluationInput.class);
        verify(aiClient).callQuestionDetailEvaluation(inputCaptor.capture());
        QuestionDetailEvaluationInput input = inputCaptor.getValue();
        assertEquals(11L, input.getInterviewId());
        assertEquals(22L, input.getQuestionId());
        assertEquals("FRONTEND", input.getPositionCode());
        assertEquals("JUNIOR", input.getExperienceLevel());
        assertEquals("practice", input.getMode());
        assertEquals("请解释浏览器渲染流水线。", input.getQuestionStem());
        assertEquals("PRINCIPLE", input.getQuestionType());
        assertEquals("浏览器原理", input.getDomainName());
        assertEquals("我会按 parse、layout、paint 来回答。", input.getAnswerText());
        assertEquals(List.of("Parse", "Layout", "Paint"), input.getExpectedPoints());
        assertTrue(input.getRecentContext().isEmpty());

        ArgumentCaptor<QuestionRedoAttempt> updateCaptor = ArgumentCaptor.forClass(QuestionRedoAttempt.class);
        verify(redoMapper, times(2)).updateById(updateCaptor.capture());
        assertEquals("generating", updateCaptor.getAllValues().get(0).getEvaluationStatus());
        assertEquals("ready", updateCaptor.getAllValues().get(1).getEvaluationStatus());
        assertEquals(BigDecimal.valueOf(91), updateCaptor.getAllValues().get(1).getEvaluationJson().get("score"));
        Object rawAnnotations = updateCaptor.getAllValues().get(1).getEvaluationJson().get("highlightedAnnotations");
        assertTrue(rawAnnotations instanceof List<?> annotations && !annotations.isEmpty());
    }

    @Test
    void evaluateByRedoAttemptId_shouldClampScoresToPercentageRange() {
        AiClient aiClient = mock(AiClient.class);
        QuestionRedoAttemptMapper redoMapper = mock(QuestionRedoAttemptMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionRedoEvaluationService service = new QuestionRedoEvaluationService(
                aiClient,
                redoMapper,
                objectMapper
        );

        QuestionRedoAttempt attempt = new QuestionRedoAttempt();
        attempt.setId(3001L);
        attempt.setUserId(9L);
        attempt.setSourceSessionId(11L);
        attempt.setSourceQuestionId(22L);
        attempt.setAnswerText("重答内容");
        attempt.setEvaluationStatus("pending");
        attempt.setSourceSnapshotJson(Map.of(
                "questionStem", "请解释浏览器渲染流水线。",
                "questionType", "PRINCIPLE",
                "domainName", "浏览器原理",
                "positionCode", "FRONTEND",
                "experienceLevel", "JUNIOR",
                "mode", "practice",
                "expectedPoints", List.of("Parse", "Layout", "Paint")
        ));
        when(redoMapper.selectById(3001L)).thenReturn(attempt);
        when(redoMapper.updateById(any())).thenReturn(1);

        QuestionDetailEvaluationOutput output = QuestionDetailEvaluationOutput.builder()
                .score(new BigDecimal("-8"))
                .evaluatedDomains(List.of(
                        QuestionDetailEvaluationOutput.EvaluatedDomain.builder()
                                .domainCode("browser")
                                .domainName("浏览器原理")
                                .score(new BigDecimal("108.2"))
                                .commentary("超范围")
                                .build()
                ))
                .build();
        when(aiClient.callQuestionDetailEvaluation(any())).thenReturn(
                AiCallResult.<QuestionDetailEvaluationOutput>builder().output(output).build()
        );

        service.evaluateByRedoAttemptId(3001L);

        ArgumentCaptor<QuestionRedoAttempt> updateCaptor = ArgumentCaptor.forClass(QuestionRedoAttempt.class);
        verify(redoMapper, times(2)).updateById(updateCaptor.capture());
        QuestionDetailEvaluationOutput saved = objectMapper.convertValue(
                updateCaptor.getAllValues().get(1).getEvaluationJson(),
                QuestionDetailEvaluationOutput.class
        );
        assertEquals(new BigDecimal("0"), saved.getScore());
        assertEquals(new BigDecimal("100"), saved.getEvaluatedDomains().get(0).getScore());
    }
}
