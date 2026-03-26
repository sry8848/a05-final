# 面试题库式 RAG 重构实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前“只有 `retrievalPlans` 提示、没有真实检索结果”的链路，重构为基于单库题目卡片的可执行、可评测、低延迟 RAG，服务理论题、场景题、行为题，以及带明确技术钩子的项目题，并把 AI 检索输出从旧的短 query 结构改成更适合后端编译的检索 brief。

**Architecture:** 保留 `evaluation_decision` 先决定下一题 `focus` 的职责，并继续输出 `retrievalPlans` 作为检索 brief；但 brief 内部字段改为 `goal / displayQuery / queryText / keywordHints / difficultyHint / mustHaveClues / avoidClues`，由后端“检索计划编译器”再结合题型、岗位、运行时经验级别、项目上下文编译成单库检索请求。知识库不再拆成 `QUESTION_PATTERN` / `KNOWLEDGE_EVIDENCE` 两类语料，而是统一使用“题目卡片”作为检索单元；每张卡片同时包含题面、考点、参考语境、评分要点、误区、追问关联和元数据。检索执行采用两阶段：先做硬过滤，再并行执行 dense vector recall 与 sparse keyword/BM25 recall，随后用 RRF 融合候选集，再由专用 reranker 或 semantic ranker 做精排，最后叠加 `difficultyHint / mustHaveClues / avoidClues` 的业务分，只把 Top 3-5 条结果注入出题模型。第一版不直接上通用大模型重排，避免延迟和成本失控。

**Tech Stack:** Java 17、Spring Boot、Spring AI VectorStore/Qdrant、Markdown Prompt、JUnit 5、Maven

---

## 目标锁定

- 保留 `retrievalPlans` 外层 JSON 契约，但允许重设计其内部字段，使之更适合单库检索。
- 不做知识图谱。
- 不引入 `followup_dimensions`。
- 只做一个题库索引，不再拆 `QUESTION_PATTERN` / `KNOWLEDGE_EVIDENCE`。
- 单条题目卡片至少包含以下业务字段：
  - `id`
  - `question_text`
  - `intent_concept`
  - `reference_context`
  - `scoring_key_points`
  - `scoring_pitfalls`
  - `follow_up_ids`
  - `domain`
  - `question_type`
  - `difficulty`
  - `keywords`
  - `source`
  - `active`
  - `version`
- `retrieval_text` 作为内部检索拼接字段使用，不要求对外暴露为接口字段。
- AI 检索输出统一改为：
  - `goal`
  - `displayQuery`
  - `queryText`
  - `keywordHints`
  - `difficultyHint`
  - `mustHaveClues`
  - `avoidClues`
- `difficultyHint` 是 AI 对目标难度的提示，不是硬过滤条件。
- 运行时仍保留候选人 `experienceLevel`，但只作为检索编译和重排参考，不直接替代题卡 `difficulty`。
- 难度口径统一为三层，不允许继续混用：
  - `experienceLevel`：候选人资历层级，只用于节奏、题型配额、初始预期范围
  - `difficulty`：题目卡片本身的认知深度，唯一合法取值为 `L1-L5`
  - `difficultyHint`：AI 当前这一轮想问到的目标深度提示，只用于检索编译和重排
- `difficulty` 的业务解释统一为：
  - `L1`：定义和基础概念
  - `L2`：原理和常见用途
  - `L3`：结合具体场景分析或实现
  - `L4`：取舍、排障、优化、深度原理
  - `L5`：复杂系统设计或架构判断
- 旧术语已经全部清理；当前实现与文档只使用 `experienceLevel / difficulty / difficultyHint`。
- 主链路不写死固定默认难度；若历史兼容分支确实必须保留单值兜底，统一使用 `L3`。
- 项目题允许进入同一套题库，但只收“技术钩子型项目题卡片”；泛泛的项目介绍题不依赖这套检索。
- 检索路由按题型分流：
  - `PRINCIPLE`：检索题目卡片
  - `SCENARIO`：检索题目卡片
  - `BEHAVIORAL`：检索题目卡片，但不依赖技术知识字段
  - `PROJECT_DEEP_DIVE`：只有 `focus` 或 `retrievalPlans.displayQuery / queryText / keywordHints` 显示包含明确技术钩子时才检索题目卡片
