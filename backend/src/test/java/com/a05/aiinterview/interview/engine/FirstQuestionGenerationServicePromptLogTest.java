package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.ai.entity.AiInvocationLog;
import com.a05.aiinterview.ai.service.AiInvocationLogService;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FirstQuestionGenerationService prompt log tests")
class FirstQuestionGenerationServicePromptLogTest {

    @Test
    @DisplayName("generateAndSave should force INTRO even if planner has no INTRO quota")
    void generateAndSave_shouldForceIntroEvenWithoutPlannerQuota() {
        AiClient aiClient = mock(AiClient.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        AiInvocationLogService logService = mock(AiInvocationLogService.class);
        IntroQuestionStrategyService strategyService = mock(IntroQuestionStrategyService.class);
        when(strategyService.selectIntroForUser(2L)).thenReturn(IntroQuestionStrategyService.IntroQuestionSelection.builder()
                .variantId("INTRO_V1")
                .basePrompt("base-intro")
                .recentPrompts(List.of())
                .avoidPhrases(List.of())
                .historyAvoidCount(0)
                .build());
        when(aiClient.callIntroRewrite(any(IntroRewriteInput.class))).thenReturn(AiCallResult.<String>builder()
                .output("rewritten-intro")
                .promptCode("intro_rewrite")
                .promptVersion("v7")
                .promptTokens(10)
                .responseTokens(20)
                .latencyMs(30L)
                .build());
        doAnswer(invocation -> {
            InterviewQuestion question = invocation.getArgument(0);
            question.setId(123L);
            return 1;
        }).when(questionMapper).insert(any(InterviewQuestion.class));

        FirstQuestionGenerationService service = new FirstQuestionGenerationService(
                aiClient,
                new PromptProperties(),
                strategyService,
                questionMapper,
                logService
        );

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(2L);
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("SENIOR");
        session.setMode("professional");
        session.setModelProvider("openai");
        session.setModelName("gpt-test");

        PlannerOutput plannerOutput = new PlannerOutput();
        plannerOutput.setQuestionMixPlan(Map.of("PRINCIPLE", 3));
        plannerOutput.setDomains(List.of());

        InterviewQuestion saved = service.generateAndSave(session, plannerOutput);

        assertThat(saved.getQuestionType()).isEqualTo("INTRO");
        assertThat(saved.getStem()).isEqualTo("rewritten-intro");
        assertThat(saved.getGenerationContextJson().get("rewritten")).isEqualTo(true);
        assertThat(saved.getGenerationContextJson().get("variantId")).isEqualTo("INTRO_V1");

        ArgumentCaptor<AiInvocationLog> captor = ArgumentCaptor.forClass(AiInvocationLog.class);
        verify(logService).saveAsync(captor.capture());
        assertThat(captor.getValue().getPromptCode()).isEqualTo("intro_rewrite");
        assertThat(captor.getValue().getPromptVersion()).isEqualTo("v7");
    }

    @Test
    @DisplayName("generateAndSave should fallback to base prompt when intro rewrite fails")
    void generateAndSave_shouldFallbackToBasePromptWhenRewriteFails() {
        AiClient aiClient = mock(AiClient.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        AiInvocationLogService logService = mock(AiInvocationLogService.class);
        IntroQuestionStrategyService strategyService = mock(IntroQuestionStrategyService.class);

        when(strategyService.selectIntroForUser(2L)).thenReturn(IntroQuestionStrategyService.IntroQuestionSelection.builder()
                .variantId("INTRO_V2")
                .basePrompt("base-prompt")
                .recentPrompts(List.of("history-1"))
                .avoidPhrases(List.of("history-1"))
                .historyAvoidCount(1)
                .build());
        when(aiClient.callIntroRewrite(any(IntroRewriteInput.class)))
                .thenThrow(new RuntimeException("rewrite failed"));
        doAnswer(invocation -> {
            InterviewQuestion question = invocation.getArgument(0);
            question.setId(321L);
            return 1;
        }).when(questionMapper).insert(any(InterviewQuestion.class));

        FirstQuestionGenerationService service = new FirstQuestionGenerationService(
                aiClient,
                new PromptProperties(),
                strategyService,
                questionMapper,
                logService
        );

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(2L);
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("SENIOR");
        session.setMode("professional");
        session.setModelProvider("openai");
        session.setModelName("gpt-test");

        PlannerOutput plannerOutput = new PlannerOutput();
        plannerOutput.setQuestionMixPlan(Map.of("INTRO", 1));

        InterviewQuestion saved = service.generateAndSave(session, plannerOutput);

        assertThat(saved.getStem()).isEqualTo("base-prompt");
        assertThat(saved.getGenerationContextJson().get("rewritten")).isEqualTo(false);
        assertThat(saved.getGenerationContextJson().get("rewritePromptCode")).isEqualTo("intro_rewrite");
        assertThat(saved.getGenerationContextJson().get("rewritePromptVersion")).isEqualTo("v1");

        ArgumentCaptor<AiInvocationLog> captor = ArgumentCaptor.forClass(AiInvocationLog.class);
        verify(logService).saveAsync(captor.capture());
        assertThat(captor.getValue().getPromptCode()).isEqualTo("intro_rewrite");
        assertThat(captor.getValue().getSuccess()).isFalse();
        verify(aiClient, never()).callQuestionGeneration(any());
    }
}
