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
 * 样本知识数据加载器。
 *
 * <p>仅当 {@code rag.enabled=true AND rag.init-sample-data=true} 时在应用启动后执行一次，
 * 向 Qdrant 注入覆盖"Java 内存模型"和"JVM 垃圾回收"两个知识域的内置样本数据，
 * 满足 T1.2 DoD 中"至少 2 个知识域有样本数据入库"的要求。
 *
 * <p>生产环境请通过 {@code POST /api/v1/admin/knowledge/ingest} 接口管理真实知识库，
 * 并将 {@code rag.init-sample-data} 设为 false。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"rag.enabled", "rag.init-sample-data"}, havingValue = "true")
public class SampleKnowledgeDataLoader implements ApplicationRunner {

    private final KnowledgeIngestionService knowledgeIngestionService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("样本知识数据加载开始（rag.init-sample-data=true）");
        try {
            List<KnowledgeDocument> samples = buildSampleDocuments();
            int count = knowledgeIngestionService.ingest(samples);
            log.info("样本知识数据加载完成, 写入片段数={}", count);
        } catch (Exception e) {
            log.error("样本知识数据加载失败，不影响应用启动", e);
        }
    }

    /**
     * 内置样本数据：覆盖 Java 后端岗位的两个核心知识域。
     *
     * <p>知识域 1：{@code java_memory_model}（Java 内存模型）
     * <p>知识域 2：{@code jvm_gc}（JVM 垃圾回收）
     */
    private List<KnowledgeDocument> buildSampleDocuments() {
        return List.of(
                // ── 知识域 1：Java 内存模型 ─────────────────────────────
                KnowledgeDocument.builder()
                        .knowledgeType("job_knowledge")
                        .domainCode("java_memory_model")
                        .positionCode("backend_java")
                        .difficulty("L3")
                        .source("jsr133_jmm")
                        .version("v1")
                        .content("""
                                Java 内存模型（Java Memory Model，JMM）是 Java 规范的核心部分（JSR-133），\
                                定义了多线程程序中共享变量的可见性、有序性和原子性规则。

                                **主内存与工作内存**
                                每个线程拥有独立的工作内存（寄存器/CPU 缓存抽象），所有变量均存储在主内存中。\
                                线程操作变量时，先将主内存中的副本读入工作内存，修改后再写回主内存。\
                                线程间通过主内存进行间接通信，无法直接读写对方的工作内存。

                                **可见性**
                                一个线程对共享变量的修改，何时对另一个线程可见，由 JMM 的 happens-before 规则决定。\
                                若缺少同步，JIT 编译器和 CPU 的指令重排可能导致可见性问题（如死循环读到旧值）。

                                **volatile 语义**
                                volatile 写操作会将工作内存中的值立即刷新到主内存；\
                                volatile 读操作会使线程工作内存中的副本失效，强制从主内存重新读取。\
                                因此 volatile 保证可见性，但不保证复合操作的原子性（如 i++ 仍不安全）。

                                **happens-before 规则**
                                - 程序顺序规则：同一线程内，前一操作 happens-before 后一操作。
                                - volatile 规则：volatile 写 happens-before 后续 volatile 读。
                                - 锁规则：unlock happens-before 后续对同一锁的 lock。
                                - 线程启动规则：start() happens-before 线程内所有操作。
                                - 线程终止规则：所有操作 happens-before join() 返回。
                                """)
                        .build(),

                KnowledgeDocument.builder()
                        .knowledgeType("interview_question")
                        .domainCode("java_memory_model")
                        .positionCode("backend_java")
                        .difficulty("L3")
                        .questionType("PRINCIPLE")
                        .source("team_internal")
                        .version("v1")
                        .content("""
                                **面试题：volatile 能否代替 synchronized？请从 JMM 角度分析。**

                                考察点：可见性 vs 原子性的区别；volatile 的适用场景边界。

                                黄金骨架回答要素：
                                1. volatile 保证可见性和有序性（禁止编译器/CPU 对 volatile 变量前后的指令重排），\
                                   但不保证原子性。
                                2. synchronized 同时保证可见性、有序性和原子性（临界区内操作互斥执行）。
                                3. 适用场景：状态标志位（如 stop 变量）可用 volatile；计数器、复合检查（check-then-act）\
                                   必须用 synchronized 或 Atomic 类。
                                4. 典型错误：用 volatile int count; count++ 以为线程安全，实际 count++ 是读-改-写三步操作，\
                                   非原子。

                                常见误区：认为 volatile 是"轻量级 synchronized"可以完全替代。\
                                正确表述：两者解决不同问题，volatile 是单变量可见性保障，synchronized 是临界区互斥访问保障。
                                """)
                        .build(),

                // ── 知识域 2：JVM 垃圾回收 ─────────────────────────────
                KnowledgeDocument.builder()
                        .knowledgeType("job_knowledge")
                        .domainCode("jvm_gc")
                        .positionCode("backend_java")
                        .difficulty("L3")
                        .source("oracle_jvm_spec")
                        .version("v1")
                        .content("""
                                JVM 垃圾回收（Garbage Collection，GC）负责自动管理堆内存，\
                                回收不可达对象释放内存空间。

                                **堆内存分代模型**
                                传统分代 GC（G1 之前）将堆划分为年轻代（Young）和老年代（Old）：
                                - 年轻代：Eden + 两个 Survivor（S0/S1），大部分对象首先分配于 Eden。
                                - Minor GC：年轻代满时触发，采用复制算法，存活对象晋升至 Survivor 或老年代。
                                - Major/Full GC：老年代满时触发，通常耗时更长，影响吞吐量。

                                **可达性分析**
                                JVM 以 GC Roots（局部变量表中的引用、静态变量、JNI 引用等）为起点，\
                                遍历引用链，无法到达的对象即为垃圾。引用计数法无法处理循环引用，Java 不采用。

                                **常见 GC 算法**
                                - 标记-清除：两阶段，产生碎片。
                                - 标记-整理：整理阶段移动对象，无碎片，但 STW（Stop-The-World）时间较长。
                                - 复制算法：年轻代常用，效率高但空间利用率低。
                                - G1 GC：将堆分为大小相等的 Region，兼顾吞吐量与停顿时间可预测性，JDK 9+ 默认。
                                - ZGC / Shenandoah：低延迟 GC，停顿时间目标在 10ms 以内。

                                **常见 GC 调优参数**
                                -Xms / -Xmx：初始堆 / 最大堆大小。
                                -XX:NewRatio：年轻代与老年代比例。
                                -XX:+UseG1GC / -XX:+UseZGC：指定 GC 算法。
                                -XX:MaxGCPauseMillis：G1 停顿时间目标（软目标）。
                                """)
                        .build(),

                KnowledgeDocument.builder()
                        .knowledgeType("interview_question")
                        .domainCode("jvm_gc")
                        .positionCode("backend_java")
                        .difficulty("L4")
                        .questionType("SCENARIO")
                        .source("team_internal")
                        .version("v1")
                        .content("""
                                **面试题：线上服务频繁 Full GC，你如何排查和优化？**

                                考察点：GC 日志分析能力、堆内存监控工具使用、对象晋升规律理解。

                                黄金骨架排查步骤：
                                1. 确认现象：通过 jstat -gcutil <pid> 1000 观察 Full GC 频率；\
                                   检查 GC 日志（-Xlog:gc* 或 -XX:+PrintGCDetails），确认 Full GC 触发原因。
                                2. 常见原因分类：
                                   a) 老年代空间不足：对象晋升速率过快（大对象直入老年代、Survivor 空间不足），\
                                      可增大年轻代或 Survivor 空间。
                                   b) 元空间（Metaspace）溢出：动态类加载过多，调大 -XX:MaxMetaspaceSize。
                                   c) 显式 System.gc() 调用：添加 -XX:+DisableExplicitGC 屏蔽。
                                   d) 内存泄漏：对象本应被回收却一直持有强引用，需用 heap dump 分析（jmap / MAT）。
                                3. 工具链：jstat / jmap / jvisualvm / GCEasy / Arthas。
                                4. 优化思路：对象复用（池化）、缩短对象生命周期、选用低延迟 GC（ZGC/Shenandoah）。

                                评分锚点（SCENARIO 题型，L4 深度）：
                                - L2：能说出 Full GC 触发条件。
                                - L3：能用 jstat/jmap 定位问题，区分内存泄漏与配置不当。
                                - L4：有线上实操经验，能描述完整排查流程和配置变更结果。
                                """)
                        .build()
        );
    }
}
