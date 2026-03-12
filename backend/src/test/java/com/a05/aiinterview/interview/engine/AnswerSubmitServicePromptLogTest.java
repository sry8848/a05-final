package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.entity.AiInvocationLog;
import com.a05.aiinterview.ai.service.AiInvocationLogService;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("AnswerSubmitService prompt log tests")
class AnswerSubmitServicePromptLogTest {

    @Test
    @DisplayName("recordEvalLog should use prompt metadata from AiCallResult instead of hardcoded version")
    void recordEvalLog_shouldUsePromptMetadataFromResult() {
        AiInvocationLogService aiInvocationLogService = mock(AiInvocationLogService.class);
        AnswerSubmitService service = new AnswerSubmitService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(StateLedgerPatchService.class),
                aiInvocationLogService,
                mock(ReportGenerationService.class),
                new PromptProperties()
        );

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(2L);
        session.setModelProvider("openai");
        session.setModelName("gpt-test");

        InterviewQuestion question = new InterviewQuestion();
        question.setId(3L);

        AiCallResult<EvaluationDecisionOutput> result = AiCallResult.<EvaluationDecisionOutput>builder()
                .output(EvaluationDecisionOutput.builder().signal("NEXT_DOMAIN").build())
                .promptCode("evaluation_decision")
                .promptVersion("v9")
                .promptTokens(11)
                .responseTokens(22)
                .latencyMs(33L)
                .build();

        ReflectionTestUtils.invokeMethod(service, "recordEvalLog", session, question, true, null, result);

        ArgumentCaptor<AiInvocationLog> captor = ArgumentCaptor.forClass(AiInvocationLog.class);
        verify(aiInvocationLogService).saveAsync(captor.capture());
        AiInvocationLog logEntry = captor.getValue();
        assertThat(logEntry.getPromptCode()).isEqualTo("evaluation_decision");
        assertThat(logEntry.getPromptVersion()).isEqualTo("v9");
        assertThat(logEntry.getRequestTokens()).isEqualTo(11);
        assertThat(logEntry.getResponseTokens()).isEqualTo(22);
    }
}
