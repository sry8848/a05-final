package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlannerDomainNormalizationService tests")
class PlannerDomainNormalizationServiceTest {

    private final PlannerDomainNormalizationService service = new PlannerDomainNormalizationService();

    @Test
    @DisplayName("all illegal domains should be dropped and backfilled to minimum allowed size")
    void normalize_shouldDropIllegalDomainsAndBackfillMinimum() {
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("test")
                .domains(List.of(domain("spring_cloud", "Spring Cloud 微服务", List.of("注册中心")),
                        domain("rabbitmq", "RabbitMQ", List.of("消息可靠性"))))
                .experienceItems(List.of())
                .build();

        PlannerDomainNormalizationService.NormalizationResult result = service.normalize(output, buildAllowedDomains());

        assertThat(result.rawDomainsCount()).isEqualTo(2);
        assertThat(result.droppedDomains()).containsExactly(
                "domainCode=spring_cloud, domainName=Spring Cloud 微服务",
                "domainCode=rabbitmq, domainName=RabbitMQ"
        );
        assertThat(result.backfilledDomains()).containsExactly(
                "java_core", "concurrency", "jvm", "spring", "mysql"
        );
        assertThat(result.normalizedOutput().getDomains())
                .extracting(PlannerOutput.DomainPlan::getDomainCode)
                .containsExactly("java_core", "concurrency", "jvm", "spring", "mysql");
        assertThat(result.normalizedOutput().getDomains().get(0).getFocusPoints()).containsExactly("Java 语法", "泛型");
    }

    @Test
    @DisplayName("mixed valid and invalid domains should keep valid order and backfill remainder")
    void normalize_shouldKeepValidOrderAndBackfillRemainder() {
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("test")
                .domains(List.of(
                        domain("redis", "Redis 缓存", List.of("缓存击穿互斥锁")),
                        domain("unknown_code", "未知域", List.of("foo")),
                        domain("mysql", "MySQL 数据库", List.of("事务隔离级别")),
                        domain("redis", "Redis 缓存", List.of("缓存雪崩"))
                ))
                .experienceItems(List.of())
                .build();

        PlannerDomainNormalizationService.NormalizationResult result = service.normalize(output, buildAllowedDomains());

        assertThat(result.droppedDomains()).containsExactly("domainCode=unknown_code, domainName=未知域");
        assertThat(result.backfilledDomains()).containsExactly("java_core", "concurrency", "jvm");
        assertThat(result.normalizedOutput().getDomains())
                .extracting(PlannerOutput.DomainPlan::getDomainCode)
                .containsExactly("redis", "mysql", "java_core", "concurrency", "jvm");
        assertThat(result.normalizedOutput().getDomains().get(0).getFocusPoints()).containsExactly("缓存击穿互斥锁");
    }

    @Test
    @DisplayName("more than max valid domains should be truncated to eight")
    void normalize_shouldTruncateToMaxDomains() {
        List<PositionSkillDomain> allowed = List.of(
                allowed(1L, "d1", "D1", "a、b", 1),
                allowed(2L, "d2", "D2", "a、b", 2),
                allowed(3L, "d3", "D3", "a、b", 3),
                allowed(4L, "d4", "D4", "a、b", 4),
                allowed(5L, "d5", "D5", "a、b", 5),
                allowed(6L, "d6", "D6", "a、b", 6),
                allowed(7L, "d7", "D7", "a、b", 7),
                allowed(8L, "d8", "D8", "a、b", 8),
                allowed(9L, "d9", "D9", "a、b", 9)
        );
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("test")
                .domains(List.of(
                        domain("d1", "D1", List.of("p1")),
                        domain("d2", "D2", List.of("p2")),
                        domain("d3", "D3", List.of("p3")),
                        domain("d4", "D4", List.of("p4")),
                        domain("d5", "D5", List.of("p5")),
                        domain("d6", "D6", List.of("p6")),
                        domain("d7", "D7", List.of("p7")),
                        domain("d8", "D8", List.of("p8")),
                        domain("d9", "D9", List.of("p9"))
                ))
                .experienceItems(List.of())
                .build();

        PlannerDomainNormalizationService.NormalizationResult result = service.normalize(output, allowed);

        assertThat(result.normalizedOutput().getDomains()).hasSize(8);
        assertThat(result.normalizedOutput().getDomains())
                .extracting(PlannerOutput.DomainPlan::getDomainCode)
                .containsExactly("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8");
        assertThat(result.backfilledDomains()).isEmpty();
    }

