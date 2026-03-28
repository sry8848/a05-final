# 面试 RAG 轻量生产化计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不明显加重架构的前提下，把当前题库式 RAG 从“验证可跑通”提升到“真实更好用、更现代、可持续优化”的实现。

**Architecture:** 保持单库题目卡片、单套 Qdrant、单条出题链路，不引入 Elasticsearch，不新增第二套搜索系统，不做知识图谱。优化顺序固定为：先补评测和瓶颈证据，再优先使用 Qdrant 原生能力升级 hybrid 检索，最后把商业文本排序 API 作为默认精排方案接入所有已进入检索链路的请求。

**Tech Stack:** Java 21、Spring Boot、Spring AI、Qdrant、OpenAI-compatible Embedding、JUnit 5、Maven

---

## 先统一原则

这份计划只接受下面三条原则：

1. **先问“现有栈原生支持吗”，再问“要不要加系统”**
- 当前优先方案是继续用 Qdrant 原生能力。
- 不默认引入 ES。
- 不手写一套搜索引擎。

2. **先问“这是当前瓶颈吗”，再决定要不要加复杂度**
- 如果评测没证明 sparse 是瓶颈，就不要先堆 sparse 技术名词。
- 如果评测没证明 rerank 需要覆盖更多候选，就不要盲目放大候选集。

3. **所有优化都必须服务“更好用”，不是服务“更现代的名词”**
- 你们真正要的是：
  - 题目更像真人
  - 技术题更准
  - 项目题不跑偏
  - 行为题不被技术污染
- 不是“看起来像一个很高级的检索系统”。

---

## 当前实现的真实位置

当前实现已经具备：
- 真实 dense recall
- sparse fallback
- RRF
- 业务重排
- `retrievedMaterials` 注入出题链路
- 真实 Qdrant + Embedding 的评测

它的问题不是“完全不能用”，而是：

1. **当前 sparse 路还偏工程 fallback**
- 现在是可用，但不够优雅。
- 需要判断能不能切到 Qdrant 原生 query/hybrid 能力。

2. **排序仍然主要靠规则分**
- 这对理论题还行。
- 对复杂场景题、项目题的帮助还不够稳定。
- 当前已经值得升级为默认商业精排，但不应该扩大检索触发范围。

3. **评测已经证明“能检索到”，但还没充分证明“生成的下一题更好”**
- 这是当前最重要的缺口。

4. **还缺最小线上观测**
- 现在 debug 和测试够用了。
- 但离“线上可定位问题”还差一层轻量审计。

一句话判断：

**现在这版该做的是“原地提纯”，不是“扩系统”。**

---

## 方案比较

### 方案 A：上 Elasticsearch 或新搜索系统

优点：
- 理论上能更强

缺点：
- 架构立刻变重
- 运维复杂度明显上升
- 当前阶段收益不一定大于成本

**不推荐。**

### 方案 B：继续单库 Qdrant，优先使用原生 Query / Hybrid 能力

优点：
- 保持当前架构
- 复杂度最可控
- 技术路线现代，但不臃肿

缺点：
- 需要先确认 Java 侧接入成本和当前版本兼容性

**推荐。**

### 方案 C：不改检索，只继续调规则和 prompt

优点：
- 最省事

缺点：
- 很容易把问题掩盖在 prompt 里
- 后面会越来越难判断问题在哪

**不推荐。**

---

## 推荐设计

### 1. 检索系统保持单库，不引 ES

继续保留：
- 单个 Qdrant
- 单个题库索引
- 单套入库链路

明确禁止：
- 引入 Elasticsearch
- 再加一套独立关键词搜索服务
- 因为“BM25”三个字就去扩系统

### 2. sparse 优化优先走 Qdrant 原生路线

第一优先级不是“上 BM25”，而是：

**先验证 Qdrant 原生 Query API 能不能优雅替代当前 fallback sparse 路。**

优先顺序：

1. **首选：Qdrant 原生 hybrid / sparse vectors / fusion**
- 如果当前 Java 客户端和服务端组合能平滑接通，就走这条。

2. **次选：Qdrant full-text / text match + dense + RRF**
- 如果 sparse vectors 在当前 Java 栈接起来明显不优雅，就先走这条。
- 它不一定是标准 BM25，但对你们当前题库规模足够轻、足够稳。

3. **最后才讨论更重方案**
- 只有上面两条都证明不够，才重新评估更重的技术选项。

