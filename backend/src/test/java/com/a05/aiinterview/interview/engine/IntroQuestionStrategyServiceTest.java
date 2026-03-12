package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("IntroQuestionStrategyService tests")
class IntroQuestionStrategyServiceTest {

    @Test
    @DisplayName("should avoid variants from recent window and choose never-used first")
    void shouldAvoidRecentVariantsAndChooseNeverUsedFirst() {
        InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
        Long userId = 100L;

        List<InterviewQuestion> recent = List.of(
                introHistory("INTRO_V1", "base-v1", "stem-v1", nowMinusDays(1)),
                introHistory("INTRO_V2", "base-v2", "stem-v2", nowMinusDays(2)),
                introHistory("INTRO_V3", "base-v3", "stem-v3", nowMinusDays(3)),
                introHistory("INTRO_V4", "base-v4", "stem-v4", nowMinusDays(4)),
                introHistory("INTRO_V5", "base-v5", "stem-v5", nowMinusDays(5))
        );
        when(mapper.selectUserFirstIntroQuestions(userId, 5)).thenReturn(recent);
        when(mapper.selectUserFirstIntroQuestions(userId, null)).thenReturn(recent);

        IntroQuestionStrategyService service = new IntroQuestionStrategyService(mapper);
        IntroQuestionStrategyService.IntroQuestionSelection selection = service.selectIntroForUser(userId);

        assertThat(selection.getVariantId()).isEqualTo("INTRO_V6");
        assertThat(selection.getHistoryAvoidCount()).isEqualTo(5);
        assertThat(selection.getRecentPrompts()).containsExactly("base-v1", "base-v2", "base-v3", "base-v4", "base-v5");
    }

    @Test
    @DisplayName("should fallback to least recently used when all variants are in recent deny list")
    void shouldFallbackToLeastRecentlyUsedWhenAllVariantsAreDenied() {
        InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
        Long userId = 200L;

        List<IntroQuestionStrategyService.IntroVariant> variants = List.of(
                new IntroQuestionStrategyService.IntroVariant("A", "prompt-A"),
                new IntroQuestionStrategyService.IntroVariant("B", "prompt-B")
        );

        List<InterviewQuestion> recent = List.of(
                introHistory("A", "base-a", "stem-a", nowMinusDays(1)),
                introHistory("B", "base-b", "stem-b", nowMinusDays(2))
        );
        when(mapper.selectUserFirstIntroQuestions(userId, 5)).thenReturn(recent);
        when(mapper.selectUserFirstIntroQuestions(userId, null)).thenReturn(recent);

        IntroQuestionStrategyService service = new IntroQuestionStrategyService(mapper, variants, 5);
        IntroQuestionStrategyService.IntroQuestionSelection selection = service.selectIntroForUser(userId);

        assertThat(selection.getVariantId()).isEqualTo("B");
    }

    @Test
    @DisplayName("should tolerate missing variantId in history context")
    void shouldTolerateMissingVariantIdInHistoryContext() {
        InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
        Long userId = 300L;

        InterviewQuestion missingVariant = introHistory(null, null, "fallback-stem", nowMinusDays(1));
        when(mapper.selectUserFirstIntroQuestions(userId, 5)).thenReturn(List.of(missingVariant));
        when(mapper.selectUserFirstIntroQuestions(userId, null)).thenReturn(List.of(missingVariant));

        IntroQuestionStrategyService service = new IntroQuestionStrategyService(mapper);
        IntroQuestionStrategyService.IntroQuestionSelection selection = service.selectIntroForUser(userId);

        assertThat(selection.getVariantId()).isNotBlank();
        assertThat(selection.getRecentPrompts()).containsExactly("fallback-stem");
    }

    private InterviewQuestion introHistory(String variantId, String basePromptText, String stem, LocalDateTime createdAt) {
        InterviewQuestion question = new InterviewQuestion();
        question.setQuestionType("INTRO");
        question.setQuestionNo(1);
        question.setStem(stem);
        question.setCreatedAt(createdAt);

        Map<String, Object> ctx = new LinkedHashMap<>();
        if (variantId != null) {
            ctx.put("variantId", variantId);
        }
        if (basePromptText != null) {
            ctx.put("basePromptText", basePromptText);
        }
        question.setGenerationContextJson(ctx);
        return question;
    }

    private LocalDateTime nowMinusDays(long days) {
        return LocalDateTime.now().minusDays(days);
    }
}