    @Test
    @DisplayName("when allowed domains are fewer than minimum should use all available")
    void normalize_shouldUseAllAvailableWhenAllowedLessThanMinimum() {
        List<PositionSkillDomain> allowed = List.of(
                allowed(1L, "browser", "浏览器原理", "渲染流程、Event Loop、缓存", 1),
                allowed(2L, "network", "网络基础", "HTTP、HTTPS、WebSocket", 2),
                allowed(3L, "performance", "前端性能优化", "加载优化、渲染优化、首屏优化", 3)
        );
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("test")
                .domains(List.of(domain("unknown", "未知域", List.of("foo"))))
                .experienceItems(List.of())
                .build();

        PlannerDomainNormalizationService.NormalizationResult result = service.normalize(output, allowed);

        assertThat(result.normalizedOutput().getDomains())
                .extracting(PlannerOutput.DomainPlan::getDomainCode)
                .containsExactly("browser", "network", "performance");
        assertThat(result.backfilledDomains()).containsExactly("browser", "network", "performance");
    }

    @Test
    @DisplayName("numeric id or localized name should not be treated as legal domain code")
    void normalize_shouldRejectNumericIdAndNameFallback() {
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("test")
                .domains(List.of(
                        domain("21", "Java 核心基础", List.of("泛型")),
                        domain("", "Redis 缓存", List.of("缓存一致性"))
                ))
                .experienceItems(List.of())
                .build();

        PlannerDomainNormalizationService.NormalizationResult result = service.normalize(output, buildAllowedDomains());

        assertThat(result.droppedDomains()).containsExactly(
                "domainCode=21, domainName=Java 核心基础",
                "domainCode=, domainName=Redis 缓存"
        );
        assertThat(result.normalizedOutput().getDomains())
                .extracting(PlannerOutput.DomainPlan::getDomainCode)
                .containsExactly("java_core", "concurrency", "jvm", "spring", "mysql");
    }

    @Test
    @DisplayName("when allowed domains are empty should continue with empty domains")
    void normalize_shouldReturnEmptyWhenAllowedDomainsMissing() {
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("test")
                .domains(List.of(domain("unknown", "未知域", List.of("foo"))))
                .experienceItems(List.of())
                .build();

        PlannerDomainNormalizationService.NormalizationResult result = service.normalize(output, List.of());

        assertThat(result.normalizedOutput().getDomains()).isEmpty();
        assertThat(result.backfilledDomains()).isEmpty();
        assertThat(result.droppedDomains()).containsExactly("domainCode=unknown, domainName=未知域");
    }

    private List<PositionSkillDomain> buildAllowedDomains() {
        return List.of(
                allowed(21L, "java_core", "Java 核心基础", "Java 语法、泛型、集合框架、IO", 1),
                allowed(22L, "concurrency", "并发编程", "JMM、线程模型、锁机制、线程池", 2),
                allowed(23L, "jvm", "JVM 原理", "内存结构、GC 算法、类加载", 3),
                allowed(24L, "spring", "Spring 框架", "IoC、AOP、Spring Boot、事务", 4),
                allowed(25L, "mysql", "MySQL 数据库", "索引原理、事务、锁、SQL 优化", 5),
                allowed(26L, "redis", "Redis 缓存", "数据结构、持久化、缓存策略、集群", 6)
        );
    }

    private PlannerOutput.DomainPlan domain(String code, String name, List<String> focusPoints) {
        return PlannerOutput.DomainPlan.builder()
                .domainCode(code)
                .domainName(name)
                .focusPoints(focusPoints)
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
