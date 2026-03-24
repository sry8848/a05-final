package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("StateLedgerPatchService debug logging tests")
class StateLedgerPatchServiceDebugLoggingTest {

    @Test
    @DisplayName("logReductionDebug should tolerate null focus values")
    void logReductionDebug_shouldTolerateNullFocusValues() {
        InterviewDebugTraceService debugTraceService = mock(InterviewDebugTraceService.class);
        StateLedgerPatchService service = new StateLedgerPatchService(
                mock(InterviewSessionMapper.class),
                mock(SessionSkillStateMapper.class),
                mock(StateLedgerReducer.class),
                mock(StateLedgerDiffService.class),
                debugTraceService
        );

        InterviewQuestion question = new InterviewQuestion();
        question.setId(173L);

        LedgerMutation mutation = LedgerMutation.builder()
                .interviewAction("WRAPUP")
                .currentFocus(null)
                .nextFocus(null)
                .build();

        ReflectionTestUtils.invokeMethod(
                service,
                "logReductionDebug",
                67L,
                question,
                mutation,
                new LinkedHashMap<String, Object>(),
                Map.of(),
                Map.of(),
                "attempt-1"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> summaryCaptor = ArgumentCaptor.forClass(Map.class);
        verify(debugTraceService).recordQuestionStage(
                eq(67L),
                eq(173L),
                eq("attempt-1"),
                eq("ledgerPatch"),
                any(),
                summaryCaptor.capture()
        );

        assertThat(summaryCaptor.getValue()).containsEntry("currentFocus", "");
        assertThat(summaryCaptor.getValue()).containsEntry("nextFocus", "");
        assertThat(summaryCaptor.getValue()).containsEntry("diffKeys", List.of());
    }

    @Test
    @DisplayName("buildQuestionFamilyId should return null when next plan is absent")
    void buildQuestionFamilyId_shouldReturnNullWhenNextPlanMissing() {
        StateLedgerPatchService service = new StateLedgerPatchService(
                mock(InterviewSessionMapper.class),
                mock(SessionSkillStateMapper.class),
                mock(StateLedgerReducer.class),
                mock(StateLedgerDiffService.class),
                mock(InterviewDebugTraceService.class)
        );

        DecisionExecutionPlan output = DecisionExecutionPlan.builder()
                .interviewAction("WRAPUP")
                .targetQuestionType("")
                .nextFocus("")
                .build();

        String questionFamilyId = ReflectionTestUtils.invokeMethod(service, "buildQuestionFamilyId", output);

        assertThat(questionFamilyId).isNull();
    }
}