### 3. 排序优化改成“默认商业精排 + 业务护栏”

已确认的方向：
- 对所有**已进入检索链路**的请求，默认调用商业文本排序 API 做精排
- 不对所有题目强制检索
- 不引入通用大模型 rerank
- 不引入很重的多阶段排序体系

保留并继续使用：
- 改进 query 编译质量
- 提高原生 hybrid 候选质量
- `mustHaveClues / avoidClues / difficultyHint / 项目钩子` 业务护栏

默认精排之后再用评测判断：
- 是否需要扩大候选数
- 是否需要进一步替换或补充业务护栏
- 是否需要更复杂的多阶段排序

### 4. 题库治理保持轻量

当前阶段不做重平台治理，只做 3 件事：
- 清理明显重复卡
- 保证失效卡能停用
- 给题卡保留 `source / version / active`

不要一开始就上：
- 复杂质量标签系统
- 大而全的治理平台
- 很重的 dedupe 流水线

### 5. 线上观测只补最小必要集

当前最值得加的不是一整套实验平台，而是最小审计：
- 本轮是否检索
- dense 候选数
- sparse 候选数
- 最终注入的题卡 id
- follow-up 候选
- 最终题目

这已经够定位大部分问题。

---

## 文件边界

- Modify: `backend/src/test/resources/rag-eval/interview-retrieval-cases.json`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/KnowledgeIngestionService.java`
- Create: `docs/superpowers/reports/2026-03-28-rag-lightweight-hardening-baseline.md`

---

## Chunk 1：先证明瓶颈在哪

### Task 1：把评测从“命中率”扩成“题目质量 + 瓶颈定位”

**Files:**
- Modify: `backend/src/test/resources/rag-eval/interview-retrieval-cases.json`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`
- Create: `docs/superpowers/reports/2026-03-28-rag-lightweight-hardening-baseline.md`

- [ ] **Step 1: 扩样本，但只扩到足够判断问题**

目标样本数：16-20 条。  
不要盲目扩到几十条，先保证结构覆盖：
- 理论题
- 场景题
- 行为题
- 技术钩子项目题
- 泛项目负样本

- [ ] **Step 2: 给每条样本补最小期望**

至少补：
- 是否必须保持项目锚点
- 是否必须避免技术污染
- 期望 top 命中 questionId

- [ ] **Step 3: 写失败测试，先区分是“召回问题”还是“排序问题”**

要求评测能分清：
- dense 没召回
- sparse 没召回
- 融合后没提升
- 排序没把正确题卡放到前面

- [ ] **Step 4: 输出基线报告**

把当前结论写进：
- `docs/superpowers/reports/2026-03-28-rag-lightweight-hardening-baseline.md`

报告必须回答：
- 当前真正瓶颈是什么
- 是否值得先做 native sparse upgrade
- 当前候选质量是否足以支撑“默认商业精排”

- [ ] **Step 5: 运行评测**

Run: `mvn -q "-Dtest=InterviewRagEvaluationTest" test`

Expected:
- PASS
- 且报告能清楚指出下一阶段该优先改哪一层

---

## Chunk 2：优先用好 Qdrant 原生能力

### Task 2：把 sparse fallback 升级成更原生的 Qdrant 路径

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`

- [ ] **Step 1: 先写失败测试**

断言目标不是“必须叫 BM25”，而是：
- sparse 路必须比当前 scroll + 本地关键词打分更原生
- 不能继续只靠大范围 scroll 做 lexical recall

- [ ] **Step 2: 先尝试接 Qdrant 原生 Query API / hybrid 能力**

优先检查：
- 当前 client/server 版本下是否可平滑接 `queryAsync`
- 是否能直接使用 Qdrant 原生 fusion/query 能力

这一步如果实现顺滑，就继续；不要多加系统。

- [ ] **Step 3: 如果 sparse vectors 在当前 Java 栈上接入明显不优雅，改走 Qdrant text/full-text 能力**

要求：
- 仍然留在单库 Qdrant
- 不引 ES
- 不手写 BM25

这里只在现有栈内选“更优雅的原生能力”，不是追求术语最标准。

- [ ] **Step 4: 保留现有 RRF 和业务重排**

这一阶段不要同时改：
- 融合公式
- 精排模型

否则无法判断提升来自哪里。

- [ ] **Step 5: 运行 targeted tests**

Run: `mvn -q "-Dtest=RagRetrievalServiceImplTest,InterviewRagEvaluationTest" test`

Expected:
- PASS
- 技术词密集题的 sparse 命中或最终 top 排序优于当前基线

---

## Chunk 3：把商业文本排序 API 接成默认精排

### Task 3：升级排序，但不引入重型方案

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`

