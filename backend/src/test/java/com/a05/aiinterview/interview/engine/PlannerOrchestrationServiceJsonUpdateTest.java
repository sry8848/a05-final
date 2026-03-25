package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.dto.InterviewSyllabus;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.a05.aiinterview.position.service.PositionService;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PlannerOrchestrationService JSON persistence tests")
class PlannerOrchestrationServiceJsonUpdateTest {

    @Test
    @DisplayName("runAsync should persist assembled syllabus via updateById entities")
    void runAsync_shouldPersistAssembledSyllabus() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        PositionService positionService = mock(PositionService.class);
        AiClient aiClient = mock(AiClient.class);
        StateLedgerInitService ledgerInitService = mock(StateLedgerInitService.class);
        FirstQuestionGenerationService firstQuestionGenerationService = mock(FirstQuestionGenerationService.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);

        InterviewSession session = buildSession();
        PositionSkillDomain domain = buildDomain();
        PlannerOutput plannerOutput = buildPlannerOutput();
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("overall_status", "IN_PROGRESS");
        InterviewQuestion firstQuestion = buildFirstQuestion();

        when(sessionMapper.selectById(8L)).thenReturn(session);
        when(positionService.listSkillDomainEntities("JAVA_BACKEND")).thenReturn(List.of(domain));
        when(aiClient.callPlanner(any())).thenReturn(AiCallResult.<PlannerOutput>builder()
                .output(plannerOutput)
                .build());
        when(ledgerInitService.initLedger(eq(8L), eq("FRESH_GRAD"), any(InterviewSyllabus.class), eq(List.of(domain)))).thenReturn(ledger);
        when(firstQuestionGenerationService.generateAndSave(any(InterviewSession.class), any())).thenReturn(firstQuestion);
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(1);

        PlannerOrchestrationService service = new PlannerOrchestrationService(
                sessionMapper,
                resumeMapper,
                positionService,
                aiClient,
                ledgerInitService,
                firstQuestionGenerationService,
                new PlannerHistoryBuilderService(sessionMapper, questionMapper),
                new PlannerDomainNormalizationService(),
                new PlannerHistoryDedupService(),
                new InterviewSyllabusAssembler(),
                new InterviewDebugTraceService(new ObjectMapper()),
                new ObjectMapper()
        );

        service.runAsync(8L);

