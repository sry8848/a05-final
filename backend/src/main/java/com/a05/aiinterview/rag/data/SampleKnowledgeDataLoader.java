package com.a05.aiinterview.rag.data;

import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 样本题库数据加载器。
 *
 * <p>仅当 {@code rag.enabled=true AND rag.init-sample-data=true} 时在应用启动后执行一次，
 * 向 Qdrant 注入最小题目卡片样本，验证单库题卡入库链路可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"rag.enabled", "rag.init-sample-data"}, havingValue = "true")
public class SampleKnowledgeDataLoader implements ApplicationRunner {

    private final KnowledgeIngestionService knowledgeIngestionService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("样本题库数据加载开始（rag.init-sample-data=true）");
        try {
            List<KnowledgeDocument> samples = buildSampleDocuments();
            int count = knowledgeIngestionService.ingest(samples);
            log.info("样本题库数据加载完成, 写入条数={}", count);
        } catch (Exception e) {
            log.error("样本题库数据加载失败，不影响应用启动", e);
        }
    }

    private List<KnowledgeDocument> buildSampleDocuments() {
        return List.of(
                KnowledgeDocument.builder()
                        .id("java-hashmap-resize-001")
                        .questionText("讲一下 HashMap 扩容机制")
                        .intentConcept("考察触发条件、2 的幂设计、元素迁移规则和线程不安全风险")
                        .referenceContext("HashMap 扩容不只是容量变大，还涉及阈值计算和桶位迁移；候选人如果只能背定义，说明原理掌握不扎实。")
                        .scoringKeyPoints(List.of("达到阈值触发 resize", "容量通常保持 2 的幂", "元素迁移不是简单重算全部 hash"))
                        .scoringPitfalls(List.of("把扩容理解成完全重哈希", "混淆 resize 和 treeify"))
                        .followUpIds(List.of("java-hashmap-thread-unsafe-001"))
                        .domainCode("java_core")
                        .questionType("PRINCIPLE")
                        .difficulty("L2")
                        .keywords(List.of("HashMap", "resize", "2的幂", "rehash"))
                        .source("manual_curated")
                        .active(true)
                        .version("v1")
                        .build(),
                KnowledgeDocument.builder()
                        .id("redis-cache-penetration-001")
                        .questionText("讲一下 Redis 缓存穿透")
                        .intentConcept("考察空值缓存、布隆过滤器和数据库保护方案")
                        .referenceContext("高并发查询不存在数据时，如果缓存层没有兜底，请求会持续打到数据库。")
                        .scoringKeyPoints(List.of("解释缓存穿透场景", "提到缓存空对象", "提到布隆过滤器"))
                        .scoringPitfalls(List.of("把缓存穿透和击穿混淆"))
                        .followUpIds(List.of("redis-bloom-filter-false-positive-001"))
                        .domainCode("redis")
                        .questionType("PRINCIPLE")
                        .difficulty("L2")
                        .keywords(List.of("Redis", "缓存穿透", "布隆过滤器", "空对象缓存"))
                        .source("manual_curated")
                        .active(true)
                        .version("v1")
                        .build(),
                KnowledgeDocument.builder()
                        .id("order-timeout-close-001")
                        .questionText("订单超时关闭，怎么保证幂等和顺序")
                        .intentConcept("考察延迟任务、状态机约束、重复消费处理和补偿思路")
                        .referenceContext("订单超时关闭往往会同时涉及定时任务或延迟消息，如果只会说加锁，通常说明没有真正处理过链路问题。")
                        .scoringKeyPoints(List.of("状态流转校验", "消费幂等", "顺序错乱处理", "补偿或重试"))
                        .scoringPitfalls(List.of("只说加锁，不说明链路", "不区分重复关闭和重复支付"))
                        .followUpIds(List.of("order-mq-idempotency-001"))
                        .domainCode("distributed")
                        .questionType("SCENARIO")
                        .difficulty("L3")
                        .keywords(List.of("订单超时关闭", "MQ", "幂等", "状态机"))
                        .source("manual_curated")
                        .active(true)
                        .version("v1")
                        .build(),
                KnowledgeDocument.builder()
                        .id("behavior-conflict-product-001")
                        .questionText("讲一次你和产品意见不一致但最终推动事情落地的经历")
                        .intentConcept("考察事实描述、沟通动作、推进过程和复盘能力")
                        .referenceContext("行为题的核心不是价值观口号，而是能不能把冲突背景、你的动作、结果和反思说完整。")
                        .scoringKeyPoints(List.of("说清背景和冲突点", "描述个人动作", "说明结果", "有复盘"))
                        .scoringPitfalls(List.of("只说原则，不说具体事件", "把冲突讲成情绪发泄"))
                        .followUpIds(List.of("behavior-push-hard-problem-001"))
                        .domainCode("")
                        .questionType("BEHAVIORAL")
                        .difficulty("L2")
                        .keywords(List.of("行为面试", "冲突协作", "推动落地"))
                        .source("manual_curated")
                        .active(true)
                        .version("v1")
                        .build(),
                KnowledgeDocument.builder()
                        .id("project-redisson-watchdog-001")
                        .questionText("你们项目里 Redisson 看门狗是怎么工作的")
                        .intentConcept("考察分布式锁续期机制、触发条件和失败场景")
                        .referenceContext("项目里用了 Redisson 并不等于真正理解看门狗机制，很多人只会背 API，不知道续期何时发生、何时失效。")
                        .scoringKeyPoints(List.of("说明默认续期逻辑", "说清显式 leaseTime 的影响", "知道失败场景"))
                        .scoringPitfalls(List.of("把看门狗说成固定租约", "不知道续期线程何时停止"))
                        .followUpIds(List.of("project-redisson-lock-expire-001"))
                        .domainCode("redis")
                        .questionType("PROJECT")
                        .difficulty("L3")
                        .keywords(List.of("Redisson", "看门狗", "分布式锁", "续期"))
                        .source("manual_curated")
                        .active(true)
                        .version("v1")
                        .build(),
                KnowledgeDocument.builder()
                        .id("project-seata-xid-loss-001")
                        .questionText("你们项目里 Seata XID 丢失是怎么定位和修复的")
                        .intentConcept("考察事务上下文传播、Feign 调用链和修复验证方法")
                        .referenceContext("Seata XID 丢失往往发生在跨服务调用链，如果只会说加注解，通常不能说明真实修复过程。")
                        .scoringKeyPoints(List.of("定位 XID 丢失点", "说明透传链路", "说明修复点", "说明验证方法"))
                        .scoringPitfalls(List.of("只会说加注解", "解释不清 Header 透传或拦截器位置"))
                        .followUpIds(List.of("project-seata-undo-log-001"))
                        .domainCode("distributed")
                        .questionType("PROJECT")
                        .difficulty("L4")
                        .keywords(List.of("Seata", "XID", "Feign", "AT", "Header透传"))
                        .source("manual_curated")
                        .active(true)
                        .version("v1")
                        .build(),
                KnowledgeDocument.builder()
                        .id("project-redis-session-context-001")
                        .questionText("你们项目里 Redis 多轮对话上下文的 key 和 TTL 怎么设计")
                        .intentConcept("考察上下文建模、过期策略、容量控制和一致性权衡")
                        .referenceContext("多轮对话上下文设计不是简单把消息丢进 Redis，要考虑 key 结构、TTL、淘汰和回源策略。")
                        .scoringKeyPoints(List.of("说明 key 结构", "说明 TTL 设计", "说明容量或淘汰策略"))
                        .scoringPitfalls(List.of("只说存 Redis，不说结构和过期策略"))
                        .followUpIds(List.of("project-redis-context-eviction-001"))
                        .domainCode("redis")
                        .questionType("PROJECT")
                        .difficulty("L3")
                        .keywords(List.of("Redis", "上下文", "TTL", "key设计", "多轮对话"))
                        .source("manual_curated")
                        .active(true)
                        .version("v1")
                        .build()
        );
    }
}
