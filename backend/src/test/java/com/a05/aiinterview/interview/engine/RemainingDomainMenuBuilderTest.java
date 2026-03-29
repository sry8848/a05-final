package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.interview.entity.InterviewSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RemainingDomainMenuBuilder tests")
class RemainingDomainMenuBuilderTest {

    private final RemainingDomainMenuBuilder builder = new RemainingDomainMenuBuilder();

    @Test
    @DisplayName("should filter covered domains and keep syllabus order")
    void shouldFilterCoveredDomainsAndKeepSyllabusOrder() {
        InterviewSession session = new InterviewSession();
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "java_core",
                                "domainName", "Java 核心基础",
                                "focusPoints", List.of("HashMap 扩容")
                        ),
                        Map.of(
                                "domainCode", "redis",
                                "domainName", "Redis 缓存",
                                "focusPoints", List.of("缓存一致性")
                        ),
                        Map.of(
                                "domainCode", "mysql",
                                "domainName", "MySQL 数据库",
                                "focusPoints", List.of("间隙锁")
                        )
                )
        ));
        session.setStateLedgerJson(Map.of(
                "domain_states", List.of(
                        Map.of("domainCode", "redis", "status", "COVERED")
                )
        ));

        List<EvaluationDecisionInput.RemainingTargetDomain> remaining = builder.build(session);

        assertThat(remaining).extracting(EvaluationDecisionInput.RemainingTargetDomain::getDomainCode)
                .containsExactly("java_core", "mysql");
        assertThat(remaining.getFirst().getFocusPoints()).containsExactly("HashMap 扩容");
        assertThat(remaining.get(1).getFocusPoints()).containsExactly("间隙锁");
    }
}