- 检索链路固定为：
  - 先做硬过滤：`question_type`、`active`、项目题技术钩子适用性
  - 再做多路召回：dense recall + sparse recall
  - 再做融合：优先使用 RRF
  - 再做重排：优先使用专用 reranker / semantic ranker，不直接上通用大模型
  - 最后只向出题模型注入 Top 3-5 条结果
- 初版召回规模不要盲目放大，优先目标：
  - dense recall：Top 20-30
  - sparse recall：Top 20-30
  - 融合去重后：20-40 条
  - 精排后注入：Top 3-5 条
- 评测必须前置，先建立效果检测口径，再改 Prompt、数据结构和检索链路。

## 文件边界

- 修改：`backend/src/main/resources/prompts/evaluation-decision.md`
- 修改：`backend/src/main/resources/prompts/question-generation-stream.md`
- 修改：`backend/src/main/java/com/a05/aiinterview/common/enums/DepthLevel.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/common/enums/ExperienceLevel.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/contract/AiOutputContractValidator.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionGenerationInput.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/rag/dto/KnowledgeDocument.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/rag/dto/RagContext.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/rag/service/KnowledgeIngestionService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/rag/service/impl/NoopRagRetrievalService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/rag/data/SampleKnowledgeDataLoader.java`
- 新建：`backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java`
- 新建：`backend/src/test/resources/rag-eval/interview-retrieval-cases.json`
- 新建：`backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`
- 新建：`backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java`
- 新建：`backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`
- 新建：`backend/src/test/java/com/a05/aiinterview/rag/service/KnowledgeIngestionServiceTest.java`
- 修改：`backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java`
- 修改：`backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java`
- 修改：`backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java`

## Chunk 1：先建立效果检测口径

### Task 1：建立评测样本、指标和阈值

**Files:**
- Create: `backend/src/test/resources/rag-eval/interview-retrieval-cases.json`
- Create: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`

- [ ] **Step 1: 先写失败的评测测试骨架**

评测测试先不要依赖最终实现，只先把指标和断言固定下来。至少覆盖这几类样本：
- `HashMap扩容机制`
- `Redis缓存穿透`
- `订单超时关闭 幂等性 DB+MQ顺序`
- `讲一次和产品意见不一致的经历`
- `Redisson看门狗机制原理`
- `Seata XID 丢失怎么修`
- 1 个“泛项目叙述，不该发起检索”的负样本

测试里先定义这些指标：

```java
assertThat(report.routingAccuracy()).isEqualTo(1.0);
assertThat(report.retrievalApplicableHitRate()).isGreaterThanOrEqualTo(0.8);
assertThat(report.mustHaveCoverage()).isGreaterThanOrEqualTo(0.7);
assertThat(report.avoidPollutionRate()).isLessThanOrEqualTo(0.2);
```

并记录但不一定第一版卡死阈值的辅助指标：
- `denseRecallHitRate`
- `sparseRecallHitRate`
- `fusionLift`
- `rerankTop3HitRate`

- [ ] **Step 2: 编写真正可用的评测集**

在 `interview-retrieval-cases.json` 中为每个样本记录：
- `traceId`
- `questionType`
- `focusPoint`
- `retrievalPlans`
- `shouldRetrieve`
- `mustHaveClues`
- `avoidClues`
- `difficultyHint`
- `expectedKeywords`
- `expectedFollowUpIds`

不要追求全量，先用你们日志里的 12 到 20 条真实样本建第一版金标集。

- [ ] **Step 3: 实现评测报告骨架**

`InterviewRagEvaluationTest` 先完成：
- 读取评测样本
- 校验哪些样本应该检索、哪些应该跳过
- 预留 dense / sparse / fusion / rerank 各阶段命中率统计入口
- 输出清晰的失败报告，能看到具体失效样本

- [ ] **Step 4: 定义延迟预算并写入测试说明**

先把预算写死，不要后面再补：
- 单次可执行检索编译：目标 `< 10ms`
- 单次检索执行：本地开发环境目标 `p95 < 300ms`
- 出题链路额外引入的总延迟预算：目标 `< 500ms`

这里先记录预算，不在第一步就做强性能断言，避免测试抖动。

- [ ] **Step 5: 运行评测测试，确认当前是失败状态**

Run: `mvn -q "-Dtest=InterviewRagEvaluationTest" test`

Expected: FAIL，原因应是检索尚未接通或样本尚未命中

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/resources/rag-eval/interview-retrieval-cases.json backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java
git commit -m "test: add interview rag evaluation harness"
```

