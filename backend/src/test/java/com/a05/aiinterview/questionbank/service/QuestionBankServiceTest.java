package com.a05.aiinterview.questionbank.service;

import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.questionbank.dto.QuestionBankCreateRequest;
import com.a05.aiinterview.questionbank.dto.QuestionBankItemDto;
import com.a05.aiinterview.questionbank.entity.QuestionBankItem;
import com.a05.aiinterview.questionbank.mapper.QuestionBankItemMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionBankServiceTest {

    @Test
    void collect_shouldReturnExistingItemWhenIdempotentHit() {
        QuestionBankItemMapper itemMapper = mock(QuestionBankItemMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        QuestionBankService service = new QuestionBankService(itemMapper, sessionMapper, questionMapper, attemptMapper);

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(9L);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(2L);
        question.setSessionId(1L);
        when(questionMapper.selectById(2L)).thenReturn(question);

        QuestionBankItem existing = new QuestionBankItem();
        existing.setId(99L);
        existing.setUserId(9L);
        existing.setSessionId(1L);
        existing.setQuestionId(2L);
        existing.setSourceSnapshotJson(Map.of("questionStem", "Q", "domainName", "并发"));
        when(itemMapper.selectOne(any())).thenReturn(existing);

        QuestionBankCreateRequest request = new QuestionBankCreateRequest();
        request.setSessionId(1L);
        request.setQuestionId(2L);
        QuestionBankItemDto dto = service.collect(9L, request);

        assertEquals(99L, dto.getId());
        verify(itemMapper, never()).insert(any(QuestionBankItem.class));
    }

    @Test
    void collect_shouldInsertSnapshotAndAllowNullScore() {
        QuestionBankItemMapper itemMapper = mock(QuestionBankItemMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        QuestionBankService service = new QuestionBankService(itemMapper, sessionMapper, questionMapper, attemptMapper);

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(9L);
        session.setCreatedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        when(sessionMapper.selectById(1L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(2L);
        question.setSessionId(1L);
        question.setStem("请解释线程池拒绝策略");
        question.setQuestionType("PRINCIPLE");
        question.setGenerationContextJson(Map.of("domainCode", "concurrency", "focusPoint", "并发"));
        when(questionMapper.selectById(2L)).thenReturn(question);

        when(itemMapper.selectOne(any())).thenReturn(null);
        when(attemptMapper.selectLatestFinalAttempt(1L, 2L)).thenReturn(new InterviewAttempt());

        doAnswer(invocation -> {
            QuestionBankItem inserted = invocation.getArgument(0);
            inserted.setId(123L);
            return 1;
        }).when(itemMapper).insert(any(QuestionBankItem.class));

        QuestionBankCreateRequest request = new QuestionBankCreateRequest();
        request.setSessionId(1L);
        request.setQuestionId(2L);
        request.setTag("并发");
        QuestionBankItemDto dto = service.collect(9L, request);

        assertEquals(123L, dto.getId());
        assertNull(dto.getScore());

        ArgumentCaptor<QuestionBankItem> itemCaptor = ArgumentCaptor.forClass(QuestionBankItem.class);
        verify(itemMapper).insert(itemCaptor.capture());
        Map<String, Object> snapshot = itemCaptor.getValue().getSourceSnapshotJson();
        assertEquals("请解释线程池拒绝策略", snapshot.get("questionStem"));
        assertEquals("concurrency", snapshot.get("domainCode"));
        assertEquals("PRINCIPLE", snapshot.get("questionType"));
        assertEquals("2026-03-01T09:00", snapshot.get("sourceCreatedAt"));
        assertEquals(true, snapshot.containsKey("domainCode"));
        assertEquals("并发", snapshot.get("focusPoint"));
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("questionStem", "domainCode", "domainName", "questionType", "focusPoint", "answerSummary", "sourceCreatedAt"),
                snapshot.keySet()
        );
    }

    @Test
    void collect_shouldUseIntroDisplayNameForIntroQuestion() {
        QuestionBankItemMapper itemMapper = mock(QuestionBankItemMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        QuestionBankService service = new QuestionBankService(itemMapper, sessionMapper, questionMapper, attemptMapper);

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(9L);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(2L);
        question.setSessionId(1L);
        question.setQuestionType("INTRO");
        question.setDomainCode("intro");
        question.setStem("请先做一个简短的自我介绍");
        question.setGenerationContextJson(Map.of("domainCode", "intro", "focusPoint", "沟通表达与项目概述"));
        when(questionMapper.selectById(2L)).thenReturn(question);

        when(itemMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            QuestionBankItem inserted = invocation.getArgument(0);
            inserted.setId(124L);
            return 1;
        }).when(itemMapper).insert(any(QuestionBankItem.class));

        QuestionBankCreateRequest request = new QuestionBankCreateRequest();
        request.setSessionId(1L);
        request.setQuestionId(2L);

        QuestionBankItemDto dto = service.collect(9L, request);

        assertEquals(124L, dto.getId());

        ArgumentCaptor<QuestionBankItem> itemCaptor = ArgumentCaptor.forClass(QuestionBankItem.class);
        verify(itemMapper).insert(itemCaptor.capture());
        assertEquals("intro", itemCaptor.getValue().getDomainCode());
        assertEquals("自我介绍", itemCaptor.getValue().getSourceSnapshotJson().get("domainName"));
        assertEquals("沟通表达与项目概述", itemCaptor.getValue().getSourceSnapshotJson().get("focusPoint"));
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("questionStem", "domainCode", "domainName", "questionType", "focusPoint", "answerSummary", "sourceCreatedAt"),
                itemCaptor.getValue().getSourceSnapshotJson().keySet()
        );
    }

    @Test
    void delete_shouldRejectWhenOwnerMismatch() {
        QuestionBankItemMapper itemMapper = mock(QuestionBankItemMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        QuestionBankService service = new QuestionBankService(itemMapper, sessionMapper, questionMapper, attemptMapper);

        QuestionBankItem item = new QuestionBankItem();
        item.setId(5L);
        item.setUserId(100L);
        when(itemMapper.selectById(5L)).thenReturn(item);

        assertThrows(IllegalArgumentException.class, () -> service.delete(9L, 5L));
    }
}
