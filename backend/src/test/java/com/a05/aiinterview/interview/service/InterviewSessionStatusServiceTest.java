package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewSessionStatusServiceTest {

    @Test
    void resolveAndSync_shouldAbortStaleInProgressSessionWithoutReport() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewSessionStatusService service = new InterviewSessionStatusService(
                sessionMapper, reportMapper, attemptMapper
        );

        InterviewSession session = new InterviewSession();
        session.setId(52L);
        session.setStatus("in_progress");
        session.setStartedAt(LocalDateTime.now().minusHours(3));
        session.setUpdatedAt(LocalDateTime.now().minusHours(3));
        when(sessionMapper.selectById(52L)).thenReturn(session);
        when(reportMapper.selectBySessionId(52L)).thenReturn(null);

        String status = service.resolveAndSync(52L);

        assertEquals("aborted", status);
        verify(sessionMapper).updateById(any(InterviewSession.class));
    }

    @Test
    void resolveAndSync_shouldKeepFreshInProgressSession() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewSessionStatusService service = new InterviewSessionStatusService(
                sessionMapper, reportMapper, attemptMapper
        );

        InterviewSession session = new InterviewSession();
        session.setId(53L);
        session.setStatus("in_progress");
        session.setStartedAt(LocalDateTime.now().minusMinutes(15));
        session.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        when(sessionMapper.selectById(53L)).thenReturn(session);
        when(reportMapper.selectBySessionId(53L)).thenReturn(null);

        String status = service.resolveAndSync(53L);

        assertEquals("in_progress", status);
    }
}
