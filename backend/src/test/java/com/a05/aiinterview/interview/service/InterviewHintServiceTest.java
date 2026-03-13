package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewHintResponse;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewHintServiceTest {

    @Test
    void getHint_shouldReuseCachedHintWhenExists() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewHintService service = new InterviewHintService(sessionMapper, questionMapper);

        InterviewSession session = new InterviewSession();
        session.setId(10L);
        session.setUserId(7L);
        when(sessionMapper.selectById(10L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(20L);
        question.setSessionId(10L);
        question.setHintText("已有提示");
        when(questionMapper.selectById(20L)).thenReturn(question);

        InterviewHintResponse response = service.getHint(10L, 20L, 7L);
        assertEquals("cached", response.getSource());
        assertEquals("已有提示", response.getHintText());
        verify(questionMapper, never()).updateById(any(InterviewQuestion.class));
    }

    @Test
    void getHint_shouldGenerateAndPersistWhenMissing() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewHintService service = new InterviewHintService(sessionMapper, questionMapper);

        InterviewSession session = new InterviewSession();
        session.setId(11L);
        session.setUserId(8L);
        when(sessionMapper.selectById(11L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(21L);
        question.setSessionId(11L);
        question.setExpectedPoints(List.of("线程安全", "锁粒度", "性能影响"));
        when(questionMapper.selectById(21L)).thenReturn(question);

        InterviewHintResponse response = service.getHint(11L, 21L, 8L);
        assertEquals("generated", response.getSource());
        assertEquals(21L, response.getQuestionId());
        assertEquals(true, response.getHintText().contains("线程安全"));

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).updateById(captor.capture());
        assertEquals(21L, captor.getValue().getId());
        assertEquals(true, captor.getValue().getHintText().contains("线程安全"));
    }

    @Test
    void getHint_shouldRejectQuestionOutsideSession() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewHintService service = new InterviewHintService(sessionMapper, questionMapper);

        InterviewSession session = new InterviewSession();
        session.setId(12L);
        session.setUserId(9L);
        when(sessionMapper.selectById(12L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(22L);
        question.setSessionId(99L);
        when(questionMapper.selectById(22L)).thenReturn(question);

        assertThrows(IllegalArgumentException.class, () -> service.getHint(12L, 22L, 9L));
    }
}
