package com.a05.aiinterview.interview;

import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.speech.service.TtsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewSpeechControllerTest {

    @Test
    void downloadAttemptSegmentAudio_shouldReturnAudioBytesWhenAuthorized() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        TtsService ttsService = mock(TtsService.class);

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(7L);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setAttemptId("attempt-1");
        attempt.setSessionId(1L);
        when(attemptMapper.selectByAttemptId("attempt-1")).thenReturn(attempt);
        when(ttsService.getAttemptSegmentAudioBytes(1L, "attempt-1", 0)).thenReturn(new byte[]{1, 2, 3});

        InterviewSpeechController controller =
                new InterviewSpeechController(sessionMapper, questionMapper, attemptMapper, ttsService);

        var response = controller.downloadAttemptSegmentAudio(7L, 1L, "attempt-1", 0);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
    }

    @Test
    void downloadAttemptSegmentAudio_shouldRejectWhenSessionUnauthorized() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        TtsService ttsService = mock(TtsService.class);

        InterviewSpeechController controller =
                new InterviewSpeechController(sessionMapper, questionMapper, attemptMapper, ttsService);

        assertThrows(IllegalArgumentException.class,
                () -> controller.downloadAttemptSegmentAudio(7L, 1L, "attempt-1", 0));
    }
}
