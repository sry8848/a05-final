package com.a05.aiinterview.rag.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KnowledgeDocument tests")
class KnowledgeDocumentTest {

    @Test
    @DisplayName("toDenseRetrievalText should keep only question text and intent concept")
    void toDenseRetrievalText_shouldKeepOnlyQuestionTextAndIntentConcept() {
        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("redis-cache-penetration-001")
                .questionText("讲一下 Redis 缓存穿透")
                .intentConcept("考察空值缓存、布隆过滤器和数据库保护方案")
                .referenceContext("高并发查询不存在数据时，缓存层需要做兜底，避免数据库被持续打穿。")
                .scoringKeyPoints(List.of("缓存空对象", "布隆过滤器", "方案局限性"))
                .scoringPitfalls(List.of("混淆穿透和击穿"))
                .keywords(List.of("Redis", "缓存穿透", "布隆过滤器"))
                .questionType("PRINCIPLE")
                .difficulty("L2")
                .source("manual_curated")
                .active(true)
                .version("v1")
                .build();

        String denseText = document.toDenseRetrievalText();

        assertThat(denseText)
                .contains("讲一下 Redis 缓存穿透")
                .contains("考察空值缓存、布隆过滤器和数据库保护方案")
                .doesNotContain("高并发查询不存在数据时")
                .doesNotContain("缓存空对象")
                .doesNotContain("方案局限性")
                .doesNotContain("Redis\n缓存穿透\n布隆过滤器");
    }

    @Test
    @DisplayName("toSparseRetrievalText should keep question text keywords and scoring points only")
    void toSparseRetrievalText_shouldKeepQuestionTextKeywordsAndScoringPointsOnly() {
        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("project-seata-xid-001")
                .questionText("项目里 Seata XID 丢失怎么定位和修复")
                .intentConcept("考察 Seata AT 模式、XID 透传、Feign 调用链")
                .referenceContext("候选人需要说明问题背景、团队分工、上线节奏，以及如何和产品沟通回滚窗口。")
                .scoringKeyPoints(List.of("Seata", "XID", "Feign", "Header 透传", "全局事务"))
                .scoringPitfalls(List.of("只背 Seata 注解，不讲真实定位链路"))
                .keywords(List.of("Seata", "XID", "Feign", "Header透传"))
                .questionType("PROJECT")
                .difficulty("L4")
                .source("manual_curated")
                .active(true)
                .version("v1")
                .build();

        String sparseText = document.toSparseRetrievalText();

        assertThat(sparseText)
                .contains("项目里 Seata XID 丢失怎么定位和修复")
                .contains("Seata")
                .contains("XID")
                .contains("Feign")
                .contains("Header 透传")
                .doesNotContain("考察 Seata AT 模式")
                .doesNotContain("团队分工")
                .doesNotContain("沟通回滚窗口")
                .doesNotContain("只背 Seata 注解");
    }
}
