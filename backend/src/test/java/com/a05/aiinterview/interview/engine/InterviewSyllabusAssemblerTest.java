package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.interview.dto.InterviewSyllabus;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewSyllabusAssembler tests")
class InterviewSyllabusAssemblerTest {

    private final InterviewSyllabusAssembler assembler = new InterviewSyllabusAssembler();

    @Test
    @DisplayName("assemble should map domainId by domainCode and generate stable itemKey")
    void assemble_shouldMapDomainIdAndGenerateStableItemKey() {
        PositionSkillDomain domain = new PositionSkillDomain();
        domain.setId(11L);
        domain.setDomainCode("redis");
        domain.setDomainName("Redis");

        PlannerOutput.DomainPlan plannerDomain = new PlannerOutput.DomainPlan();
        plannerDomain.setDomainCode("redis");
        plannerDomain.setDomainName("Redis");
        plannerDomain.setFocusPoints(List.of("缓存击穿互斥锁方案"));

        PlannerOutput.ExperienceItem plannerItem = new PlannerOutput.ExperienceItem();
        plannerItem.setItemType("PROJECT");
        plannerItem.setItemName("苍穹外卖");
        plannerItem.setResumeDescription("负责订单与缓存模块。");
        plannerItem.setTechHooks(List.of("缓存店铺营业状态"));

        PlannerOutput output = new PlannerOutput();
        output.setPlanningReasoning("按 JD 和真实项目规划。");
        output.setDomains(List.of(plannerDomain));
        output.setExperienceItems(List.of(plannerItem));

        InterviewSyllabus syllabus = assembler.assemble(output, List.of(domain));

        assertThat(syllabus.getPlanningReasoning()).isEqualTo("按 JD 和真实项目规划。");
        assertThat(syllabus.getDomains()).hasSize(1);
        assertThat(syllabus.getDomains().get(0).getDomainId()).isEqualTo(11L);
        assertThat(syllabus.getExperienceItems()).hasSize(1);
        assertThat(syllabus.getExperienceItems().get(0).getItemKey()).isEqualTo("project_cang_qiong_wai_mai");
    }

    @Test
    @DisplayName("assemble should fallback to domain id or name when planner domainCode is invalid")
    void assemble_shouldFallbackToDomainIdOrName() {
        PositionSkillDomain javaCore = new PositionSkillDomain();
        javaCore.setId(1L);
        javaCore.setDomainCode("java_core");
        javaCore.setDomainName("Java 核心基础");

        PositionSkillDomain redis = new PositionSkillDomain();
        redis.setId(6L);
        redis.setDomainCode("redis");
        redis.setDomainName("Redis 缓存");

        PlannerOutput.DomainPlan byId = new PlannerOutput.DomainPlan();
        byId.setDomainCode("1");
        byId.setDomainName("Java 核心基础");
        byId.setFocusPoints(List.of("HashMap 扩容机制"));

        PlannerOutput.DomainPlan byName = new PlannerOutput.DomainPlan();
        byName.setDomainCode("not_real_code");
        byName.setDomainName("Redis 缓存");
        byName.setFocusPoints(List.of("缓存击穿"));

        PlannerOutput output = new PlannerOutput();
        output.setDomains(List.of(byId, byName));
        output.setExperienceItems(List.of());

        InterviewSyllabus syllabus = assembler.assemble(output, List.of(javaCore, redis));

        assertThat(syllabus.getDomains()).hasSize(2);
        assertThat(syllabus.getDomains().get(0).getDomainId()).isEqualTo(1L);
        assertThat(syllabus.getDomains().get(0).getDomainCode()).isEqualTo("java_core");
        assertThat(syllabus.getDomains().get(1).getDomainId()).isEqualTo(6L);
        assertThat(syllabus.getDomains().get(1).getDomainCode()).isEqualTo("redis");
    }
}
