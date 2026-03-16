package com.a05.aiinterview.interview.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import com.a05.aiinterview.speech.service.TtsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QuestionStreamServiceBuildInputTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildGenInput_shouldFallbackRagContextAndNormalizeDifficulty() throws Exception {
        QuestionStreamService service = new QuestionStreamService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(RagRetrievalService.class),
                mock(TtsService.class),
                mock(StringRedisTemplate.class),
                new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(100L);
        session.setTargetRole("JAVA_BACKEND");
        session.setMode("professional");
        session.setExperienceLevel("SENIOR");
        session.setSyllabusJson(Map.of("domains", List.of()));

        EvaluationDecisionOutput.NextQuestionStrategy strategy =
                EvaluationDecisionOutput.NextQuestionStrategy.builder()
                        .nextDomainId(1L)
                        .nextDomainCode("concurrency")
                        .nextDomainName("并发编程")
                        .questionType("PRINCIPLE")
                        .targetDepth("L3")
                        .difficulty("medium")
                        .targetSkill("线程池调优")
                        .expectedPoints(List.of("核心参数", "拒绝策略"))
                        .focusPoint("线程池")
                        .build();

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "buildGenInput",
                InterviewSession.class,
                EvaluationDecisionOutput.NextQuestionStrategy.class,
                List.class,
                RagContext.class
        );
        method.setAccessible(true);

        QuestionGenerationInput input = (QuestionGenerationInput) method.invoke(
                service,
                session,
                strategy,
                List.of(),
                RagContext.empty()
        );

        assertEquals("无外部参考资料，请严格依赖你自身的工程师知识库进行出题。", input.getRagContext());
        assertEquals("L3", input.getDifficulty());
    }

    @Test
    void questionGenerationDebugLogs_shouldContainStrategyInputAndFinalStem() throws Exception {
        QuestionStreamService service = new QuestionStreamService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(RagRetrievalService.class),
                mock(TtsService.class),
                mock(StringRedisTemplate.class),
                new ObjectMapper()
        );
        ListAppender<ILoggingEvent> appender = startLogCapture();

        InterviewSession session = new InterviewSession();
        session.setId(200L);
        session.setTargetRole("JAVA_BACKEND");
        session.setMode("practice");
        session.setExperienceLevel("FRESH_GRAD");
        session.setSyllabusJson(Map.of("domains", List.of()));

        EvaluationDecisionOutput.NextQuestionStrategy strategy =
                EvaluationDecisionOutput.NextQuestionStrategy.builder()
                        .nextDomainId(6L)
                        .nextDomainCode("redis")
                        .nextDomainName("Redis")
                        .questionType("PRINCIPLE")
                        .targetDepth("L1")
                        .difficulty("L1")
                        .targetSkill("缓存击穿")
                        .expectedPoints(List.of("说明缓存击穿方案"))
                        .focusPoint("缓存击穿")
                        .build();

        Method buildInput = QuestionStreamService.class.getDeclaredMethod(
                "buildGenInput",
                InterviewSession.class,
                EvaluationDecisionOutput.NextQuestionStrategy.class,
                List.class,
                RagContext.class
        );
        buildInput.setAccessible(true);
        QuestionGenerationInput input = (QuestionGenerationInput) buildInput.invoke(
                service, session, strategy, List.of(), RagContext.empty());

        Method logInput = QuestionStreamService.class.getDeclaredMethod(
                "logQuestionGenerationDebugInput",
                Long.class,
                String.class,
                EvaluationDecisionOutput.NextQuestionStrategy.class,
                QuestionGenerationInput.class
        );
        logInput.setAccessible(true);
        logInput.invoke(service, 200L, "attempt-200", strategy, input);

        Method logOutput = QuestionStreamService.class.getDeclaredMethod(
                "logQuestionGenerationDebugOutput",
                Long.class,
                String.class,
                EvaluationDecisionOutput.NextQuestionStrategy.class,
                String.class
        );
        logOutput.setAccessible(true);
        logOutput.invoke(service, 200L, "attempt-200", strategy, "Redis 缓存击穿一般怎么处理？");

        List<String> logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(logs).anyMatch(msg -> msg.contains("出题调试输入")
                && msg.contains("\"nextDomainCode\":\"redis\"")
                && msg.contains("\"targetSkill\":\"缓存击穿\""));
        assertThat(logs).anyMatch(msg -> msg.contains("出题调试输出")
                && msg.contains("Redis 缓存击穿一般怎么处理？"));
    }

    private ListAppender<ILoggingEvent> startLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(QuestionStreamService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
