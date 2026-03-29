# 面试 RAG 轻量生产化计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不增加第二套搜索系统的前提下，把当前题库式 RAG 升级为“Qdrant lexical 预过滤 + dense 主召回 + 商业 rerank 主排序 + 程序硬护栏”的真实可用实现。

**Architecture:** 保持单库题目卡片、单套 Qdrant、单条出题链路。Qdrant 的 text index 只负责 lexical prefilter，不承担独立排序；dense 继续承担主召回；阿里百炼文本排序 API 作为所有已进入检索链路请求的默认精排方案；程序仅保留题型、污染、项目锚点等硬护栏。

**Tech Stack:** Java 21、Spring Boot、Spring AI、Qdrant、DashScope OpenAI-compatible Embedding、DashScope 文本排序 API、JUnit 5、Maven

---

## 先统一结论

这份计划已经排除下面这些方向：

1. **不引入 Elasticsearch**
- 不增加第二套搜索系统
- 不为了“BM25”三个字引入新架构

2. **不做 sparse vectors**
- 当前 Java 栈下接 sparse vector 生成链路不够轻
- 当前目标是更好用，而不是术语最标准

3. **不把 lexical 当独立排序器**
- Qdrant text index / text match 负责预过滤、收缩候选范围
- 不再把 lexical 当成第二条独立召回排序路
- 因此新方案里不再保留 RRF 作为核心融合机制

4. **不把坏上下文交给出题 AI 自己判断**
- 程序必须保留硬护栏
- 不能把“行为题技术污染”“泛项目误检索”甩给生成模型自行规避

---

## 当前实现的真实问题

当前实现已经具备：
- dense 向量召回
- keyword fallback
- 规则重排
- `retrievedMaterials` 注入出题链路
- 真实 Qdrant + 真实 embedding 的评测

但它的核心问题已经很明确：

1. **当前 lexical 路是假实现**
- 现在是 `scroll + 本地 keywordScore`
- 这不是原生 lexical query
- 长期维护价值不高

2. **当前 `mustHaveClues / avoidClues` 的程序匹配太粗**
- 现在主要是字面 `contains`
- 对“解释缓存穿透场景”这种语义要求帮助有限

3. **当前规则重排承担了不该它承担的职责**
- 规则分适合做业务硬护栏
- 不适合继续做主排序器

4. **当前最值得升级的是排序质量，不是架构规模**
- dense 主召回已经能用
- 真正缺的是更强、更语义化的 final ranking

---

## 最终方案

### 1. Qdrant text index / lexical 预过滤

用途：
- 利用 `question_text / intent_concept / keywords` 的 text index
- 对技术专有词和明确关键词做预过滤
- 收缩 dense 主召回的候选范围

边界：
- lexical 不是独立排序器
- 不负责最终分数
- 不再参与 RRF

### 2. dense 继续做主召回

用途：
- 负责“问法不同但考点一致”的主语义召回
- 在 lexical prefilter 之后取轻量候选集

候选规模建议：
- dense 候选：`10-20`
- 最终注入：`3-5`

### 3. 商业 rerank 做主排序

用途：
- 对所有**已进入检索链路**的请求默认启用
- 输入是 dense 候选集，不是全库
- query 不是只传 `queryText`
- 需要把 `mustHaveClues / avoidClues` 编进 ranking brief

边界：
- 不是所有题都强制检索
- 只对 `shouldRetrieve=true` 的请求全量启用 rerank

### 4. 程序规则收缩成硬护栏

程序仍必须保留，但职责收缩为：
- `questionType` 不串题
- 行为题不被技术污染
- 泛项目题不误触发检索
- 项目技术钩子题不丢项目锚点
- rerank 失败时回退到本地排序链路

不再保留：
- `mustHaveClues / avoidClues` 的字面打分主排序

---

## 文件边界

- Modify: `backend/src/test/resources/rag-eval/interview-retrieval-cases.json`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/config/RagProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
- Create: `backend/src/main/java/com/a05/aiinterview/rag/service/RagRerankService.java`
- Create: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/DashScopeRagRerankService.java`
- Create: `docs/superpowers/reports/2026-03-28-rag-lightweight-hardening-baseline.md`

---

## Chunk 1: 先固化评测门禁

### Task 1: 让后续所有优化都有裁判

**Files:**
- Modify: `backend/src/test/resources/rag-eval/interview-retrieval-cases.json`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`
- Create: `docs/superpowers/reports/2026-03-28-rag-lightweight-hardening-baseline.md`

- [ ] **Step 1: 扩充并收敛样本**

目标样本数：`16-20`

至少覆盖：
- 理论题
- 场景题
- 行为题
- 技术钩子项目题
- 泛项目负样本

- [ ] **Step 2: 给每条样本补最小期望**

至少补：
- 期望命中的 `questionId`
- 是否必须保留项目锚点
- 是否必须避免技术污染
- 是否允许完全不检索

- [ ] **Step 3: 写失败测试**

评测必须能区分：
- lexical 预过滤问题
- dense 主召回问题
- rerank 排序问题
- 硬护栏失效问题

- [ ] **Step 4: 输出基线报告**

