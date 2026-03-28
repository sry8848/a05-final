package com.a05.aiinterview.interview.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.interview.engine.DecisionExecutionPlan;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.dto.SseDoneEvent;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagPlanCompiler;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class QuestionStreamServiceBuildInputTest {

    @TempDir
    Path tempDir;

    @Test
    void extractNextQuestionPlan_shouldParseNewEvaluationSchema() throws Exception {
        QuestionStreamService service = newService();

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setAttemptId("attempt-continue");
        attempt.setAnswerText("候选人解释了延迟消息方案。");
        attempt.setEvaluationJson(new LinkedHashMap<>(Map.of(
                "effectiveDecisionPlan", Map.of(
                        "interviewAction", "CONTINUE",
                        "strategyCode", "S_ENTER_PROJECT",
                        "targetQuestionType", "PROJECT_DEEP_DIVE",
                        "nextFocus", "订单超时关闭链路的幂等与并发控制",
                        "decisionReason", "上一题已经建立基础认知，下一题应切回项目主线核实真实工程落地。",
                        "retrievalPlans", List.of(Map.of(
                                "goal", "补充项目案例",
                                "displayQuery", "订单超时关闭",
                                "queryText", "订单超时关闭 幂等 并发控制 延迟消息",
                                "keywordHints", List.of("订单超时关闭", "幂等", "延迟消息"),
                                "difficultyHint", "L3",
                                "mustHaveClues", List.of("项目案例"),
                                "avoidClues", List.of("重复问法")
                        ))
                )
        )));

        Method method = QuestionStreamService.class.getDeclaredMethod("extractNextQuestionPlan", InterviewAttempt.class);
        method.setAccessible(true);

        QuestionStreamService.NextQuestionPlan plan =
                (QuestionStreamService.NextQuestionPlan) method.invoke(service, attempt);

        assertThat(plan).isNotNull();
        assertThat(plan.getInterviewAction()).isEqualTo("CONTINUE");
        assertThat(plan.getTargetQuestionType()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(plan.getNextFocus()).contains("订单超时关闭");
        assertThat(plan.getNextItemType()).isEmpty();
        assertThat(plan.getNextItemName()).isEmpty();
        assertThat(plan.getNextProjectPoint()).isEmpty();
        assertThat(plan.getRetrievalPlans()).hasSize(1);
        assertThat(plan.getDecisionReason()).contains("项目主线");
    }

    @Test
    void buildGenInput_shouldBuildNewPromptInput() throws Exception {
        QuestionStreamService service = newService();

        InterviewSession session = new InterviewSession();
        session.setId(100L);
        session.setPositionCode("JAVA_BACKEND");
        session.setMode("professional");
        session.setExperienceLevel("SENIOR");
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainCode", "redis", "domainName", "Redis", "focusPoints", List.of("缓存击穿"))
                )
        ));
        session.setStateLedgerJson(Map.of(
                "active_item_key", "item-order",
                "active_item_type", "PROJECT",
                "active_item_name", "订单系统",
                "current_focus", "缓存击穿",
                "recent_question_families", List.of("redis.breakdown.definition"),
                "interviewer_archetype", "stress"
        ));

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setAttemptId("attempt-build");
        attempt.setAnswerText("候选人给出了基础方案。");

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .targetQuestionType("PRINCIPLE")
                .nextFocus("缓存击穿")
                .decisionReason("回答覆盖了基础方案，但还需要继续核实工程取舍。")
                .retrievalPlans(List.of(EvaluationDecisionOutput.RetrievalPlan.builder()
                        .goal("补充缓存击穿案例")
                        .displayQuery("缓存击穿")
                        .queryText("缓存击穿 互斥锁 逻辑过期 热点 key 失效")
                        .keywordHints(List.of("缓存击穿", "互斥锁", "逻辑过期"))
                        .difficultyHint("L3")
                        .mustHaveClues(List.of("方案边界"))
                        .avoidClues(List.of())
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

        assertEquals("SENIOR", input.getExperienceLevel());
        assertThat(new ObjectMapper().convertValue(input.getRoleContext(), Map.class))
                .containsEntry("style", "stress")
                .containsOnlyKeys("roundType", "style");
        assertEquals("item-order", input.getProjectContext().getActiveItemKey());
        assertEquals("PROJECT", input.getProjectContext().getItemType());
        assertEquals("缓存击穿", input.getProjectContext().getCurrentFocus());
        assertEquals("PRINCIPLE", input.getNextQuestionGoal().getQuestionType());
        assertEquals("缓存击穿", input.getNextQuestionGoal().getNextFocus());
        assertEquals("redis", input.getNextQuestionGoal().getRelatedDomainCode());
        assertThat(input.getNextQuestionGoal().getExpectedAnswerPoints()).isEmpty();
        assertThat(input.getRecentContext().getLastAnswerSummary()).isEqualTo("候选人给出了基础方案。");
        assertThat(input.getRecentContext().getRecentTurnsSummary()).isEqualTo("回答覆盖了基础方案，但还需要继续核实工程取舍。");
        assertThat(input.getRetrievalContext().getSummary()).contains("无外部参考资料");
        assertThat(input.getRetrievalContext().getRetrievalPlans()).hasSize(1);
        assertThat(input.getRetrievalContext().getRetrievalPlans().getFirst().getDisplayQuery()).isEqualTo("缓存击穿");
        assertThat(input.getRetrievalContext().getRetrievalPlans().getFirst().getKeywordHints()).containsExactly("缓存击穿", "互斥锁", "逻辑过期");
        assertThat(input.getConstraints().getAvoidRepetitionFamilies()).isEmpty();
    }

    @Test
    void buildGenInput_shouldUseRetrievedMaterialsAndFollowUpCandidatesWhenRagHitsExist() throws Exception {
        QuestionStreamService service = newService();

        InterviewSession session = new InterviewSession();
        session.setId(102L);
        session.setPositionCode("JAVA_BACKEND");
        session.setMode("practice");
        session.setExperienceLevel("FRESH_GRAD");
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainCode", "redis", "domainName", "Redis", "focusPoints", List.of("缓存穿透"))
                )
        ));
        session.setStateLedgerJson(Map.of());

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setAttemptId("attempt-rag-hit");
        attempt.setAnswerText("候选人提到了布隆过滤器，但没有展开误判问题。");

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .targetQuestionType("PRINCIPLE")
                .nextFocus("缓存穿透")
                .decisionReason("需要继续确认布隆过滤器和空对象缓存的取舍。")
                .retrievalPlans(List.of(EvaluationDecisionOutput.RetrievalPlan.builder()
                        .goal("补充缓存穿透高频问法")
                        .displayQuery("缓存穿透")
                        .queryText("Redis 缓存穿透 布隆过滤器 空对象缓存")
                        .keywordHints(List.of("缓存穿透", "布隆过滤器", "空对象缓存"))
                        .difficultyHint("L2")
                        .mustHaveClues(List.of("布隆过滤器", "误判"))
                        .avoidClues(List.of("Redis 安装部署"))
                        .build()))
                .build();

        RagContext ragContext = RagContext.builder()
                .summary("命中 1 张题卡：缓存穿透高频题")
                .retrievedMaterials(List.of(RagContext.RetrievedMaterial.builder()
                        .questionId("redis-cache-penetration-001")
                        .questionText("讲一下 Redis 缓存穿透")
                        .intentConcept("考察缓存空值兜底和布隆过滤器理解")
                        .referenceContext("高并发不存在数据查询时，如果没有兜底，会持续打穿数据库。")
                        .scoringKeyPoints(List.of("缓存空对象", "布隆过滤器"))
                        .scoringPitfalls(List.of("混淆缓存击穿和穿透"))
                        .followUpIds(List.of("redis-bloom-filter-false-positive-001"))
                        .domainCode("redis")
                        .questionType("PRINCIPLE")
                        .difficulty("L2")
                        .keywords(List.of("缓存穿透", "布隆过滤器"))
                        .build()))
                .followUpCandidates(List.of("redis-bloom-filter-false-positive-001"))
                .hitCount(1)
                .empty(false)
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
                ragContext
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> retrievalContextMap = new ObjectMapper().convertValue(input.getRetrievalContext(), Map.class);
        assertThat(retrievalContextMap)
                .containsEntry("summary", "命中 1 张题卡：缓存穿透高频题")
                .containsEntry("followUpCandidates", List.of("redis-bloom-filter-false-positive-001"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> retrievedMaterials =
                (List<Map<String, Object>>) retrievalContextMap.get("retrievedMaterials");
        assertThat(retrievedMaterials).singleElement().satisfies(material -> assertThat(material)
                .containsEntry("questionId", "redis-cache-penetration-001")
                .containsEntry("questionText", "讲一下 Redis 缓存穿透")
                .containsEntry("domainCode", "redis"));
        assertThat(input.getRetrievalContext().getRetrievalPlans()).hasSize(1);
    }

    @Test
    void safeRetrieveRag_shouldCompilePlanAndCallRetrievalServiceWhenApplicable() throws Exception {
        RagRetrievalService ragService = mock(RagRetrievalService.class);
        QuestionStreamService service = newService(ragService);
        RagPlanCompiler compiler = mock(RagPlanCompiler.class);
        setField(service, QuestionStreamService.class, "ragPlanCompiler", compiler);

        InterviewSession session = new InterviewSession();
        session.setId(103L);
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("FRESH_GRAD");

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .targetQuestionType("PRINCIPLE")
                .nextFocus("缓存穿透")
                .targetDomainCode("redis")
                .targetDomainName("Redis")
                .retrievalPlans(List.of(EvaluationDecisionOutput.RetrievalPlan.builder()
                        .goal("补充高频题")
                        .displayQuery("缓存穿透")
                        .queryText("Redis 缓存穿透 布隆过滤器")
                        .keywordHints(List.of("缓存穿透", "布隆过滤器"))
                        .difficultyHint("L2")
                        .mustHaveClues(List.of("布隆过滤器"))
                        .avoidClues(List.of("安装部署"))
                        .build()))
                .build();

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .displayQuery("缓存穿透")
                .queryText("Redis 缓存穿透 布隆过滤器")
                .questionType("PRINCIPLE")
                .domainCode("redis")
                .difficultyHint("L2")
                .keywordQueries(List.of("缓存穿透", "布隆过滤器"))
                .mustHaveClues(List.of("布隆过滤器"))
                .avoidClues(List.of("安装部署"))
                .build();
        RagContext expected = RagContext.builder()
                .summary("命中 1 张题卡")
                .retrievedMaterials(List.of())
                .hitCount(1)
                .empty(false)
                .build();

        when(compiler.compile(any(DecisionExecutionPlan.class), eq("JAVA_BACKEND"), eq("FRESH_GRAD")))
                .thenReturn(request);
        when(ragService.retrieve(request)).thenReturn(expected);

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "safeRetrieveRag",
                InterviewSession.class,
                QuestionStreamService.NextQuestionPlan.class
        );
        method.setAccessible(true);

        RagContext actual = (RagContext) method.invoke(service, session, plan);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<DecisionExecutionPlan> planCaptor = ArgumentCaptor.forClass(DecisionExecutionPlan.class);
        verify(compiler).compile(planCaptor.capture(), eq("JAVA_BACKEND"), eq("FRESH_GRAD"));
        assertThat(planCaptor.getValue().getTargetQuestionType()).isEqualTo("PRINCIPLE");
        assertThat(planCaptor.getValue().getNextFocus()).isEqualTo("缓存穿透");
        assertThat(planCaptor.getValue().getTargetDomainCode()).isEqualTo("redis");
        verify(ragService).retrieve(request);
    }

    @Test
    void buildGenInput_shouldLeaveRelatedDomainEmptyWhenFocusDoesNotMatchSyllabus() throws Exception {
        QuestionStreamService service = newService();

        InterviewSession session = new InterviewSession();
        session.setId(101L);
        session.setPositionCode("JAVA_BACKEND");
        session.setMode("practice");
        session.setExperienceLevel("JUNIOR");
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainCode", "redis", "domainName", "Redis", "focusPoints", List.of("缓存击穿")),
                        Map.of("domainCode", "mq", "domainName", "消息队列", "focusPoints", List.of("削峰填谷"))
                )
        ));
        session.setStateLedgerJson(Map.of());

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setAttemptId("attempt-no-domain");
        attempt.setAnswerText("候选人回答较泛。");

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .targetQuestionType("SCENARIO")
                .nextFocus("线程模型与调度策略")
                .retrievalPlans(List.of())
                .decisionReason("当前回答较泛，下一题需要切到更明确的场景。")
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

        assertThat(input.getNextQuestionGoal().getRelatedDomainCode()).isEmpty();
        assertThat(input.getNextQuestionGoal().getRelatedDomainName()).isEmpty();
    }

    @Test
    void questionGenerationDebugLogs_shouldContainNewPlanFields() throws Exception {
        InterviewDebugTraceService debugTraceService = newDebugTraceService(true);
        QuestionStreamService service = newService(debugTraceService);
        ListAppender<ILoggingEvent> appender = startLogCapture();

        InterviewSession session = new InterviewSession();
        session.setId(200L);
        session.setPositionCode("JAVA_BACKEND");
        session.setMode("practice");
        session.setExperienceLevel("FRESH_GRAD");
        session.setSyllabusJson(Map.of(
                "domains", List.of(Map.of("domainCode", "redis", "domainName", "Redis", "focusPoints", List.of("缓存击穿")))
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
        attempt.setAnswerText("候选人回答了基础概念。");

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .targetQuestionType("PRINCIPLE")
                .nextFocus("缓存击穿")
                .retrievalPlans(List.of())
                .decisionReason("回答覆盖了基础概念，但还需要补边界。")
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
        assertThat(root.path("stages").path("nextQuestionPlan").path("targetQuestionType").asText()).isEqualTo("PRINCIPLE");
        assertThat(root.path("stages").path("questionGenerationOutput").path("finalStem").asText())
                .contains("Redis 缓存击穿一般怎么处理");
    }

    @Test
    void buildDoneEvent_shouldIncludeAuthoritativeQuestionSnapshot() throws Exception {
        QuestionStreamService service = newService();

        InterviewSession session = new InterviewSession();
        session.setId(300L);
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainCode", "redis", "domainName", "Redis")
                )
        ));

        InterviewQuestion question = new InterviewQuestion();
        question.setId(501L);
        question.setSessionId(300L);
        question.setQuestionNo(4);
        question.setQuestionType("BEHAVIORAL");
        question.setDomainCode("");
        question.setStem("请分享一次跨团队推动方案落地的经历。");
        question.setGenerationContextJson(Map.of("focusPoint", "跨团队协作"));

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "buildDoneEvent",
                String.class,
                InterviewSession.class,
                InterviewQuestion.class,
                int.class,
                boolean.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        var event = (org.springframework.http.codec.ServerSentEvent<String>) method.invoke(
                service,
                "attempt-done",
                session,
                question,
                12,
                true
        );

        assertThat(event.event()).isEqualTo("done");
        SseDoneEvent payload = new ObjectMapper().readValue(event.data(), SseDoneEvent.class);
        assertThat(payload.getQuestionId()).isEqualTo(501L);
        assertThat(payload.getAudioStatusUrl()).isEqualTo("/api/v1/interviews/300/questions/501/audio");
        assertThat(payload.getQuestion()).isNotNull();
        assertThat(payload.getQuestion().getQuestionId()).isEqualTo(501L);
        assertThat(payload.getQuestion().getQuestionNo()).isEqualTo(4);
        assertThat(payload.getQuestion().getQuestionType()).isEqualTo("BEHAVIORAL");
        assertThat(payload.getQuestion().getDomainCode()).isBlank();
        assertThat(payload.getQuestion().getDomainName()).isEqualTo("行为题");
        Map<String, Object> questionJson = new ObjectMapper().convertValue(payload.getQuestion(), Map.class);
        assertThat(questionJson).containsEntry("focusPoint", "跨团队协作");
        assertThat(questionJson).containsOnlyKeys(
                "questionId", "questionNo", "questionType", "domainCode", "domainName",
                "stem", "focusPoint", "aiResultStatus", "hintAvailable");
    }

    @Test
    void saveQuestion_shouldAdvanceQuotaStateAfterQuestionIsPersisted() throws Exception {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        RagRetrievalService ragService = mock(RagRetrievalService.class);
        TtsService ttsService = mock(TtsService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(900L);
        currentQuestion.setSessionId(500L);
        currentQuestion.setQuestionNo(1);
        currentQuestion.setQuestionType("PRINCIPLE");

        when(questionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(currentQuestion));
        when(questionMapper.selectById(900L)).thenReturn(currentQuestion);
        doAnswer(invocation -> {
            InterviewQuestion inserted = invocation.getArgument(0, InterviewQuestion.class);
            inserted.setId(901L);
            return 1;
        }).when(questionMapper).insert(org.mockito.ArgumentMatchers.any(InterviewQuestion.class));

        QuestionStreamService service = new QuestionStreamService(
                aiClient,
                sessionMapper,
                questionMapper,
                attemptMapper,
                ragService,
                mock(RagPlanCompiler.class),
                ttsService,
                redisTemplate,
                new InterviewDebugTraceService(new ObjectMapper()),
                new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(500L);
        session.setCurrentQuestionNo(1);
        session.setStateLedgerJson(new LinkedHashMap<>(Map.of(
                "quota_state", new LinkedHashMap<>(Map.of(
                        "samePointContinue", 1,
                        "sameDomainContinue", 2,
                        "sameProjectPointContinue", 0,
                        "sameProjectContinue", 0,
                        "principleTotal", 1,
                        "projectTotal", 0,
                        "scenarioTotal", 0,
                        "behavioralTotal", 0
                )),
                "interviewer_archetype", "guiding"
        )));

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .finalDecision("S_ENTER_PROJECT")
                .targetQuestionType("PROJECT_DEEP_DIVE")
                .nextFocus("订单系统里的缓存一致性设计")
                .build();

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "saveQuestion",
                InterviewSession.class,
                Long.class,
                String.class,
                QuestionStreamService.NextQuestionPlan.class,
                String.class
        );
        method.setAccessible(true);

        InterviewQuestion saved = (InterviewQuestion) method.invoke(
                service,
                session,
                900L,
                "attempt-900",
                plan,
                "请结合订单系统，讲讲缓存一致性你是怎么设计和落地的？"
        );

        assertThat(saved.getId()).isEqualTo(901L);
        assertThat(saved.getGenerationContextJson()).containsEntry("interviewerArchetype", "guiding");
        assertThat(session.getCurrentQuestionNo()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> quotaState = (Map<String, Object>) session.getStateLedgerJson().get("quota_state");
        assertThat(session.getStateLedgerJson()).containsEntry("asked_total", 2);
        assertThat(quotaState).containsEntry("samePointContinue", 0)
                .containsEntry("sameDomainContinue", 0)
                .containsEntry("projectTotal", 1)
                .containsEntry("principleTotal", 1);
    }

    @Test
    void saveQuestion_shouldRejectPrincipleQuestionWithoutDomain() throws Exception {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        RagRetrievalService ragService = mock(RagRetrievalService.class);
        TtsService ttsService = mock(TtsService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(910L);
        currentQuestion.setSessionId(510L);
        currentQuestion.setQuestionNo(1);
        currentQuestion.setQuestionType("PROJECT_DEEP_DIVE");
        currentQuestion.setGenerationContextJson(Map.of());

        when(questionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(currentQuestion));
        when(questionMapper.selectById(910L)).thenReturn(currentQuestion);

        QuestionStreamService service = new QuestionStreamService(
                aiClient,
                sessionMapper,
                questionMapper,
                attemptMapper,
                ragService,
                mock(RagPlanCompiler.class),
                ttsService,
                redisTemplate,
                new InterviewDebugTraceService(new ObjectMapper()),
                new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(510L);
        session.setCurrentQuestionNo(1);
        session.setSyllabusJson(Map.of("domains", List.of()));
        session.setStateLedgerJson(new LinkedHashMap<>(Map.of(
                "asked_total", 1,
                "quota_state", new LinkedHashMap<>(Map.of(
                        "samePointContinue", 0,
                        "sameDomainContinue", 0,
                        "sameProjectPointContinue", 0,
                        "sameProjectContinue", 1,
                        "principleTotal", 0,
                        "projectTotal", 1,
                        "scenarioTotal", 0,
                        "behavioralTotal", 0
                )),
                "interviewer_archetype", "efficiency"
        )));

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .finalDecision("S_ENTER_PRINCIPLE")
                .targetQuestionType("PRINCIPLE")
                .nextFocus("缓存一致性")
                .targetDomainCode("")
                .targetDomainName("")
                .build();

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "saveQuestion",
                InterviewSession.class,
                Long.class,
                String.class,
                QuestionStreamService.NextQuestionPlan.class,
                String.class
        );
        method.setAccessible(true);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            try {
                method.invoke(
                        service,
                        session,
                        910L,
                        "attempt-910",
                        plan,
                        "请结合项目讲讲缓存一致性的关键设计。"
                );
            } catch (java.lang.reflect.InvocationTargetException ex) {
                if (ex.getTargetException() instanceof IllegalStateException illegalStateException) {
                    throw illegalStateException;
                }
                throw new RuntimeException(ex.getTargetException());
            }
        });
    }

    @Test
    void saveQuestion_shouldPersistProjectPointAndResolvedProjectIdentity() throws Exception {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        RagRetrievalService ragService = mock(RagRetrievalService.class);
        TtsService ttsService = mock(TtsService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(920L);
        currentQuestion.setSessionId(520L);
        currentQuestion.setQuestionNo(1);
        currentQuestion.setQuestionType("INTRO");

        when(questionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(currentQuestion));
        when(questionMapper.selectById(920L)).thenReturn(currentQuestion);
        doAnswer(invocation -> {
            InterviewQuestion inserted = invocation.getArgument(0, InterviewQuestion.class);
            inserted.setId(921L);
            return 1;
        }).when(questionMapper).insert(org.mockito.ArgumentMatchers.any(InterviewQuestion.class));

        QuestionStreamService service = new QuestionStreamService(
                aiClient,
                sessionMapper,
                questionMapper,
                attemptMapper,
                ragService,
                mock(RagPlanCompiler.class),
                ttsService,
                redisTemplate,
                new InterviewDebugTraceService(new ObjectMapper()),
                new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(520L);
        session.setCurrentQuestionNo(1);
        session.setSyllabusJson(Map.of(
                "domains", List.of(),
                "experienceItems", List.of(
                        Map.of(
                                "itemKey", "project_chabst",
                                "itemType", "PROJECT",
                                "itemName", "Chabst",
                                "resumeDescription", "项目描述",
                                "techHooks", List.of("RabbitMQ 延迟消息处理超时订单")
                        )
                )
        ));
        session.setStateLedgerJson(new LinkedHashMap<>(Map.of(
                "asked_total", 1,
                "quota_state", new LinkedHashMap<>(Map.of(
                        "samePointContinue", 0,
                        "sameDomainContinue", 0,
                        "sameProjectPointContinue", 0,
                        "sameProjectContinue", 0,
                        "principleTotal", 0,
                        "projectTotal", 0,
                        "scenarioTotal", 0,
                        "behavioralTotal", 0
                ))
        )));

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .finalDecision("S_ENTER_PROJECT")
                .targetQuestionType("PROJECT_DEEP_DIVE")
                .nextFocus("延迟消息与并发控制")
                .nextItemType("PROJECT")
                .nextItemName("Chabst")
                .nextProjectPoint("RabbitMQ 延迟消息处理超时订单")
                .build();

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "saveQuestion",
                InterviewSession.class,
                Long.class,
                String.class,
                QuestionStreamService.NextQuestionPlan.class,
                String.class
        );
        method.setAccessible(true);

        InterviewQuestion saved = (InterviewQuestion) method.invoke(
                service,
                session,
                920L,
                "attempt-920",
                plan,
                "结合 Chabst 讲讲你们用 RabbitMQ 延迟消息处理超时订单时，怎么保证并发和幂等。"
        );

        assertThat(saved.getId()).isEqualTo(921L);
        assertThat(saved.getGenerationContextJson())
                .containsEntry("activeItemKey", "project_chabst")
                .containsEntry("activeItemType", "PROJECT")
                .containsEntry("activeItemName", "Chabst")
                .containsEntry("projectPoint", "RabbitMQ 延迟消息处理超时订单")
                .containsEntry("interviewerArchetype", "efficiency");
    }

    @Test
    void saveQuestion_shouldNotInventBehavioralPseudoDomainWhenPlanDoesNotProvideOne() throws Exception {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        RagRetrievalService ragService = mock(RagRetrievalService.class);
        TtsService ttsService = mock(TtsService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(930L);
        currentQuestion.setSessionId(530L);
        currentQuestion.setQuestionNo(1);
        currentQuestion.setQuestionType("PRINCIPLE");
        currentQuestion.setDomainCode("redis");
        currentQuestion.setGenerationContextJson(Map.of("domainCode", "redis", "domainName", "Redis"));

        when(questionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(currentQuestion));
        when(questionMapper.selectById(930L)).thenReturn(currentQuestion);
        doAnswer(invocation -> {
            InterviewQuestion inserted = invocation.getArgument(0, InterviewQuestion.class);
            inserted.setId(931L);
            return 1;
        }).when(questionMapper).insert(org.mockito.ArgumentMatchers.any(InterviewQuestion.class));

        QuestionStreamService service = new QuestionStreamService(
                aiClient,
                sessionMapper,
                questionMapper,
                attemptMapper,
                ragService,
                mock(RagPlanCompiler.class),
                ttsService,
                redisTemplate,
                new InterviewDebugTraceService(new ObjectMapper()),
                new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(530L);
        session.setCurrentQuestionNo(1);
        session.setSyllabusJson(Map.of("domains", List.of()));
        session.setStateLedgerJson(new LinkedHashMap<>(Map.of(
                "asked_total", 1,
                "quota_state", new LinkedHashMap<>(Map.of(
                        "samePointContinue", 0,
                        "sameDomainContinue", 0,
                        "sameProjectPointContinue", 0,
                        "sameProjectContinue", 0,
                        "principleTotal", 1,
                        "projectTotal", 0,
                        "scenarioTotal", 0,
                        "behavioralTotal", 0
                )),
                "interviewer_archetype", "stress"
        )));

        QuestionStreamService.NextQuestionPlan plan = QuestionStreamService.NextQuestionPlan.builder()
                .interviewAction("CONTINUE")
                .effectiveDecisionSource("SYSTEM_FALLBACK")
                .finalDecision("S_ENTER_BEHAVIORAL")
                .targetQuestionType("BEHAVIORAL")
                .nextFocus("一次你在协作中遇到分歧并推动结果的真实经历")
                .targetDomainCode("")
                .targetDomainName("")
                .build();

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "saveQuestion",
                InterviewSession.class,
                Long.class,
                String.class,
                QuestionStreamService.NextQuestionPlan.class,
                String.class
        );
        method.setAccessible(true);

        InterviewQuestion saved = (InterviewQuestion) method.invoke(
                service,
                session,
                930L,
                "attempt-930",
                plan,
                "请分享一次你在协作中遇到分歧并推动结果的真实经历。"
        );

        assertThat(saved.getDomainCode()).isBlank();
        assertThat(saved.getGenerationContextJson())
                .containsEntry("domainCode", "")
                .containsEntry("domainName", "");
    }

    private QuestionStreamService newService() {
        return newService(mock(RagRetrievalService.class), newDebugTraceService(false));
    }

    private QuestionStreamService newService(InterviewDebugTraceService debugTraceService) {
        return newService(mock(RagRetrievalService.class), debugTraceService);
    }

    private QuestionStreamService newService(RagRetrievalService ragService) {
        return newService(ragService, newDebugTraceService(false));
    }

    private QuestionStreamService newService(RagRetrievalService ragService, InterviewDebugTraceService debugTraceService) {
        return new QuestionStreamService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                ragService,
                mock(RagPlanCompiler.class),
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
