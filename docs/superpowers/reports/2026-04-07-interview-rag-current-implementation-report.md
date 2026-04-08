# 面试 RAG 现行实现评审报告

## 1. 报告目的

这份报告只回答一个问题：**当前仓库里的面试 RAG 到底是怎么工作的。**

重点不是复述理想方案，而是把现行实现的真实边界讲清楚，避免后续讨论继续混入已经删除的旧链路描述。

本文配套的 Mermaid 图源仍在：

- [architecture-overview.mmd](D:/a05-cursor/docs/superpowers/reports/assets/2026-04-07-interview-rag-current-implementation/architecture-overview.mmd)
- [retrieval-sequence.mmd](D:/a05-cursor/docs/superpowers/reports/assets/2026-04-07-interview-rag-current-implementation/retrieval-sequence.mmd)
- [knowledge-dataflow.mmd](D:/a05-cursor/docs/superpowers/reports/assets/2026-04-07-interview-rag-current-implementation/knowledge-dataflow.mmd)

## 2. 结论摘要

当前实现已经是一条完整的面试出题知识支撑链路，而不是单点向量检索插件。现行主路径是：

`评估决策 -> RagPlanCompiler -> Qdrant dense/sparse 双路召回 -> RRF 融合 -> DashScope rerank -> 题型硬护栏 -> RagContext 注入 QuestionGenerationInput`

这条链路有 5 个必须明确的事实：

1. `queryText` 已被定义为上游唯一的自然语言语义视图。
2. `keywordHints` 只用于构造 sparse/BM25 词法输入，不再与 dense/rerank 混用。
3. `difficultyHint` 在开关开启时会被解析成相邻一级 difficulty window，并在 dense/sparse 两路召回前统一生效。
4. 现行检索已切到 Qdrant 原生 hybrid query，不再依赖旧的通用 `VectorStore` 检索路径。
5. 检索审计已经能记录 dense、sparse、fusion、rerank 与最终注入阶段的关键状态，并在异常时尽量保留已知事实。

## 3. 当前架构总览

现行架构可以概括为：

**结构化题卡知识库 + Qdrant hybrid recall + RRF + 商业 rerank + 程序硬护栏 + 结构化上下文注入。**

这里最重要的工程分层是：

- 决策层只负责判断是否需要检索，以及给出语义查询与词法锚点。
- 编译层把决策输出整理成 `RagRetrievalRequest`。
- 检索层只负责召回、融合、重排、护栏和审计。
- 出题主链路只消费 `RagContext` 的结构化结果，不直接介入检索细节。

这意味着 RAG 在当前系统里承担的是“受控知识注入层”，而不是“让模型自己找资料”的黑箱步骤。

## 4. 知识单元与入库设计

### 4.1 知识单元仍然是结构化题卡

当前知识库基础单元是 `KnowledgeDocument`，不是长文切片。每条题卡包含：

- 题目本身 `questionText`
- 考察意图 `intentConcept`
- 参考语境 `referenceContext`
- 关键得分点 `scoringKeyPoints`
- 典型误区 `scoringPitfalls`
- 追问候选 `followUpIds`
- 元数据 `domainCode`、`questionType`、`difficulty`、`keywords`、`source`、`version`、`active`

这样建模的直接好处是：命中的结果天然就是出题链路能消费的结构化材料，不需要再从自由文本里二次抽取。

### 4.2 一张题卡会被拆成三种表示

当前映射规则由 `KnowledgeDocument` 和 `QdrantHybridPointMapper` 决定：

- dense 表示只使用 `questionText + intentConcept`
- sparse 表示只使用 `questionText + scoringKeyPoints + keywords`
- payload 保留完整业务字段，供结果还原、护栏判断、上下文注入和审计使用

这意味着：

- 语义召回尽量保留高语义密度文本
- BM25 分支优先吃术语锚点和评分点
- 长叙事字段如 `referenceContext`、`scoringPitfalls` 不直接进入召回文本，而是在 rerank 文档和最终注入时使用

### 4.3 入库已经切到 Qdrant 原生 hybrid point

`KnowledgeIngestionService` 的现行路径是：

1. 用 embedding 模型生成 dense vector
2. 用 `qdrant/bm25` 文档模型生成 sparse document
3. 用 `QdrantHybridPointMapper` 组装 payload
4. 通过 `QdrantClient.upsertAsync(...)` 批量写入 collection