写入：
- `docs/superpowers/reports/2026-03-28-rag-lightweight-hardening-baseline.md`

报告必须回答：
- 当前 dense 基线是否稳定
- 当前候选是否足以支撑默认商业 rerank
- 当前最容易退化的题型是哪类

- [ ] **Step 5: 运行评测**

Run: `mvn -q "-Dtest=InterviewRagEvaluationTest" test`

Expected: PASS

---

## Chunk 2: 把假 sparse 改成 Qdrant lexical 预过滤

### Task 2: 删除 `scroll + keywordScore`，换成原生 text 路

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`

- [ ] **Step 1: 写失败测试**

断言目标：
- 不允许继续依赖大范围 `scroll` 再本地关键词打分
- lexical 路必须明确承担“候选收缩”职责

- [ ] **Step 2: 为题卡正文相关字段建 text index 使用方式**

优先字段：
- `question_text`
- `intent_concept`
- `keywords`

注意：
- 这里是 lexical prefilter
- 不是独立排序器

- [ ] **Step 3: 改检索执行顺序**

新顺序：
1. lexical prefilter
2. dense 主召回
3. 候选集输出给 rerank

- [ ] **Step 4: 删除 RRF 依赖**

因为新方案不是两路独立排序融合：
- 删除 RRF 主逻辑
- 删除把 lexical 当独立打分路的代码

- [ ] **Step 5: 运行 targeted tests**

Run: `mvn -q "-Dtest=RagRetrievalServiceImplTest,InterviewRagEvaluationTest" test`

Expected:
- PASS
- 技术词题不退化
- 行为题不被技术污染

---

## Chunk 3: 接商业 rerank 作为默认精排

### Task 3: 让商业 rerank 接管主排序

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/rag/service/RagRerankService.java`
- Create: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/DashScopeRagRerankService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`

- [ ] **Step 1: 写失败测试**

断言：
- rerank 默认作用于所有 `shouldRetrieve=true` 的请求
- 不改变 `RagPlanCompiler` 的检索触发策略
- 不把所有题都扩大成强制检索

- [ ] **Step 2: 设计 rerank 输入协议**

query brief 至少包含：
- `queryText`
- `mustHaveClues`
- `avoidClues`
- 当前题型
- 当前 focus

documents 至少包含：
- `questionText`
- `intentConcept`
- `referenceContext`
- `scoringKeyPoints`

- [ ] **Step 3: 接阿里百炼文本排序 API**

要求：
- 输入候选规模控制在 `10-20`
- 默认作为主排序器
- 不直接把全库传给 rerank

- [ ] **Step 4: 运行 targeted tests**

Run: `mvn -q "-Dtest=RagRetrievalServiceImplTest,InterviewRagEvaluationTest" test`

Expected:
- PASS
- `rerankTop3HitRate` 提升或至少不退化

---

## Chunk 4: 把规则重排收缩成硬护栏

### Task 4: 删掉 clue 字面打分主排序

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`

- [ ] **Step 1: 写失败测试**

构造语义匹配但字面不匹配样本，证明：
- `contains` 规则不该继续主导排序

- [ ] **Step 2: 删除 clue 字面主打分**

不再保留：
- `mustHaveClues` 字面加分主排序
- `avoidClues` 字面减分主排序

- [ ] **Step 3: 保留硬护栏**

必须保留：
- 行为题技术污染拦截
- 泛项目误检索拦截
- 项目锚点保护
- rerank 失败时回退本地链路

- [ ] **Step 4: 运行 targeted tests**

Run: `mvn -q "-Dtest=RagRetrievalServiceImplTest,InterviewRagEvaluationTest" test`

Expected:
- PASS
- 行为题技术污染率 = 0
- 泛项目误检索率 = 0

---

## Chunk 5: 补最小审计和人工抽样

### Task 5: 证明“更好用”，而不只是“更复杂”

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
- Modify: `docs/superpowers/reports/2026-03-28-rag-lightweight-hardening-baseline.md`

- [ ] **Step 1: 加最小审计字段**

至少记录：
- 是否触发检索
- lexical prefilter 后候选数
- dense 候选数
- rerank 前 top ids
- rerank 后 top ids
- 最终注入题卡 ids

- [ ] **Step 2: 做人工抽样**

抽样 `10-12` 条：
- 理论题 3
- 场景题 3
- 行为题 2
- 项目题 3
- 负样本 1

- [ ] **Step 3: 固定审查模板**

每条样本审查：
- 检索是否合理
- 注入题卡是否合理
- 最终问题是否更自然
- 是否八股漂移
- 是否技术污染

- [ ] **Step 4: 运行全量测试**

Run: `mvn test`

Expected: PASS

---

## 实施提醒

- 不要再把 lexical filter 误当成 lexical ranking。
- 不要再把 `mustHaveClues / avoidClues` 的字面匹配当主排序。
- 不要把“默认全量启用 rerank”理解成“所有题都强制检索”。
- 不要把坏上下文直接丢给出题模型自己判断。
- 不要为了“现代”继续加第二套搜索系统。

Plan complete and saved to `docs/superpowers/plans/2026-03-28-interview-rag-production-hardening.md`. Ready to execute?
