package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FirstQuestionGenerationService fallback tests")
class FirstQuestionGenerationServicePromptLogTest {

    @Test
    @DisplayName("intro rewrite throws exception should fallback to base prompt and keep response stable")
    void generateAndSave_shouldFallbackWhenRewriteThrows() {
        AiClient aiClient = mock(AiClient.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        IntroQuestionStrategyService strategyService = mock(IntroQuestionStrategyService.class);
        ListAppender<ILoggingEvent> appender = startLogCapture();

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
                questionMapper
        );

        InterviewQuestion saved = service.generateAndSave(buildSession(), new PlannerOutput());

        assertThat(saved.getQuestionType()).isEqualTo("INTRO");
        assertThat(saved.getStem()).isEqualTo("base-prompt");
        assertThat(saved.getGenerationContextJson().get("rewritten")).isEqualTo(false);
        assertThat(saved.getGenerationContextJson().get("aiResultStatus")).isEqualTo("fallback");
        assertThat(saved.getGenerationContextJson().get("fallbackReason")).isEqualTo("exception");

        String logLine = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(msg -> msg.contains("ai_lite_audit="))
                .findFirst()
                .orElse("");
        assertThat(logLine).contains("\"status\":\"fallback\"");
        assertThat(logLine).contains("\"promptCode\":\"intro_rewrite\"");
        assertThat(logLine).contains("\"interviewId\":1");
        assertThat(logLine).contains("\"fallbackReason\":\"exception\"");
    }

    @Test
    @DisplayName("intro rewrite returns invalid output should fallback to base prompt")
    void generateAndSave_shouldFallbackWhenRewriteOutputInvalid() {
        AiClient aiClient = mock(AiClient.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        IntroQuestionStrategyService strategyService = mock(IntroQuestionStrategyService.class);

        when(strategyService.selectIntroForUser(2L)).thenReturn(IntroQuestionStrategyService.IntroQuestionSelection.builder()
                .variantId("INTRO_V3")
                .basePrompt("base-intro")
                .recentPrompts(List.of())
                .avoidPhrases(List.of())
                .historyAvoidCount(0)
                .build());
        String overLongRewrite = "A".repeat(181);
        when(aiClient.callIntroRewrite(any(IntroRewriteInput.class))).thenReturn(AiCallResult.<String>builder()
                .output(overLongRewrite)
                .promptCode("intro_rewrite")
                .promptVersion("v1")
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
                questionMapper
        );

        InterviewQuestion saved = service.generateAndSave(buildSession(), new PlannerOutput());

        assertThat(saved.getQuestionType()).isEqualTo("INTRO");
        assertThat(saved.getStem()).isEqualTo("base-intro");
        assertThat(saved.getGenerationContextJson().get("rewritten")).isEqualTo(false);
        assertThat(saved.getGenerationContextJson().get("aiResultStatus")).isEqualTo("fallback");
        assertThat(saved.getGenerationContextJson().get("fallbackReason")).isEqualTo("too_long_output");
        assertThat(saved.getGenerationContextJson().get("rewritePromptCode")).isEqualTo("intro_rewrite");
        assertThat(saved.getGenerationContextJson().get("rewritePromptVersion")).isEqualTo("v1");
    }

    private InterviewSession buildSession() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(2L);
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("SENIOR");
        session.setMode("professional");
        session.setModelProvider("openai");
        session.setModelName("gpt-test");
        return session;
    }

    private ListAppender<ILoggingEvent> startLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(FirstQuestionGenerationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
