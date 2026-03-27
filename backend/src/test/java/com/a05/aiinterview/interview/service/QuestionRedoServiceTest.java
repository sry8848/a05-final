package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.QuestionRedoAttemptDto;
import com.a05.aiinterview.interview.dto.QuestionRedoAttemptRequest;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.entity.QuestionRedoAttempt;
import com.a05.aiinterview.interview.event.QuestionRedoAttemptPersistedEvent;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.QuestionRedoAttemptMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionRedoServiceTest {

    @Test
    void createRedoAttempt_shouldFreezeSourceSnapshotAndPublishEvent() {
        QuestionRedoAttemptMapper redoMapper = mock(QuestionRedoAttemptMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        QuestionRedoService service = new QuestionRedoService(redoMapper, sessionMapper, questionMapper, eventPublisher);

        InterviewSession session = new InterviewSession();
        session.setId(11L);
        session.setUserId(9L);
        session.setTargetRole("FRONTEND");
        session.setExperienceLevel("JUNIOR");
        session.setMode("practice");
        session.setSyllabusJson(Map.of(
                "domains", List.of(Map.of("domainCode", "browser", "domainName", "浏览器原理"))
        ));
        when(sessionMapper.selectById(11L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(22L);
        question.setSessionId(11L);
        question.setQuestionNo(3);
        question.setQuestionType("PRINCIPLE");
        question.setDomainCode("browser");
        question.setStem("请解释浏览器渲染流水线。");
        question.setTargetSkill("渲染流水线");
        question.setExpectedPoints(List.of("Parse", "Layout", "Paint"));
        question.setGenerationContextJson(Map.of("domainCode", "browser"));
        when(questionMapper.selectById(22L)).thenReturn(question);

        doAnswer(invocation -> {
            QuestionRedoAttempt inserted = invocation.getArgument(0);
            inserted.setId(1001L);
            return 1;
        }).when(redoMapper).insert(any(QuestionRedoAttempt.class));

        QuestionRedoAttemptRequest request = new QuestionRedoAttemptRequest();
        request.setAnswerText("我会先从 HTML 解析讲起。");

        QuestionRedoAttemptDto response = service.createRedoAttempt(11L, 22L, 9L, request);

        assertEquals(1001L, response.getRedoAttemptId());
        assertEquals("pending", response.getEvaluationStatus());
        assertEquals("我会先从 HTML 解析讲起。", response.getAnswerText());
        assertNull(response.getScore());

        ArgumentCaptor<QuestionRedoAttempt> captor = ArgumentCaptor.forClass(QuestionRedoAttempt.class);
        verify(redoMapper).insert(captor.capture());
        QuestionRedoAttempt inserted = captor.getValue();
        assertEquals(9L, inserted.getUserId());
        assertEquals(11L, inserted.getSourceSessionId());
        assertEquals(22L, inserted.getSourceQuestionId());
        assertEquals("pending", inserted.getEvaluationStatus());
        assertNotNull(inserted.getSourceSnapshotJson());
        assertEquals("请解释浏览器渲染流水线。", inserted.getSourceSnapshotJson().get("questionStem"));
        assertEquals("PRINCIPLE", inserted.getSourceSnapshotJson().get("questionType"));
        assertEquals("浏览器原理", inserted.getSourceSnapshotJson().get("domainName"));
        assertEquals(List.of("Parse", "Layout", "Paint"), inserted.getSourceSnapshotJson().get("expectedPoints"));
        assertEquals("FRONTEND", inserted.getSourceSnapshotJson().get("positionCode"));
        assertEquals("JUNIOR", inserted.getSourceSnapshotJson().get("experienceLevel"));
        assertEquals("practice", inserted.getSourceSnapshotJson().get("mode"));

        verify(eventPublisher).publishEvent(any(QuestionRedoAttemptPersistedEvent.class));
    }

    @Test
    void getLatestRedoAttempt_shouldMapReadyEvaluationJson() {
        QuestionRedoAttemptMapper redoMapper = mock(QuestionRedoAttemptMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        QuestionRedoService service = new QuestionRedoService(redoMapper, sessionMapper, questionMapper, eventPublisher);

        InterviewSession session = new InterviewSession();
        session.setId(11L);
        session.setUserId(9L);
        when(sessionMapper.selectById(11L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(22L);
        question.setSessionId(11L);
        when(questionMapper.selectById(22L)).thenReturn(question);

        QuestionRedoAttempt attempt = new QuestionRedoAttempt();
        attempt.setId(1002L);
        attempt.setUserId(9L);
        attempt.setSourceSessionId(11L);
        attempt.setSourceQuestionId(22L);
        attempt.setAnswerText("新的回答");
        attempt.setEvaluationStatus("ready");
        attempt.setCreatedAt(LocalDateTime.of(2026, 3, 21, 18, 0));
        attempt.setEvaluationJson(Map.of(
                "score", BigDecimal.valueOf(88),
                "commentary", "结构更完整",
                "strengthPoints", List.of("讲清了主流程"),
                "weakPoints", List.of("缺少性能边界"),
                "idealAnswerOutline", List.of("定义", "流程", "优化"),
                "rewrittenAnswer", "参考答案",
                "evaluatedDomains", List.of(Map.of(
                        "domainCode", "browser",
                        "domainName", "浏览器原理",
                        "score", BigDecimal.valueOf(88),
                        "commentary", "主域表现良好"
                )),
                "highlightedSegments", List.of(Map.of(
                        "segment", "先说解析再说布局",
                        "label", "strength",
                        "comment", "结构顺序合理"
                ))
        ));
        when(redoMapper.selectLatestBySource(eq(9L), eq(11L), eq(22L))).thenReturn(attempt);

        QuestionRedoAttemptDto response = service.getLatestRedoAttempt(11L, 22L, 9L);

        assertEquals(1002L, response.getRedoAttemptId());
        assertEquals("ready", response.getEvaluationStatus());
        assertEquals("新的回答", response.getAnswerText());
        assertEquals(BigDecimal.valueOf(88), response.getScore());
        assertEquals("结构更完整", response.getCommentary());
        assertEquals(List.of("讲清了主流程"), response.getStrengthPoints());
        assertEquals(List.of("缺少性能边界"), response.getWeakPoints());
        assertEquals("参考答案", response.getRewrittenAnswer());
        assertEquals(1, response.getEvaluatedDomains().size());
        assertEquals("浏览器原理", response.getEvaluatedDomains().get(0).getDomainName());
        assertEquals(1, response.getHighlightedSegments().size());
        assertEquals("先说解析再说布局", response.getHighlightedSegments().get(0).getSegment());
    }
}