## Chunk 2：锁定 Prompt 语义和题型路由

### Task 2：明确何时检索、何时只靠上下文自然出题

**Files:**
- Modify: `backend/src/main/resources/prompts/evaluation-decision.md`
- Modify: `backend/src/main/resources/prompts/question-generation-stream.md`
- Modify: `backend/src/main/java/com/a05/aiinterview/common/enums/DepthLevel.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/common/enums/ExperienceLevel.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/contract/AiOutputContractValidator.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java`

- [ ] **Step 1: 先写失败的 Prompt 覆盖断言**

断言至少检查：
- `retrievalPlans`
- `retrievedMaterials`
- 文案里明确区分“检索 brief”和“真实检索结果”
- 项目题不是默认必检索
- 新检索 brief 字段在契约和 Prompt 中保持一致

- [ ] **Step 2: 修改 `evaluation-decision.md`**

加清楚这几条规则：
- 理论题、场景题默认可以请求检索
- 行为题可以请求检索，但目标是题目素材，不是知识解释
- 项目题只有在 `focus`、`displayQuery`、`queryText` 或 `keywordHints` 显示明确技术钩子时才请求检索
- `retrievalPlans` 是后续检索 brief，不代表已经命中材料
- 废弃旧的 `primaryQuery / alternateQueries / expectedEvidence / avoidEvidence / retrievalType`
- 新输出字段固定为：`goal / displayQuery / queryText / keywordHints / difficultyHint / mustHaveClues / avoidClues`
- `difficultyHint` 只是目标难度提示，不是硬过滤条件

- [ ] **Step 2.1: 统一难度术语注释和 Prompt 语义**

同步收紧下面三者的语义，避免实现时再次漂移：
- `experienceLevel`：候选人资历，不直接等于题目难度
- `difficulty`：题卡深度，唯一使用 `L1-L5`
- `difficultyHint`：当前轮检索和提问目标深度提示

这一步至少要做到：
- `DepthLevel.java` 的注释改成面试语义版
- `ExperienceLevel.java` 的注释去掉“决定目标深度”的表述
- Prompt 中不再把 `experienceLevel` 写成题目难度本身
- `difficultyHint` 在 Prompt 中明确是软约束，不是硬过滤

- [ ] **Step 3: 修改 `EvaluationDecisionOutput` 与校验器**

将 `RetrievalPlan` 内部字段改成：
- `goal`
- `displayQuery`
- `queryText`
- `keywordHints`
- `difficultyHint`
- `mustHaveClues`
- `avoidClues`

校验器要求：
- 所有数组仍然输出 `[]`
- `difficultyHint` 允许为空
- 不再校验 `retrievalType`

- [ ] **Step 4: 修改 `question-generation-stream.md`**

加清楚这几条规则：
- `retrievedMaterials` 非空时，优先使用真实题目卡片材料
- `retrievalPlans` 仅在无真实材料时作为弱提示
- `follow_up_ids` 是可选追问候选，不是强制跳题规则
- 项目题若无检索结果，仍优先依据项目上下文自然追问

- [ ] **Step 5: 更新契约测试和 Prompt 覆盖测试**

Run: `mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest" test`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/prompts/evaluation-decision.md backend/src/main/resources/prompts/question-generation-stream.md backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java backend/src/main/java/com/a05/aiinterview/ai/contract/AiOutputContractValidator.java backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java
git commit -m "refactor: align interview retrieval brief schema with single-corpus rag"
```

## Chunk 3：把单库题目卡片字段落到 DTO 和入库链路

### Task 3：改造 `KnowledgeDocument` 和入库 metadata

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/dto/KnowledgeDocument.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/KnowledgeIngestionService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/data/SampleKnowledgeDataLoader.java`
- Create: `backend/src/test/java/com/a05/aiinterview/rag/service/KnowledgeIngestionServiceTest.java`