        verify(sessionMapper).updateById(argThatSession(s ->
                s.getId().equals(8L)
                        && s.getSyllabusJson() != null
                        && "结合 JD、简历和项目真实性规划主线。".equals(s.getSyllabusJson().get("planningReasoning"))
                        && s.getStateLedgerJson() == null
                        && s.getFirstQuestionJson() == null
                        && ((List<?>) s.getSyllabusJson().get("experienceItems")).size() == 1
        ));
        verify(sessionMapper).updateById(argThatSession(s ->
                s.getId().equals(8L)
                        && s.getStateLedgerJson() == ledger
                        && Integer.valueOf(1).equals(s.getStateLedgerJson().get("asked_total"))
                        && s.getFirstQuestionJson() != null
                        && s.getCurrentQuestionNo().equals(1)
                        && "in_progress".equals(s.getStatus())
                        && s.getStartedAt() != null
        ));
        verify(sessionMapper, never()).update(eq(null), any());
    }

    @Test
    @DisplayName("runAsync should filter and backfill unmapped domains instead of aborting")
    void runAsync_shouldFilterAndBackfillUnmappedDomains() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        PositionService positionService = mock(PositionService.class);
        AiClient aiClient = mock(AiClient.class);
        StateLedgerInitService ledgerInitService = mock(StateLedgerInitService.class);
        FirstQuestionGenerationService firstQuestionGenerationService = mock(FirstQuestionGenerationService.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);

        InterviewSession session = buildSession();
        List<PositionSkillDomain> domains = List.of(
                buildDomain(21L, "java_core", "Java 核心基础", "语法、泛型、集合框架、IO", 1),
                buildDomain(22L, "concurrency", "并发编程", "JMM、线程模型、锁机制、线程池", 2),
                buildDomain(23L, "jvm", "JVM 原理", "内存结构、GC 算法、类加载", 3),
                buildDomain(24L, "spring", "Spring 框架", "IoC、AOP、Spring Boot、事务", 4),
                buildDomain(25L, "mysql", "MySQL 数据库", "索引原理、事务、锁、SQL 优化", 5),
                buildDomain(26L, "redis", "Redis 缓存", "数据结构、持久化、缓存策略、集群", 6)
        );
        PlannerOutput plannerOutput = new PlannerOutput();
        plannerOutput.setPlanningReasoning("bad domain");
        plannerOutput.setDomains(List.of(
                domainPlan("unknown_code", "未知域"),
                domainPlan("redis", "Redis 缓存")
        ));
        plannerOutput.setExperienceItems(List.of());
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("overall_status", "IN_PROGRESS");
        InterviewQuestion firstQuestion = buildFirstQuestion();

        when(sessionMapper.selectById(8L)).thenReturn(session);
        when(positionService.listSkillDomainEntities("JAVA_BACKEND")).thenReturn(domains);
        when(aiClient.callPlanner(any())).thenReturn(AiCallResult.<PlannerOutput>builder()
                .output(plannerOutput)
                .build());
        when(ledgerInitService.initLedger(eq(8L), eq("FRESH_GRAD"), any(InterviewSyllabus.class), eq(domains))).thenReturn(ledger);
        when(firstQuestionGenerationService.generateAndSave(any(InterviewSession.class), any())).thenReturn(firstQuestion);
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(1);

        PlannerOrchestrationService service = new PlannerOrchestrationService(
                sessionMapper,
                resumeMapper,
                positionService,
                aiClient,
                ledgerInitService,
                firstQuestionGenerationService,
                new PlannerHistoryBuilderService(sessionMapper, questionMapper),
                new PlannerDomainNormalizationService(),
                new PlannerHistoryDedupService(),
                new InterviewSyllabusAssembler(),
                new InterviewDebugTraceService(new ObjectMapper()),
                new ObjectMapper()
        );

        service.runAsync(8L);

        verify(ledgerInitService).initLedger(eq(8L), eq("FRESH_GRAD"), argThat(syllabus ->
                syllabus != null
                        && syllabus.getDomains() != null
                        && syllabus.getDomains().size() == 5
                        && syllabus.getDomains().stream().allMatch(domain -> domain.getDomainId() != null)
                        && syllabus.getDomains().stream().anyMatch(domain -> "redis".equals(domain.getDomainCode()))
        ), eq(domains));
        verify(firstQuestionGenerationService).generateAndSave(any(), any());
        verify(sessionMapper).updateById(argThatSession(s ->
                s.getId().equals(8L)
                        && "in_progress".equals(s.getStatus())
                        && Integer.valueOf(1).equals(s.getStateLedgerJson().get("asked_total"))
                        && s.getFirstQuestionJson() != null
                        && s.getStateLedgerJson() == ledger
        ));
    }

    @Test
    @DisplayName("runAsync should pass recent covered knowledge points to planner history input")
    void runAsync_shouldPassRecentHistoryIntoPlannerInput() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        PositionService positionService = mock(PositionService.class);
        AiClient aiClient = mock(AiClient.class);
        StateLedgerInitService ledgerInitService = mock(StateLedgerInitService.class);
        FirstQuestionGenerationService firstQuestionGenerationService = mock(FirstQuestionGenerationService.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);

        InterviewSession session = buildSession();
        PositionSkillDomain domain = buildDomain();
        PlannerOutput plannerOutput = buildPlannerOutput();
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("overall_status", "IN_PROGRESS");
        InterviewQuestion firstQuestion = buildFirstQuestion();

        InterviewSession historySession = new InterviewSession();
        historySession.setId(7L);
        historySession.setStatus("completed");
        historySession.setFinishedAt(LocalDateTime.of(2026, 3, 20, 12, 0));
        historySession.setStateLedgerJson(new LinkedHashMap<>(Map.of(
                "covered_points", List.of("Redis / 缓存击穿", "MySQL / 索引优化")
        )));

        when(sessionMapper.selectById(8L)).thenReturn(session);
        when(sessionMapper.selectPlannerRecentSessions(
                eq(1L),
                eq("JAVA_BACKEND"),
                eq(List.of("completed", "report_generating")),
                any(LocalDateTime.class),
                eq(8L),
                eq(3)
        )).thenReturn(List.of(historySession));
        when(positionService.listSkillDomainEntities("JAVA_BACKEND")).thenReturn(List.of(domain));
        when(aiClient.callPlanner(argThat(input ->
                input != null
                        && input.getHistoryInterviews() != null
                        && input.getHistoryInterviews().size() == 1
                        && input.getHistoryInterviews().get(0).getCoveredKnowledgePoints()
                        .containsAll(List.of("Redis / 缓存击穿", "MySQL / 索引优化"))
        ))).thenReturn(AiCallResult.<PlannerOutput>builder()
                .output(plannerOutput)
                .build());
        when(ledgerInitService.initLedger(eq(8L), eq("FRESH_GRAD"), any(InterviewSyllabus.class), eq(List.of(domain)))).thenReturn(ledger);
        when(firstQuestionGenerationService.generateAndSave(any(InterviewSession.class), any())).thenReturn(firstQuestion);
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(1);

        PlannerOrchestrationService service = new PlannerOrchestrationService(
                sessionMapper,
                resumeMapper,
                positionService,
                aiClient,
                ledgerInitService,
                firstQuestionGenerationService,
                new PlannerHistoryBuilderService(sessionMapper, questionMapper),
                new PlannerDomainNormalizationService(),
                new PlannerHistoryDedupService(),
                new InterviewSyllabusAssembler(),
                new InterviewDebugTraceService(new ObjectMapper()),
                new ObjectMapper()
        );

        service.runAsync(8L);

        verify(aiClient).callPlanner(argThat(input ->
                input != null
                        && input.getHistoryInterviews() != null
                        && input.getHistoryInterviews().size() == 1
                        && input.getHistoryInterviews().get(0).getDiscussedItems().isEmpty()
                        && input.getHistoryInterviews().get(0).getStrongPoints().isEmpty()
                        && input.getHistoryInterviews().get(0).getWeakPoints().isEmpty()
        ));
    }

    private InterviewSession buildSession() {
        InterviewSession session = new InterviewSession();
        session.setId(8L);
        session.setUserId(1L);
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("FRESH_GRAD");
        session.setMode("practice");
        session.setFocusTopics("vue");
        session.setCurrentQuestionNo(0);
        return session;
    }

    private PositionSkillDomain buildDomain() {
        return buildDomain(21L, "java_core", "Java 核心基础", null, 1);
    }

    private PositionSkillDomain buildDomain(Long id, String domainCode, String domainName, String description, int sortOrder) {
        PositionSkillDomain domain = new PositionSkillDomain();
        domain.setId(id);
        domain.setPositionCode("JAVA_BACKEND");
        domain.setDomainCode(domainCode);
        domain.setDomainName(domainName);
        domain.setDescription(description);
        domain.setVersion(1);
        domain.setSortOrder(sortOrder);
        return domain;
    }

    private PlannerOutput buildPlannerOutput() {
        PlannerOutput.ExperienceItem item = new PlannerOutput.ExperienceItem();
        item.setItemType("PROJECT");
        item.setItemName("订单系统");
        item.setResumeDescription("负责订单核心链路和缓存优化。");
        item.setTechHooks(List.of("订单超时关闭", "缓存一致性"));

        PlannerOutput output = new PlannerOutput();
        output.setPlanningReasoning("结合 JD、简历和项目真实性规划主线。");
        output.setDomains(List.of(domainPlan("java_core", "Java 核心基础")));
        output.setExperienceItems(List.of(item));
        return output;
    }

    private PlannerOutput.DomainPlan domainPlan(String domainCode, String domainName) {
        PlannerOutput.DomainPlan domainPlan = new PlannerOutput.DomainPlan();
        domainPlan.setDomainCode(domainCode);
        domainPlan.setDomainName(domainName);
        domainPlan.setFocusPoints(List.of("集合扩容机制", "线程池拒绝策略"));
        return domainPlan;
    }

    private InterviewQuestion buildFirstQuestion() {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(101L);
        question.setQuestionNo(1);
        question.setQuestionType("INTRO");
        question.setStem("请你先做一个简短的自我介绍。");
        question.setTargetSkill("沟通表达与项目概述");

        Map<String, Object> generationContext = new LinkedHashMap<>();
        generationContext.put("aiResultStatus", "success");
        question.setGenerationContextJson(generationContext);
        return question;
    }

    private InterviewSession argThatSession(java.util.function.Predicate<InterviewSession> predicate) {
        return argThat(predicate::test);
    }
}
