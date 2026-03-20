package com.a05.aiinterview.interview.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
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
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class QuestionStreamServiceBuildInputTest {

    @Test
    void extractNextQuestionPlan_shouldPreserveDistinctFocusFields() throws Exception {
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

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setAttemptId("attempt-preserve-focus");
        Map<String, Object> evaluationJson = new LinkedHashMap<>();
        evaluationJson.put("decision", "rescue");
        evaluationJson.put("nextDomainId", 0L);
        evaluationJson.put("nextDomainCode", "intro");
        evaluationJson.put("nextDomainName", "intro");
        evaluationJson.put("questionType", "INTRO");
        evaluationJson.put("targetFocus", "项目名称一致性与实操细节验证");
        evaluationJson.put("focusPoint", "项目真实性与职责边界");
        evaluationJson.put("targetAngle", "role");
        evaluationJson.put("difficultyAdjustment", "down");
        evaluationJson.put("nextQuestionGoal", "请确认你提到的项目是否就是简历里的 Chabst，并给出一个你亲手做过的接口或排障细节。");
        evaluationJson.put("statePatch", Map.of(
                "activeProjectId", "p_001",
                "currentFocus", "project_identity"
        ));
        evaluationJson.put("retrievalIntent", Map.of(
                "domainHint", "intro",
                "focusQuery", "项目名称校验+职责具象化",
                "questionTypeHint", "INTRO",
                "avoidRecentFamilies", List.of()
        ));
        attempt.setEvaluationJson(evaluationJson);

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "extractNextQuestionPlan",
                InterviewAttempt.class
        );
        method.setAccessible(true);

        QuestionStreamService.NextQuestionPlan plan =
                (QuestionStreamService.NextQuestionPlan) method.invoke(service, attempt);

        assertThat(plan.getTargetFocus()).isEqualTo("项目名称一致性与实操细节验证");
        assertThat(plan.getFocusPoint()).isEqualTo("项目真实性与职责边界");
        assertThat(plan.getRetrievalIntent()).isNotNull();
        assertThat(plan.getRetrievalIntent().getFocusQuery()).isEqualTo("项目名称校验+职责具象化");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildGenInput_shouldBuildGoalDrivenInputAndFallbackRetrievalContext() throws Exception {
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
        session.setStateLedgerJson(Map.of(
                "active_project_id", "p_order",
                "current_focus", "订单超时关闭",
                "recent_question_families", List.of("mq.delay-message.definition")
        ));

        QuestionStreamService.NextQuestionPlan plan =
                QuestionStreamService.NextQuestionPlan.builder()
                        .nextDomainId(1L)
                        .nextDomainCode("concurrency")
                        .nextDomainName("并发编程")
                        .questionType("PROJECT_DEEP_DIVE")
                        .focusPoint("支付回调与关单并发冲突")
                        .targetFocus("支付回调与关单并发冲突")
                        .targetAngle("boundary")
                        .decision("followup")
                        .difficultyAdjustment("same")
                        .nextQuestionGoal("继续在订单项目里验证并发冲突处理能力")
                        .retrievalIntent(QuestionStreamService.RetrievalPlan.builder()
                                .domainHint("mq")
                                .focusQuery("支付回调 关单 并发冲突 幂等")
                                .questionTypeHint("PROJECT_DEEP_DIVE")
                                .avoidRecentFamilies(List.of("mq.delay-message.definition"))
                                .build())
                        .activeProjectId("p_order")
                        .currentFocus("订单超时关闭")
                        .build();

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "buildGenInput",
                InterviewSession.class,
                QuestionStreamService.NextQuestionPlan.class,
                List.class,
                RagContext.class
        );
        method.setAccessible(true);

        QuestionGenerationInput input = (QuestionGenerationInput) method.invoke(
                service,
                session,
                plan,
                List.of(),
                RagContext.empty()
        );

        assertEquals("SENIOR", input.getRoleContext().getCandidateLevel());
        assertEquals("p_order", input.getProjectContext().getActiveProjectId());
        assertEquals("订单超时关闭", input.getProjectContext().getCurrentFocus());
        assertEquals("followup", input.getNextQuestionGoal().getDecision());
        assertEquals("支付回调与关单并发冲突", input.getNextQuestionGoal().getTargetFocus());
        assertEquals("支付回调 关单 并发冲突 幂等", input.getRetrievalContext().getQuery());
        assertEquals("无外部参考资料，请严格依赖你自身的工程师知识库进行出题。", input.getRetrievalContext().getRagContext());
        assertThat(input.getExpectedPoints()).isEmpty();
        assertThat(input.getConstraints().getAvoidRepetitionFamilies()).containsExactly("mq.delay-message.definition");
    }

    @Test
    void questionGenerationDebugLogs_shouldContainPlanInputAndFinalStem() throws Exception {
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
        setField(service, "interviewDebugEnabled", true);
        setField(service, "interviewDebugMaxTextChars", 2000);
        ListAppender<ILoggingEvent> appender = startLogCapture();

        InterviewSession session = new InterviewSession();
        session.setId(200L);
        session.setTargetRole("JAVA_BACKEND");
        session.setMode("practice");
        session.setExperienceLevel("FRESH_GRAD");
        session.setSyllabusJson(Map.of("domains", List.of()));
        session.setStateLedgerJson(Map.of(
                "active_project_id", "p_cache",
                "current_focus", "缓存击穿处理"
        ));

        QuestionStreamService.NextQuestionPlan plan =
                QuestionStreamService.NextQuestionPlan.builder()
                        .nextDomainId(6L)
                        .nextDomainCode("redis")
                        .nextDomainName("Redis")
                        .questionType("PRINCIPLE")
                        .focusPoint("缓存击穿")
                        .targetFocus("缓存击穿")
                        .targetAngle("implementation")
                        .decision("broaden")
                        .difficultyAdjustment("same")
                        .nextQuestionGoal("补一个 Redis 缓存击穿基础题")
                        .retrievalIntent(QuestionStreamService.RetrievalPlan.builder()
                                .domainHint("redis")
                                .focusQuery("缓存击穿 热点 key 失效")
                                .questionTypeHint("PRINCIPLE")
                                .avoidRecentFamilies(List.of("project.cache.hit.definition"))
                                .build())
                        .activeProjectId("p_cache")
                        .currentFocus("缓存击穿处理")
                        .build();

        Method buildInput = QuestionStreamService.class.getDeclaredMethod(
                "buildGenInput",
                InterviewSession.class,
                QuestionStreamService.NextQuestionPlan.class,
                List.class,
                RagContext.class
        );
        buildInput.setAccessible(true);
        QuestionGenerationInput input = (QuestionGenerationInput) buildInput.invoke(
                service, session, plan, List.of(), RagContext.empty());

        Method logInput = QuestionStreamService.class.getDeclaredMethod(
                "logQuestionGenerationDebugInput",
                Long.class,
                String.class,
                QuestionStreamService.NextQuestionPlan.class,
                QuestionGenerationInput.class
        );
        logInput.setAccessible(true);
        logInput.invoke(service, 200L, "attempt-200", plan, input);

        Method logOutput = QuestionStreamService.class.getDeclaredMethod(
                "logQuestionGenerationDebugOutput",
                Long.class,
                String.class,
                QuestionStreamService.NextQuestionPlan.class,
                String.class
        );
        logOutput.setAccessible(true);
        logOutput.invoke(service, 200L, "attempt-200", plan, "Redis 缓存击穿一般怎么处理？");

        List<String> logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(logs).anyMatch(msg -> msg.contains("[INTERVIEW-DEBUG][stream.plan]")
                && msg.contains("\"nextDomainCode\":\"redis\"")
                && msg.contains("\"targetFocus\":\"缓存击穿\""));
        assertThat(logs).anyMatch(msg -> msg.contains("[INTERVIEW-DEBUG][stream.output]")
                && msg.contains("Redis 缓存击穿一般怎么处理？"));
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = QuestionStreamService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ListAppender<ILoggingEvent> startLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(QuestionStreamService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