当前 collection schema 由 `QdrantHybridCollectionManager` 维护，核心约束是：

- collection 名称默认 `interview_knowledge_hybrid`
- dense named vector 默认 `dense`
- sparse named vector 默认 `bm25`
- payload index 当前只确保 `question_type` 和 `active`

`domain_code` 仍保留在 payload 中，但它现在是结果元数据，不是检索 filter 主条件。

## 5. 检索请求编译口径

### 5.1 检索不是总会触发

`RagPlanCompiler` 会先读取 `DecisionExecutionPlan` 中的 `retrievalPlans`，再决定是否生成可执行检索请求。

当前规则是：

- `PRINCIPLE`
- `SCENARIO`
- `BEHAVIORAL`
- `PROJECT_DEEP_DIVE`

这几类题型只有在存在 `retrievalPlans` 时才触发检索；否则直接返回 `shouldRetrieve=false` 的空请求。

### 5.2 语义视图和词法视图已经拆开

当前 `RagRetrievalRequest` 的关键字段语义如下：

- `queryText`：上游给出的独立、完整、自然语言语义查询
- `denseQueryText`：dense 执行层使用的文本；当前编译器直接把它设为 `queryText`
- `sparseQueryText`：由 `keywordHints` 去重后拼接得到的 BM25 输入
- `keywordQueries`：去重后的 sparse 术语锚点列表
- `difficultyHint`：开启配置时参与 difficulty window 硬过滤

现行编译结果已经不再把题型、难度说明、锚点标签拼进 rerank query。检索链路里真正被拆开的，是“语义查询”和“词法锚点”这两个视图。

## 6. 检索执行链路

### 6.1 双路召回

`RagRetrievalServiceImpl` 的主链路是：

1. `QdrantHybridQueryExecutor.denseRecall(request)`
2. `QdrantHybridQueryExecutor.sparseRecall(request)`
3. `RrfFusion.fuse(...)`
4. `ragRerankService.rerank(...)`
5. `applyHardGuardrails(...)`
6. 构造 `RagContext`

其中：

- dense 分支对 `denseQueryText` 做 embedding，然后查 named dense vector
- sparse 分支对 `sparseQueryText` 走 `qdrant/bm25`
- 如果 `sparseQueryText` 为空，sparse 分支直接返回空列表，不会额外伪造请求

### 6.2 dense 和 sparse 共用同一套基础过滤

当前 `QdrantHybridQueryExecutor.buildBaseFilter()` 只拼 3 类条件：

- `active=true`
- 标准化后的 `question_type`
- 可选的 difficulty window

这里有两个必须强调的事实：

1. `PROJECT_DEEP_DIVE` 会在语料过滤前标准化成 `PROJECT`
2. `domainCode` 不参与现行召回过滤

### 6.3 difficulty window 已经是统一的硬过滤

只要 `rag.difficulty-window-enabled=true` 且请求带有有效 `difficultyHint`，当前实现就会先用 `DifficultyWindowResolver` 解析出窗口，再把它同时加到 dense/sparse 两路查询的 filter 中。

当前映射规则是：

- `L1 -> [L1, L2]`
- `L2 -> [L1, L2, L3]`
- `L3 -> [L2, L3, L4]`
- `L4 -> [L3, L4, L5]`
- `L5 -> [L4, L5]`

这已经不是“给 rerank 的附加提示”，而是召回阶段的统一过滤条件。

## 7. 融合、重排与护栏

### 7.1 RRF 负责把双路证据并入同一排序空间

`RrfFusion` 当前接收 dense 与 sparse 的题卡 ID 序列，输出融合后的分数列表。`RagRetrievalServiceImpl` 再基于这个结果恢复候选池顺序。

这一步的价值是：

- dense/sparse 双命中的题卡会更稳定地进入前排
- 某一路弱、另一路强的题卡也不会被提前误杀
- 融合逻辑纯本地、可测且确定

### 7.2 rerank 当前只消费语义查询与题卡核心文本

`DashScopeRagRerankService` 当前会调用 `RagRerankInputBuilder` 构造 query 与 document：

- query 侧使用语义查询文本
- document 侧按“题目、考点、语境、关键点、误区”的顺序拼接候选内容

这意味着现行 rerank 不再把题型说明、难度说明或 BM25 锚点标签拼成一段 query brief 发送给外部排序器。

