package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlannerHistoryDedupService tests")
class PlannerHistoryDedupServiceTest {

    private final PlannerHistoryDedupService service = new PlannerHistoryDedupService();

    @Test
    @DisplayName("should remove recently covered focus points without deleting domains")
    void dedup_shouldRemoveCoveredFocusPointsButKeepDomains() {
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("test")
                .domains(List.of(
                        domain("redis", "Redis 缓存", List.of("Cache Breakdown Mutual Lock", "缓存雪崩")),
                        domain("mysql", "MySQL 数据库", List.of("事务隔离级别"))
                ))
                .experienceItems(List.of())
                .build();

        List<PlannerInput.HistoryInterviewItem> history = List.of(historyItem(List.of("  cache　breakdown   mutual lock ")));

        PlannerOutput deduped = service.deduplicate(output, allowedDomains(), history);

        assertThat(deduped.getDomains()).hasSize(2);
        assertThat(deduped.getDomains().get(0).getDomainCode()).isEqualTo("redis");
        assertThat(deduped.getDomains().get(0).getFocusPoints()).containsExactly("缓存雪崩");
        assertThat(deduped.getDomains().get(1).getFocusPoints()).containsExactly("事务隔离级别");
    }

    @Test
    @DisplayName("should fallback to description when a domain loses all focus points after dedup")
    void dedup_shouldFallbackToDescriptionWhenDomainBecomesEmpty() {
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("test")
                .domains(List.of(domain("redis", "Redis 缓存", List.of("缓存击穿互斥锁"))))
                .experienceItems(List.of())
                .build();

        List<PlannerInput.HistoryInterviewItem> history = List.of(historyItem(List.of("缓存击穿互斥锁")));

        PlannerOutput deduped = service.deduplicate(output, allowedDomains(), history);

        assertThat(deduped.getDomains()).hasSize(1);
        assertThat(deduped.getDomains().get(0).getFocusPoints()).containsExactly("持久化", "缓存雪崩");
    }

    @Test
    @DisplayName("should keep first original focus point when description cannot provide fallback")
    void dedup_shouldKeepFirstOriginalFocusPointWhenDescriptionMissing() {
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("test")
                .domains(List.of(domain("mysql", "MySQL 数据库", List.of("Transaction Isolation"))))
                .experienceItems(List.of())
                .build();

        List<PlannerInput.HistoryInterviewItem> history = List.of(historyItem(List.of("transaction isolation")));
        List<PositionSkillDomain> allowedDomains = List.of(
                allowed(25L, "mysql", "MySQL 数据库", "", 1)
        );

        PlannerOutput deduped = service.deduplicate(output, allowedDomains, history);

        assertThat(deduped.getDomains()).hasSize(1);
        assertThat(deduped.getDomains().get(0).getFocusPoints()).containsExactly("Transaction Isolation");
    }

    private List<PositionSkillDomain> allowedDomains() {
        return List.of(
                allowed(26L, "redis", "Redis 缓存", "持久化、缓存雪崩、主从复制", 1),
                allowed(25L, "mysql", "MySQL 数据库", "事务隔离级别、索引优化", 2)
        );
    }

    private PlannerOutput.DomainPlan domain(String code, String name, List<String> focusPoints) {
        return PlannerOutput.DomainPlan.builder()
                .domainCode(code)
                .domainName(name)
                .focusPoints(focusPoints)
                .build();
    }

    private PlannerInput.HistoryInterviewItem historyItem(List<String> coveredKnowledgePoints) {
        return PlannerInput.HistoryInterviewItem.builder()
                .roundType("")
                .interviewAt("2026-03-20T10:00:00")
                .coveredKnowledgePoints(coveredKnowledgePoints)
                .discussedItems(List.of())
                .strongPoints(List.of())
                .weakPoints(List.of())
                .build();
    }

    private PositionSkillDomain allowed(Long id, String code, String name, String description, int sortOrder) {
        PositionSkillDomain domain = new PositionSkillDomain();
        domain.setId(id);
        domain.setDomainCode(code);
        domain.setDomainName(name);
        domain.setDescription(description);
        domain.setSortOrder(sortOrder);
        return domain;
    }
}
