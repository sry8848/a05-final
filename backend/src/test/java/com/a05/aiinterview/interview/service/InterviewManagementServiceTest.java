package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewManagementServiceTest {

    @Test
    void deleteInterview_shouldDeleteSessionAggregateInFixedOrder() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        SessionSkillStateMapper sessionSkillStateMapper = mock(SessionSkillStateMapper.class);
        InterviewManagementService service = new InterviewManagementService(
                sessionMapper,
                reportMapper,
                attemptMapper,
                questionMapper,
                sessionSkillStateMapper
        );

        InterviewSession session = new InterviewSession();
        session.setId(100L);
        session.setUserId(9L);
        when(sessionMapper.selectById(100L)).thenReturn(session);

        service.deleteInterview(100L, 9L);

        InOrder order = inOrder(reportMapper, attemptMapper, questionMapper, sessionSkillStateMapper, sessionMapper);
        order.verify(reportMapper).delete(any());
        order.verify(attemptMapper).delete(any());
        order.verify(questionMapper).delete(any());
        order.verify(sessionSkillStateMapper).delete(any());
        order.verify(sessionMapper).deleteById(100L);
    }

    @Test
    void deleteInterview_shouldRejectForeignSession() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        SessionSkillStateMapper sessionSkillStateMapper = mock(SessionSkillStateMapper.class);
        InterviewManagementService service = new InterviewManagementService(
                sessionMapper,
                reportMapper,
                attemptMapper,
                questionMapper,
                sessionSkillStateMapper
        );

        InterviewSession session = new InterviewSession();
        session.setId(100L);
        session.setUserId(10L);
        when(sessionMapper.selectById(100L)).thenReturn(session);

        assertThrows(IllegalArgumentException.class, () -> service.deleteInterview(100L, 9L));
        verify(reportMapper, never()).delete(any());
    }
}