- [ ] **Step 1: 写失败测试**

构造“候选已经基本合理，但当前规则分排错”的样本。
同时断言：
- rerank 只作用于已进入检索链路的请求
- 不会改变 `shouldRetrieve` 路由
- 不会把行为题和泛项目题扩大成强制检索

- [ ] **Step 2: 接商业文本排序 API**

要求：
- 选用阿里百炼文本排序 API
- 默认作用于所有已进入检索链路的请求
- 输入是融合后的候选集，不是全库
- 候选规模保持轻量，建议先控制在 `10-20`

- [ ] **Step 3: 保留业务护栏，改成“rerank 后再做业务校正”**

要求：
- `mustHaveClues / avoidClues / difficultyHint / 项目钩子` 仍然保留
- 但它们不再承担主精排职责，而是承担业务护栏职责
- 最终排序应体现：
  - 先看商业 rerank 分
  - 再叠加必要业务校正

- [ ] **Step 4: 加超时与失败回退**

要求：
- rerank 超时或失败时，回退到当前 RRF + 业务分链路
- 不允许因为 rerank 服务异常阻塞出题主链路
- 回退是运行时容错，不是计划降级策略；默认路径仍然是商业 rerank

- [ ] **Step 4: 运行 targeted tests**

Run: `mvn -q "-Dtest=RagRetrievalServiceImplTest,InterviewRagEvaluationTest" test`

Expected:
- PASS
- `rerankTop3HitRate` 有明确提升
- 行为题技术污染率仍为 0
- 泛项目误检索率仍为 0

---

## Chunk 4：补最小线上可观测性

### Task 4：让线上问题可定位，但不搭重平台

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java`

- [ ] **Step 1: 写失败测试**

断言至少能审计：
- 是否触发检索
- dense 候选数
- sparse 候选数
- 最终注入的 questionId
- followUpCandidates

- [ ] **Step 2: 加最小审计字段**

不要上复杂实验框架，只保证：
- 能定位检索是否被触发
- 能定位命中了哪些题卡
- 能定位最终给模型注入了什么

- [ ] **Step 3: 加最小安全阀**

必须保留：
- 行为题技术污染直接丢弃
- 泛项目题不检索
- 检索失败快速回退为空上下文

- [ ] **Step 4: 运行 targeted tests**

Run: `mvn -q "-Dtest=QuestionStreamServiceBuildInputTest,QuestionStreamServiceReconnectTest" test`

Expected: PASS

---

## Chunk 5：做一次真正的人工抽样

### Task 5：证明“更好用”，而不是只证明“更高级”

**Files:**
- Modify: `docs/superpowers/reports/2026-03-28-rag-lightweight-hardening-baseline.md`

- [ ] **Step 1: 抽样 10-12 条真实案例**

至少包含：
- 理论题 3
- 场景题 3
- 行为题 2
- 项目题 3
- 负样本 1

- [ ] **Step 2: 固定审查模板**

每条样本审查：
- query brief 是否合理
- 命中的题卡是否合理
- 最终题目是否更自然
- 是否有八股漂移
- 是否有技术污染

- [ ] **Step 3: 明确上线门槛**

只保留少量关键门槛：
- denseRecallHitRate 不低于当前基线
- rerankTop3HitRate 有改善或至少不退化
- 行为题技术污染率 = 0
- 泛项目误检索率 = 0

- [ ] **Step 4: 写结论**

必须明确回答：
- 哪些优化值得做
- 哪些优化现在不值得做
- 是否需要再讨论更重方案

---

## 实施提醒

- 不要把“现代”理解成“系统越多越好”。
- 不要把“BM25”机械翻译成“必须上 ES”。
- 不要把“全量强开 rerank”理解成“所有题目都强制检索”。
- 不要把线上观测做成平台工程。
- 如果 Qdrant 原生能力已经够用，就在现有栈内把它用好。

Plan complete and saved to `docs/superpowers/plans/2026-03-28-interview-rag-production-hardening.md`. Ready to execute?
