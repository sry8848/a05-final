package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.a05.aiinterview.position.service.PositionService;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PlannerOrchestrationService JSON persistence tests")
class PlannerOrchestrationServiceJsonUpdateTest {

    @Test
    @DisplayName("runAsync should persist JSON fields via updateById entities instead of wrapper set")
    void runAsync_shouldPersistJsonFieldsViaEntityUpdates() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        PositionService positionService = mock(PositionService.class);
        AiClient aiClient = mock(AiClient.class);
        StateLedgerInitService ledgerInitService = mock(StateLedgerInitService.class);
        FirstQuestionGenerationService firstQuestionGenerationService = mock(FirstQuestionGenerationService.class);

        InterviewSession session = buildSession();
        PositionSkillDomain domain = buildDomain();
        PlannerOutput plannerOutput = buildPlannerOutput(domain);
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("overall_status", "IN_PROGRESS");
        InterviewQuestion firstQuestion = buildFirstQuestion();

        when(sessionMapper.selectById(8L)).thenReturn(session);
        when(positionService.listSkillDomainEntities("JAVA_BACKEND")).thenReturn(List.of(domain));
        when(aiClient.callPlanner(any())).thenReturn(AiCallResult.<PlannerOutput>builder()
                .output(plannerOutput)
                .build());
        when(ledgerInitService.initLedger(8L, plannerOutput, List.of(domain))).thenReturn(ledger);
        when(firstQuestionGenerationService.generateAndSave(session, plannerOutput)).thenReturn(firstQuestion);
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(1);

        PlannerOrchestrationService service = new PlannerOrchestrationService(
                sessionMapper,
                resumeMapper,
                positionService,
                aiClient,
                ledgerInitService,
                firstQuestionGenerationService,
                new ObjectMapper()
        );

        service.runAsync(8L);

        verify(sessionMapper).updateById(argThatSession(s ->
                s.getId().equals(8L)
                        && s.getSyllabusJson() != null
                        && "Java Backend Mock Interview".equals(s.getSyllabusJson().get("title"))
                        && s.getStateLedgerJson() == null
                        && s.getFirstQuestionJson() == null
        ));
        verify(sessionMapper).updateById(argThatSession(s ->
                s.getId().equals(8L)
                        && s.getStateLedgerJson() == ledger
                        && s.getFirstQuestionJson() != null
                        && s.getCurrentQuestionNo().equals(1)
                        && "in_progress".equals(s.getStatus())
                        && s.getStartedAt() != null
        ));
        verify(sessionMapper, never()).update(eq(null), any());
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
        PositionSkillDomain domain = new PositionSkillDomain();
        domain.setId(21L);
        domain.setPositionCode("JAVA_BACKEND");
        domain.setDomainCode("java_core");
        domain.setDomainName("Java 核心基础");
        domain.setVersion(1);
        domain.setSortOrder(1);
        return domain;
    }

    private PlannerOutput buildPlannerOutput(PositionSkillDomain domain) {
        PlannerOutput.DomainPlan domainPlan = new PlannerOutput.DomainPlan();
        domainPlan.setDomainId(domain.getId());
        domainPlan.setDomainCode(domain.getDomainCode());
        domainPlan.setDomainName(domain.getDomainName());
        domainPlan.setTargetDepth("L3");
        domainPlan.setPriority("high");

        PlannerOutput output = new PlannerOutput();
        output.setTitle("Java Backend Mock Interview");
        output.setDomains(List.of(domainPlan));
        output.setProjects(List.of());
        output.setQuestionMixPlan(Map.of("INTRO", 1, "PRINCIPLE", 3));
        output.setFocusAreas(List.of("集合", "并发"));
        return output;
    }

    private InterviewQuestion buildFirstQuestion() {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(101L);
        question.setQuestionNo(1);
        question.setQuestionType("INTRO");
        question.setStem("请你先做一个简短的自我介绍。");
        question.setTargetSkill("沟通表达与项目概述");
        question.setTargetDepth("L1");

        Map<String, Object> generationContext = new LinkedHashMap<>();
        generationContext.put("aiResultStatus", "success");
        question.setGenerationContextJson(generationContext);
        return question;
    }

    private InterviewSession argThatSession(java.util.function.Predicate<InterviewSession> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