- [ ] **Step 1: 先写失败的入库测试**

验证入库后的 metadata 至少包含：
- `question_id`
- `domain`
- `question_type`
- `difficulty`
- `keywords`
- `source`
- `active`
- `version`

同时验证拼接出的 `retrieval_text` 至少覆盖：
- `question_text`
- `intent_concept`
- `reference_context`
- `scoring_key_points`

- [ ] **Step 2: 改造 `KnowledgeDocument`**

从旧的“文档正文 + knowledgeType + domainCode”设计，改成面向题目卡片的字段：
- `id`
- `questionText`
- `intentConcept`
- `referenceContext`
- `scoringKeyPoints`
- `scoringPitfalls`
- `followUpIds`
- `domain`
- `questionType`
- `difficulty`
- `keywords`
- `source`
- `active`
- `version`

保留一个内部 `toRetrievalText()` 或等价方法，用于统一拼接检索文本，不要让各处自己拼。

- [ ] **Step 3: 修改 `KnowledgeIngestionService`**

调整入库行为：
- `Document` 正文写入 `retrieval_text`
- metadata 写入业务字段
- `follow_up_ids` 和 `keywords` 用数组形式保留，避免压成不可解析的大字符串

- [ ] **Step 4: 修改 `SampleKnowledgeDataLoader`**

只先种最小题库样本，不求全：
- 理论题：HashMap、Redis 缓存穿透
- 场景题：订单超时关闭/幂等
- 行为题：冲突协作/推动问题解决
- 项目题：Redisson 看门狗、Seata XID 丢失、Redis 多轮对话上下文

样本必须按新字段填完整，不要只填题面。

- [ ] **Step 5: 运行入库测试**

Run: `mvn -q "-Dtest=KnowledgeIngestionServiceTest" test`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/dto/KnowledgeDocument.java backend/src/main/java/com/a05/aiinterview/rag/service/KnowledgeIngestionService.java backend/src/main/java/com/a05/aiinterview/rag/data/SampleKnowledgeDataLoader.java backend/src/test/java/com/a05/aiinterview/rag/service/KnowledgeIngestionServiceTest.java
git commit -m "feat: remodel interview rag knowledge cards"
```

## Chunk 4：把 brief 编译成单库检索请求

### Task 4：新增检索计划编译器和更完整的请求 DTO

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java`
- Create: `backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java`
- Create: `backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`

- [ ] **Step 1: 先写失败的编译器测试**

至少覆盖这些案例：
- `HashMap扩容机制` -> 应发起检索
- `订单超时关闭 幂等性 DB+MQ顺序` -> 应发起检索
- 行为题 -> 应发起检索，但不要求技术 clue
- `Seata XID 丢失怎么修` -> 应发起检索
- 泛化项目叙述、没有明确技术钩子的项目题 -> 不发起检索
- `difficultyHint=L4` 时不应只召回 `L4`，而应优先允许邻近难度范围

- [ ] **Step 2: 扩展 `RagRetrievalRequest`**

把它从旧的“单 focusPoint 查询”改成可执行请求，至少带上：
- `displayQuery`
- `queryText`
- `keywordQueries`
- `difficultyHint`
- `positionCode`
- `questionType`
- `experienceLevel`
- `domain`
- `projectName`
- `mustHaveClues`
- `avoidClues`
- `shouldRetrieve`

不要引入知识图谱字段。

- [ ] **Step 3: 实现 `RagPlanCompiler`**

职责：
- 读取 `NextQuestionPlan.retrievalPlans`
- 结合题型、岗位、运行时经验级别、项目上下文生成单库检索请求
- 对项目题做严格收窄：只有明确技术钩子才发起检索
- 对跳过检索的场景返回 `shouldRetrieve=false`，不要发明宽泛 query
- 对 `difficultyHint` 只生成“邻近难度范围”或重排参数，不生成等值硬过滤
- 对 `experienceLevel` 只生成默认难度包络或排序偏置，不直接改写题卡 `difficulty`