如果 DashScope rerank 失败，当前实现会记录 warning，并回退到 fusion 排序结果，不阻断主链路。

### 7.3 硬护栏负责最后的业务收口

`applyHardGuardrails(...)` 的当前逻辑很直接：

- 目标是行为题时，只保留 `BEHAVIORAL`
- 目标是项目题时，只保留 `PROJECT`
- 其他题型要求候选题型与目标题型一致

这一步的作用不是提升“相关度”，而是防止最终注入结果在业务上明显跑偏。

## 8. RagContext 输出与主链路集成

### 8.1 输出已经是结构化上下文

`RagContext` 当前会输出：

- `summary`
- `contextText`
- `retrievedMaterials`
- `followUpCandidates`
- `retrievalAudit`
- `hitCount`
- `empty`

其中 `retrievedMaterials` 仍保留完整题卡关键字段，出题链路不需要重新解析纯文本。

### 8.2 检索审计已经覆盖主要阶段

当前 `retrievalAudit` 的字段包括：

- `retrievalTriggered`
- `denseCandidateCount`
- `sparseCandidateCount`
- `difficultyWindowApplied`
- `difficultyWindowValues`
- `fusionTopQuestionIds`
- `rerankPreTopQuestionIds`
- `rerankPostTopQuestionIds`
- `injectedQuestionIds`

更关键的是，`RagRetrievalServiceImpl` 在异常路径中也会尽量保留已经累计到的审计状态，而不是统一抹成全空骨架。这让排障时可以区分“某阶段没跑到”和“某阶段跑过但后续失败”。

### 8.3 RAG 结果通过 `QuestionGenerationInput.RetrievalContext` 注入主链路

`QuestionStreamService.buildRetrievalContext(...)` 当前会把以下内容打包进出题输入：

- `retrievalPlans`
- `retrievedMaterials`
- `followUpCandidates`
- `retrievalAudit`
- `summary`

这意味着出题主链路拿到的不是“拼接好的一个大字符串”，而是一份可供 Prompt、日志和调试同时消费的结构化上下文。

## 9. 工程与运维观察

### 9.1 降级路径清晰

- `rag.enabled=false` 时，系统旁路到空 `RagContext`
- 检索异常时，系统也返回空结果，但不阻断出题
- rerank 失败时只回退排序，不直接中断检索

### 9.2 当前关键配置项已经集中在 `rag.*`

现行高相关配置包括：

- `rag.collection-name`
- `rag.dense-vector-name`
- `rag.sparse-vector-name`
- `rag.dense-top-k`
- `rag.sparse-top-k`
- `rag.fusion-top-k`
- `rag.top-k`
- `rag.min-score`
- `rag.difficulty-window-enabled`
- `rag.rerank.*`

这使得召回规模、融合规模、最终注入规模和 difficulty filter 开关都能通过配置控制。

### 9.3 测试保护已经从“结果大概对”转向“契约明确”

当前高相关测试主要锁这些边界：

- retrieval plan 的字段语义
- prompt 是否明确要求自然语言 query 与 keyword hints 分离
- difficulty window 的解析与过滤
- rerank 输入构造
- audit 字段输出
- `QuestionStreamService` 对检索上下文的组装

这类测试更适合保护接口边界和实现口径，不再依赖模糊的主观质量判断。

## 10. 当前边界

现行实现已经稳定，但边界也必须说清楚：

1. `domainCode` 仍在 payload 与结构化结果中保留，但不是当前检索 filter 的组成部分。
2. sparse 分支是否充分利用现有词法锚点，后续仍可继续做消融评估；但当前职责边界已经明确，不能再把 dense/rerank 与词法标签混写。
3. 当前文档只描述仓库内主链路，不把历史计划、旧实验路径或废弃字段当成现行实现的一部分。

## 11. 结论

当前面试 RAG 的核心价值，不是“用了 hybrid 检索”，而是把**检索视图拆分、统一过滤、融合重排、业务护栏和结构化审计**做成了一条真实可运行的中间层。

这条链路已经具备三个工程特征：

- 可以观察：审计字段足以回放主要阶段
- 可以降级：检索和 rerank 失败都不会直接打断出题
- 可以继续演进：语义查询、词法锚点、难度过滤和题型护栏的职责边界已经明显清晰于旧实现
