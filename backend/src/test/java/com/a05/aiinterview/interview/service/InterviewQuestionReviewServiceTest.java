package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewQuestionReviewDto;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewQuestionReviewServiceTest {

    @Test
    void getQuestionReview_shouldReturnReadyDetailFromLatestFinal() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionReviewService service = new InterviewQuestionReviewService(
                sessionMapper, questionMapper, attemptMapper, new ObjectMapper()
        );

        Long sessionId = 1001L;
        Long questionId = 5001L;
        Long userId = 9L;

        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setSyllabusJson(Map.of(
                "domains", List.of(Map.of("domainCode", "java_concurrency", "domainName", "Java 并发"))
        ));
        when(sessionMapper.selectById(sessionId)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(questionId);
        question.setSessionId(sessionId);
        question.setQuestionNo(2);
        question.setStem("请解释 AQS");
        question.setQuestionType("PRINCIPLE");
        question.setGenerationContextJson(Map.of("domainCode", "java_concurrency"));
        when(questionMapper.selectById(questionId)).thenReturn(question);

        InterviewAttempt latestFinal = buildAttempt(
                3L, sessionId, questionId, true, "latest final",
                "ready",
                Map.of(
                        "score", 88.5,
                        "commentary", "latest",
                        "strengthPoints", java.util.List.of("结构清晰"),
                        "weakPoints", java.util.List.of("缺少量化指标"),
                        "highlightedSegments", java.util.List.of(
                                Map.of("segment", "主要提升了系统性能", "label", "strength", "comment", "建议补充具体指标")
                        ),
                        "idealAnswerOutline", java.util.List.of("定义目标", "说明方案", "给出结果"),
                        "rewrittenAnswer", "更完整的参考答案"
                )
        );
        when(attemptMapper.selectLatestFinalAttempt(sessionId, questionId)).thenReturn(latestFinal);

        InterviewQuestionReviewDto dto = service.getQuestionReview(sessionId, questionId, userId);

        assertEquals("answered", dto.getAnswerStatus());
        assertEquals("ready", dto.getEvaluationStatus());
        assertEquals("latest final", dto.getUserAnswer());
        assertEquals(BigDecimal.valueOf(88.5), dto.getScore());
        assertEquals("latest", dto.getCommentary());
        assertEquals("主要提升了系统性能", dto.getHighlightedSegments().get(0).getSegment());
        assertEquals("Java 并发", dto.getDomainName());
    }

    @Test
    void getQuestionReview_shouldReturnPendingAndNullFieldsWhenNoFinalAttempt() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionReviewService service = new InterviewQuestionReviewService(
                sessionMapper, questionMapper, attemptMapper, new ObjectMapper()
        );

        Long sessionId = 1002L;
        Long questionId = 5002L;
        Long userId = 10L;
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(questionId);
        question.setSessionId(sessionId);
        question.setQuestionNo(1);
        question.setStem("Q");
        question.setQuestionType("INTRO");
        when(questionMapper.selectById(questionId)).thenReturn(question);
        when(attemptMapper.selectLatestFinalAttempt(sessionId, questionId)).thenReturn(null);

        InterviewQuestionReviewDto dto = service.getQuestionReview(sessionId, questionId, userId);

        assertEquals("pending", dto.getAnswerStatus());
        assertEquals("pending", dto.getEvaluationStatus());
        assertNull(dto.getUserAnswer());
        assertNull(dto.getScore());
        assertNull(dto.getCommentary());
    }

    @Test
    void getQuestionReview_shouldTreatNullStatusAsPendingForHistoryData() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionReviewService service = new InterviewQuestionReviewService(
                sessionMapper, questionMapper, attemptMapper, new ObjectMapper()
        );

        Long sessionId = 1003L;
        Long questionId = 5003L;
        Long userId = 11L;
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(questionId);
        question.setSessionId(sessionId);
        question.setQuestionNo(1);
        question.setStem("Q");
        question.setQuestionType("PRINCIPLE");
        when(questionMapper.selectById(questionId)).thenReturn(question);

        InterviewAttempt finalAttempt = buildAttempt(9L, sessionId, questionId, true, "answer", null, null);
        when(attemptMapper.selectLatestFinalAttempt(sessionId, questionId)).thenReturn(finalAttempt);

        InterviewQuestionReviewDto dto = service.getQuestionReview(sessionId, questionId, userId);
        assertEquals("pending", dto.getEvaluationStatus());
        assertNull(dto.getScore());
        assertNull(dto.getCommentary());
    }

    @Test
    void getQuestionReview_shouldThrowWhenSessionNotOwnedByUser() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionReviewService service = new InterviewQuestionReviewService(
                sessionMapper, questionMapper, attemptMapper, new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(99L);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        assertThrows(IllegalArgumentException.class,
                () -> service.getQuestionReview(1L, 2L, 100L));
    }

    private InterviewAttempt buildAttempt(
            Long id,
            Long sessionId,
            Long questionId,
            boolean isFinal,
            String answerText,
            String detailStatus,
            Map<String, Object> detailJson) {
        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setId(id);
        attempt.setSessionId(sessionId);
        attempt.setQuestionId(questionId);
        attempt.setIsFinal(isFinal);
        attempt.setAnswerText(answerText);
        attempt.setDetailEvaluationStatus(detailStatus);
        attempt.setDetailEvaluationJson(detailJson);
        return attempt;
    }
}