- [ ] **Step 4: 接回评测测试的路由正确率**

让 `InterviewRagEvaluationTest` 开始校验：
- 哪些样本应该检索
- 哪些样本应该跳过
- 编译后的关键词是否覆盖 `expectedKeywords`

- [ ] **Step 5: 运行编译器测试**

Run: `mvn -q "-Dtest=RagPlanCompilerTest,InterviewRagEvaluationTest" test`

Expected: `RagPlanCompilerTest` PASS，评测测试仍可能部分 FAIL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java
git commit -m "feat: compile interview retrieval briefs into single-corpus requests"
```

## Chunk 5：实现多路召回、融合、精排和结果结构化

### Task 5：在一个题库索引上完成过滤、多路召回、RRF 融合和精排

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/dto/RagContext.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/NoopRagRetrievalService.java`
- Create: `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`

- [ ] **Step 1: 先写失败的检索服务测试**

至少覆盖：
- metadata filter 能按 `question_type`、`domain` 缩小范围
- `keywordQueries` 能参与检索，不会被丢弃
- `avoidClues` 对明显无关结果有降权或剔除效果
- 命中题卡时可以带出 `follow_up_ids`
- `difficultyHint` 只影响排序或范围，不做等值过滤
- dense recall 和 sparse recall 都能独立产出候选集
- RRF 融合后结果优于单路召回
- `RagContext` 能返回结构化 `retrievedMaterials`

- [ ] **Step 2: 扩展 `RagContext`**

新增：
- `summary`
- `retrievedMaterials`
- `followUpCandidates`

`retrievedMaterials` 保持为出题阶段可直接消费的结构化结果，不要只回一大段拼接文本。

- [ ] **Step 3: 实现多路召回**

第一版策略固定为两路：
- dense recall：`queryText -> retrieval_text`
- sparse recall：`keywordQueries -> question_text + keywords`

如果当前底层 provider 无法直接提供 BM25：
- 优先在现有题卡集合上实现轻量 sparse recall fallback
- 不要求第一版必须绑定某个特定数据库原生 BM25 能力

结果要求：
- 每路独立返回候选题卡和排名
- 候选集按 `question_id` 去重
- 保留原始 rank，供后续融合使用

- [ ] **Step 4: 实现 RRF 融合**

先不比较不同路召回的原始分数，统一按排名做 RRF：
- dense rank 贡献一份倒数分
- sparse rank 贡献一份倒数分
- 未出现在某一路的候选不强行补零分解释

第一版不要自己发明复杂融合公式，优先先把 RRF 跑通。

- [ ] **Step 5: 实现精排**

精排顺序建议：
- 优先使用专用 reranker / semantic ranker
- 如果当前环境暂时没有可用 reranker，再退化到规则重排
- 第一版不要直接引入通用大模型做 rerank

精排时叠加这些因素：
- 命中 `mustHaveClues` 的结果加分
- 命中 `avoidClues` 的结果减分
- 技术钩子型项目题对 `keywords` 命中额外加分
- 难度越接近 `difficultyHint` 的结果额外加分，但不排除邻近难度结果
- 最终裁剪到 Top 3-5

- [ ] **Step 6: 组装结构化结果**

每条命中结果至少带：
- `questionId`
- `questionText`
- `intentConcept`
- `referenceContext`
- `scoringKeyPoints`
- `scoringPitfalls`
- `followUpIds`

`followUpCandidates` 第一版只做简单聚合：
- 从 top 命中题卡收集 `follow_up_ids`
- 去重
- 限制数量

- [ ] **Step 7: 运行检索服务测试**

Run: `mvn -q "-Dtest=RagRetrievalServiceImplTest" test`

Expected: PASS

- [ ] **Step 8: 接回评测测试的命中率指标**

让 `InterviewRagEvaluationTest` 开始统计：
- `retrievalApplicableHitRate`
- `mustHaveCoverage`
- `avoidPollutionRate`
- `denseRecallHitRate`
- `sparseRecallHitRate`
- `fusionLift`
- `rerankTop3HitRate`
- `followUpCandidatePrecision`（若暂时算不稳，至少打印报告，不先卡死）

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/dto/RagContext.java backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java backend/src/main/java/com/a05/aiinterview/rag/service/impl/NoopRagRetrievalService.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java
git commit -m "feat: add hybrid recall and rerank for interview question retrieval"
```

## Chunk 6：把真实检索结果接进出题链路

### Task 6：让出题模型优先消费真实命中题卡，而不是只看 brief

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionGenerationInput.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java`

