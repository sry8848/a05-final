package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.QuestionConsultInput;
import com.a05.aiinterview.interview.dto.CreateQuestionConsultMessageRequest;
import com.a05.aiinterview.interview.dto.CreateQuestionConsultMessageResponse;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.entity.QuestionConsultMessage;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.QuestionConsultMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionConsultServiceTest {

    @Test
    void createMessage_shouldInsertUserMessageAndAssistantPlaceholder() {
        QuestionConsultMessageMapper consultMapper = mock(QuestionConsultMessageMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        AiClient aiClient = mock(AiClient.class);
        QuestionConsultService service = new QuestionConsultService(
                consultMapper,
                sessionMapper,
                questionMapper,
                attemptMapper,
                aiClient,
                new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(11L);
        session.setUserId(9L);
        when(sessionMapper.selectById(11L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(22L);
        question.setSessionId(11L);
        when(questionMapper.selectById(22L)).thenReturn(question);
        when(consultMapper.selectLatestGenerating(eq(9L), eq(11L), eq(22L))).thenReturn(null);

        doAnswer(invocation -> {
            QuestionConsultMessage inserted = invocation.getArgument(0);
            if ("user".equals(inserted.getRole())) {
                inserted.setId(501L);
            } else {
                inserted.setId(502L);
            }
            return 1;
        }).when(consultMapper).insert(any(QuestionConsultMessage.class));

        CreateQuestionConsultMessageRequest request = new CreateQuestionConsultMessageRequest();
        request.setContent("为什么这题失分？");

        CreateQuestionConsultMessageResponse response = service.createMessage(11L, 22L, 9L, request);

        assertEquals(501L, response.getUserMessageId());
        assertEquals(502L, response.getAssistantMessageId());

        ArgumentCaptor<QuestionConsultMessage> captor = ArgumentCaptor.forClass(QuestionConsultMessage.class);
        verify(consultMapper, times(2)).insert(captor.capture());
        List<QuestionConsultMessage> inserted = captor.getAllValues();
        assertEquals("user", inserted.get(0).getRole());
        assertEquals("ready", inserted.get(0).getStatus());
        assertEquals("assistant", inserted.get(1).getRole());
        assertEquals("generating", inserted.get(1).getStatus());
        assertEquals(501L, inserted.get(1).getReplyToMessageId());
        verify(aiClient, never()).callQuestionConsultStream(any());
    }

    @Test
    void streamAssistantMessage_shouldEmitDoneAndPersistReadyContent() {
        QuestionConsultMessageMapper consultMapper = mock(QuestionConsultMessageMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        AiClient aiClient = mock(AiClient.class);
        QuestionConsultService service = new QuestionConsultService(
                consultMapper,
                sessionMapper,
                questionMapper,
                attemptMapper,
                aiClient,
                new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(11L);
        session.setUserId(9L);
        session.setPositionCode("FRONTEND");
        session.setExperienceLevel("JUNIOR");
        session.setMode("practice");
        when(sessionMapper.selectById(11L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(22L);
        question.setSessionId(11L);
        question.setQuestionType("PRINCIPLE");
        question.setStem("请解释浏览器渲染流水线。");
        question.setExpectedPoints(List.of("Parse", "Layout", "Paint"));
        question.setGenerationContextJson(Map.of("domainCode", "browser_runtime", "domainName", "浏览器运行时"));
        when(questionMapper.selectById(22L)).thenReturn(question);

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setQuestionId(22L);
        attempt.setAnswerText("我会按 parse、layout、paint 来回答。");
        attempt.setDetailEvaluationStatus("ready");
        attempt.setDetailEvaluationJson(Map.of(
                "score", BigDecimal.valueOf(86),
                "commentary", "主线是对的，但边界条件还不够。",
                "strengthPoints", List.of("主流程完整"),
                "weakPoints", List.of("缺少性能边界"),
                "idealAnswerOutline", List.of("定义", "流程", "优化"),
                "rewrittenAnswer", "参考答案"
        ));
        when(attemptMapper.selectLatestFinalAttempt(11L, 22L)).thenReturn(attempt);

        QuestionConsultMessage userMessage = new QuestionConsultMessage();
        userMessage.setId(501L);
        userMessage.setUserId(9L);
        userMessage.setSessionId(11L);
        userMessage.setQuestionId(22L);
        userMessage.setRole("user");
        userMessage.setStatus("ready");
        userMessage.setContent("为什么这题失分？");
        userMessage.setCreatedAt(LocalDateTime.of(2026, 3, 29, 15, 0));

        QuestionConsultMessage assistantMessage = new QuestionConsultMessage();
        assistantMessage.setId(502L);
        assistantMessage.setUserId(9L);
        assistantMessage.setSessionId(11L);
        assistantMessage.setQuestionId(22L);
        assistantMessage.setRole("assistant");
        assistantMessage.setStatus("generating");
        assistantMessage.setReplyToMessageId(501L);
        assistantMessage.setCreatedAt(LocalDateTime.of(2026, 3, 29, 15, 0, 1));

        when(consultMapper.selectById(502L)).thenReturn(assistantMessage);
        when(consultMapper.selectConversation(9L, 11L, 22L)).thenReturn(List.of(userMessage, assistantMessage));
        when(aiClient.callQuestionConsultStream(any())).thenReturn(Flux.just("主要", "是边界", "条件没有展开。"));

        List<ServerSentEvent<String>> events = service.streamAssistantMessage(11L, 22L, 502L, 9L)
                .collectList()
                .block();

        assertNotNull(events);
        assertEquals("start", events.get(0).event());
        assertEquals("delta", events.get(1).event());
        assertEquals("done", events.getLast().event());

        ArgumentCaptor<QuestionConsultInput> inputCaptor = ArgumentCaptor.forClass(QuestionConsultInput.class);
        verify(aiClient).callQuestionConsultStream(inputCaptor.capture());
        QuestionConsultInput input = inputCaptor.getValue();
        assertEquals("FRONTEND", input.getPositionCode());
        assertEquals("JUNIOR", input.getExperienceLevel());
        assertEquals("practice", input.getMode());
        assertEquals("请解释浏览器渲染流水线。", input.getQuestionStem());
        assertEquals("我会按 parse、layout、paint 来回答。", input.getOriginalAnswerText());
        assertEquals("主线是对的，但边界条件还不够。", input.getEvaluationCommentary());
        assertEquals("为什么这题失分？", input.getConsultHistory().getFirst().getContent());

        ArgumentCaptor<QuestionConsultMessage> updateCaptor = ArgumentCaptor.forClass(QuestionConsultMessage.class);
        verify(consultMapper).updateById(updateCaptor.capture());
        QuestionConsultMessage updated = updateCaptor.getValue();
        assertEquals(502L, updated.getId());
        assertEquals("ready", updated.getStatus());
        assertEquals("主要是边界条件没有展开。", updated.getContent());
    }

    @Test
    void streamAssistantMessage_shouldPersistCancelledWhenClientDisconnects() {
        QuestionConsultMessageMapper consultMapper = mock(QuestionConsultMessageMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        AiClient aiClient = mock(AiClient.class);
        QuestionConsultService service = new QuestionConsultService(
                consultMapper,
                sessionMapper,
                questionMapper,
                attemptMapper,
                aiClient,
                new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(11L);
        session.setUserId(9L);
        when(sessionMapper.selectById(11L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(22L);
        question.setSessionId(11L);
        when(questionMapper.selectById(22L)).thenReturn(question);
        when(attemptMapper.selectLatestFinalAttempt(11L, 22L)).thenReturn(null);

        QuestionConsultMessage assistantMessage = new QuestionConsultMessage();
        assistantMessage.setId(502L);
        assistantMessage.setUserId(9L);
        assistantMessage.setSessionId(11L);
        assistantMessage.setQuestionId(22L);
        assistantMessage.setRole("assistant");
        assistantMessage.setStatus("generating");
        when(consultMapper.selectById(502L)).thenReturn(assistantMessage);
        when(consultMapper.selectConversation(9L, 11L, 22L)).thenReturn(List.of());
        when(aiClient.callQuestionConsultStream(any())).thenReturn(
                Flux.just("未完成内容").concatWith(Flux.error(new RuntimeException("cancelled by client")))
        );

        List<ServerSentEvent<String>> events = service.streamAssistantMessage(11L, 22L, 502L, 9L)
                .collectList()
                .block();

        assertNotNull(events);
        assertEquals("error", events.getLast().event());

        ArgumentCaptor<QuestionConsultMessage> updateCaptor = ArgumentCaptor.forClass(QuestionConsultMessage.class);
        verify(consultMapper).updateById(updateCaptor.capture());
        QuestionConsultMessage updated = updateCaptor.getValue();
        assertEquals("cancelled", updated.getStatus());
        assertTrue(updated.getContent().contains("未完成内容"));
    }
}
