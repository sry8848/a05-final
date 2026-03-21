package com.a05.aiinterview.interview.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import com.a05.aiinterview.speech.service.TtsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class QuestionStreamServiceBuildInputTest {

    @TempDir
    Path tempDir;

    @Test
    void extractNextQuestionPlan_shouldParseNewEvaluationSchema() throws Exception {
        QuestionStreamService service = newService();

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setAttemptId("attempt-continue");
        attempt.setEvaluationJson(new LinkedHashMap<>(Map.of(
                "interviewAction", "CONTINUE",
                "nextQuestionType", "PROJECT",
                "nextFocus", "订单超时关闭链路的幂等与并发控制",
                "expectedAnswerPoints", List.of("任务调度", "幂等", "并发冲突"),
                "answerSummary", "候选人解释了延迟消息方案。",
                "answerAssessment", "方案方向基本正确，但责任边界还不够清楚。",
                "retrievalPlans", List.of(Map.of(
                        "retrievalNeed", true,
                        "retrievalGoal", "补充项目案例",
                        "primaryQuery", "订单超时关闭 幂等 并发控制",
                        "alternateQueries", List.of("延迟消息 订单关闭 并发"),
                        "retrievalType", "questions",
                        "expectedEvidence", List.of("项目案例"),
                        "avoidEvidence", List.of("重复问法")
                ))
        )));

        Method method = QuestionStreamService.class.getDeclaredMethod("extractNextQuestionPlan", InterviewAttempt.class);
        method.setAccessible(true);

        QuestionStreamService.NextQuestionPlan plan =
                (QuestionStreamService.NextQuestionPlan) method.invoke(service, attempt);

        assertThat(plan).isNotNull();
        assertThat(plan.getInterviewAction()).isEqualTo("CONTINUE");
        assertThat(plan.getNextQuestionType()).isEqualTo("PROJECT");
        assertThat(plan.getNextFocus()).contains("订单超时关闭");
        assertThat(plan.getExpectedAnswerPoints()).containsExactly("任务调度", "幂等", "并发冲突");
        assertThat(plan.getRetrievalPlans()).hasSize(1);
        assertThat(plan.getAnswerSummary()).contains("延迟消息");
    }

    @Test
    void buildGenInput_shouldBuildNewPromptInput() throws Exception {
        QuestionStreamService service = newService();

        InterviewSession session = new InterviewSession();
        session.setId(100L);
        session.setTargetRole("JAVA_BACKEND");
        session.setMode("professional");
        session.setExperienceLevel("SENIOR");
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainId", 6L, "domainCode", "redis", "domainName", "Redis", "focusPoints", List.of("缓存击穿"))
                )
        ));
        session.setStateLedgerJson(Map.of(
                "active_item_key", "item-order",
                "active_item_type", "PROJECT",
                "active_item_name", "订单系统",
                "current_focus", "缓存击穿",
                "recent_question_families", List.of("redis.breakdown.definition")
        ));

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setAttemptId("attempt-build");

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .nextQuestionType("THEORY")
                .nextFocus("缓存击穿")
                .expectedAnswerPoints(List.of("互斥锁", "逻辑过期"))
                .answerSummary("候选人给出了基础方案。")
                .answerAssessment("需要继续补工程取舍。")
                .retrievalPlans(List.of(EvaluationDecisionOutput.RetrievalPlan.builder()
                        .retrievalNeed(true)
                        .retrievalGoal("补充缓存击穿案例")
                        .primaryQuery("缓存击穿 互斥锁 逻辑过期")
                        .alternateQueries(List.of("热点 key 失效"))
                        .retrievalType("domain")
                        .expectedEvidence(List.of("方案边界"))
                        .avoidEvidence(List.of())
                        .build()))
                .build();

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "buildGenInput",
                InterviewSession.class,
                QuestionStreamService.NextQuestionPlan.class,
                List.class,
                InterviewAttempt.class,
                RagContext.class
        );
        method.setAccessible(true);

        QuestionGenerationInput input = (QuestionGenerationInput) method.invoke(
                service,
                session,
                plan,
                List.of(),
                attempt,
                RagContext.empty()
        );

        assertEquals("SENIOR", input.getRoleContext().getCandidateLevel());
        assertEquals("item-order", input.getProjectContext().getActiveItemKey());
        assertEquals("PROJECT", input.getProjectContext().getItemType());
        assertEquals("缓存击穿", input.getProjectContext().getCurrentFocus());
        assertEquals("PRINCIPLE", input.getNextQuestionGoal().getQuestionType());
        assertEquals("缓存击穿", input.getNextQuestionGoal().getNextFocus());
        assertEquals("redis", input.getNextQuestionGoal().getRelatedDomainCode());
        assertThat(input.getNextQuestionGoal().getExpectedAnswerPoints()).containsExactly("互斥锁", "逻辑过期");
        assertThat(input.getRetrievalContext().getSummary()).contains("无外部参考资料");
        assertThat(input.getRetrievalContext().getRetrievalPlans()).hasSize(1);
        assertThat(input.getConstraints().getAvoidRepetitionFamilies()).isEmpty();
    }

    @Test
    void questionGenerationDebugLogs_shouldContainNewPlanFields() throws Exception {
        InterviewDebugTraceService debugTraceService = newDebugTraceService(true);
        QuestionStreamService service = newService(debugTraceService);
        ListAppender<ILoggingEvent> appender = startLogCapture();

        InterviewSession session = new InterviewSession();
        session.setId(200L);
        session.setTargetRole("JAVA_BACKEND");
        session.setMode("practice");
        session.setExperienceLevel("FRESH_GRAD");
        session.setSyllabusJson(Map.of(
                "domains", List.of(Map.of("domainId", 6L, "domainCode", "redis", "domainName", "Redis", "focusPoints", List.of("缓存击穿")))
        ));
        session.setStateLedgerJson(Map.of(
                "active_item_key", "item-cache",
                "active_item_type", "PROJECT",
                "active_item_name", "缓存平台",
                "current_focus", "缓存击穿"
        ));

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setAttemptId("attempt-200");
        attempt.setQuestionId(88L);

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .nextQuestionType("THEORY")
                .nextFocus("缓存击穿")
                .expectedAnswerPoints(List.of())
                .retrievalPlans(List.of())
                .answerSummary("候选人回答了基础概念。")
                .answerAssessment("还需要补边界。")
                .build();

        Method buildInput = QuestionStreamService.class.getDeclaredMethod(
                "buildGenInput",
                InterviewSession.class,
                QuestionStreamService.NextQuestionPlan.class,
                List.class,
                InterviewAttempt.class,
                RagContext.class
        );
        buildInput.setAccessible(true);
        QuestionGenerationInput input = (QuestionGenerationInput) buildInput.invoke(
                service, session, plan, List.of(), attempt, RagContext.empty());

        Method logInput = QuestionStreamService.class.getDeclaredMethod(
                "logQuestionGenerationDebugInput",
                Long.class,
                Long.class,
                String.class,
                QuestionStreamService.NextQuestionPlan.class,
                QuestionGenerationInput.class
        );
        logInput.setAccessible(true);
        logInput.invoke(service, 200L, 88L, "attempt-200", plan, input);

        Method logOutput = QuestionStreamService.class.getDeclaredMethod(
                "logQuestionGenerationDebugOutput",
                Long.class,
                Long.class,
                String.class,
                QuestionStreamService.NextQuestionPlan.class,
                String.class
        );
        logOutput.setAccessible(true);
        logOutput.invoke(service, 200L, 88L, "attempt-200", plan, "Redis 缓存击穿一般怎么处理？");

        List<String> logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        Path snapshotFile = tempDir.resolve("session-200").resolve("session-200_q-88_attempt-attempt-200.json");
        assertThat(logs).anyMatch(msg -> msg.contains("[INTERVIEW-DEBUG][next-question-plan]")
                && msg.contains("traceId=session-200_q-88_attempt-attempt-200")
                && msg.contains(snapshotFile.toString()));
        assertThat(logs).anyMatch(msg -> msg.contains("[INTERVIEW-DEBUG][question-generation-output]")
                && msg.contains("Redis 缓存击穿一般怎么处理？"));
        assertThat(Files.exists(snapshotFile)).isTrue();

        JsonNode root = new ObjectMapper().readTree(Files.readString(snapshotFile));
        assertThat(root.path("stages").has("nextQuestionPlan")).isTrue();
        assertThat(root.path("stages").has("questionGenerationInput")).isTrue();
        assertThat(root.path("stages").has("questionGenerationOutput")).isTrue();
        assertThat(root.path("stages").path("nextQuestionPlan").path("nextQuestionType").asText()).isEqualTo("THEORY");
        assertThat(root.path("stages").path("questionGenerationOutput").path("finalStem").asText())
                .contains("Redis 缓存击穿一般怎么处理");
    }

    private QuestionStreamService newService() {
        return newService(newDebugTraceService(false));
    }

    private QuestionStreamService newService(InterviewDebugTraceService debugTraceService) {
        return new QuestionStreamService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(RagRetrievalService.class),
                mock(TtsService.class),
                mock(StringRedisTemplate.class),
                debugTraceService,
                new ObjectMapper()
        );
    }

    private InterviewDebugTraceService newDebugTraceService(boolean enabled) {
        InterviewDebugTraceService service = new InterviewDebugTraceService(new ObjectMapper());
        try {
            setField(service, InterviewDebugTraceService.class, "debugRootDir", tempDir);
            setField(service, InterviewDebugTraceService.class, "enabled", enabled);
            setField(service, InterviewDebugTraceService.class, "includePrompts", false);
            setField(service, InterviewDebugTraceService.class, "maxTextChars", 2000);
            return service;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ListAppender<ILoggingEvent> startLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(InterviewDebugTraceService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