- [ ] **Step 1: 先写失败的出题输入测试**

验证：
- 检索有命中时，`retrievalContext.summary` 不再是固定 fallback
- `retrievalContext.retrievedMaterials` 有内容
- `retrievalContext.followUpCandidates` 有内容时会透传
- `retrievalPlans` 仍保留，便于审计
- 不适用检索时，旧 fallback 仍生效

- [ ] **Step 2: 在 `QuestionStreamService` 注入并使用 `RagPlanCompiler`**

替换当前的空实现路径：
- 先编译请求
- `shouldRetrieve=false` 时直接返回空 `RagContext`
- `shouldRetrieve=true` 时调用真正的 `RagRetrievalService`

- [ ] **Step 3: 修改 `QuestionGenerationInput`**

保证输入结构可以承载：
- `retrievedMaterials`
- `followUpCandidates`
- 审计用的 `retrievalPlans`

不要再让 `retrievedMaterials` 永远是空数组。

- [ ] **Step 4: 运行出题输入测试**

Run: `mvn -q "-Dtest=QuestionStreamServiceBuildInputTest" test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionGenerationInput.java backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java
git commit -m "feat: feed retrieved interview cards into question generation"
```

## Chunk 7：回归真实日志并做整体验证

### Task 7：用真实日志样本做回归，防止改完后方向错了

**Files:**
- Modify: `backend/src/test/resources/rag-eval/interview-retrieval-cases.json`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`

- [ ] **Step 1: 扩充真实日志样本**

至少补齐这几类：
- 理论题高频经典问法
- 场景题工程取舍问法
- 行为题追问样本
- 项目题技术钩子样本
- 项目题非技术钩子负样本

- [ ] **Step 2: 回归你们前面提到的核心担忧**

专门验证：
- “只做题库检索”是否仍能支撑知识问法
- 项目题是否会被错误拉成八股题
- 行为题是否不会误召回技术知识
- `follow_up_ids` 是否只是增强项，没有反客为主

- [ ] **Step 3: 运行完整测试集**

Run: `mvn test`

Expected:
- 所有新增单测 PASS
- 评测测试 PASS 或仅剩明确记录的阈值豁免

- [ ] **Step 4: 手工抽查至少 6 条样本输出**

手工检查：
- 2 条理论题
- 1 条场景题
- 1 条行为题
- 2 条项目题

核对：
- 命中的题卡是否合理
- `followUpCandidates` 是否有明显脏数据
- 出题是否比“只有 retrievalPlans 提示”更稳定

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/resources/rag-eval/interview-retrieval-cases.json backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java
git commit -m "test: validate interview rag single-corpus retrieval flow"
```

## 实施提醒

- 这个方案的核心不是“把知识删掉”，而是“把知识压进题目卡片里”。
- `question_text` 不能单独承担检索职责；`retrieval_text` 必须包含 `intent_concept`、`reference_context`、`scoring_key_points`。
- `follow_up_ids` 是增强项，不是主检索入口；如果后续维护成本失控，应先减少覆盖范围，不要让它主导整个出题链路。
- 项目题支持纳入同一题库，但一定要坚持“技术钩子型项目题卡片”边界，避免把泛项目问题做成低价值模板库。
- 检索执行优先顺序固定为“过滤 -> 多路召回 -> RRF -> 专用精排 -> TopN 注入”，不要先上通用大模型重排。
- 如果底层当前无法直接提供 BM25，也不要因此放弃多路召回；第一版可以接受应用层 sparse recall fallback。
- 如果评测结果显示“项目题被拉成八股题”或“行为题误召回技术材料”，优先收窄路由规则，不要先加更多字段。

Plan complete and saved to `docs/superpowers/plans/2026-03-26-interview-rag-retrieval-reframe.md`. Ready to execute?
