# 面试 RAG 中 domainCode 降级为纯元数据设计

## 文档目的

本文定义当前面试 RAG 实现中 `domainCode` 的新边界：

- 保留为题卡和结果的展示元数据
- 不再参与召回、融合、rerank、硬护栏
- 不再承担项目题锚点职责

这不是一次“删几行判断”的局部修补，而是一次边界重划分。目标是把 `domainCode` 从 RAG 执行语义中彻底移出，避免它继续以隐式过滤条件或软规则的形式污染检索链路。

## 当前问题

当前代码中，`domainCode` 的角色已经混杂：

1. 它是题卡 payload 的一部分，用于返回结果和展示。
2. 它仍被编译进 `RagRetrievalRequest`，看起来像检索输入的一部分。
3. 它已退出 Qdrant 召回 filter，但仍被后置硬护栏使用。
4. 它被误当成“项目锚点”的候选字段，但代码中并没有真正独立的项目身份字段。

这会带来两个问题：

- 架构语义不清：调用方无法判断 `domainCode` 到底是元数据还是检索规则。
- 技术债继续积累：即使这次从召回 filter 移除，未来也很容易在 rerank、护栏或日志中再次被恢复为“半个约束”。

## 目标与非目标

### 目标

本次设计确认以下目标：

1. `domainCode` 在 RAG 中降级为纯元数据。
2. `domainCode` 不再进入 `RagRetrievalRequest`。
3. `domainCode` 不参与：
   - dense/sparse recall
   - RRF 融合
   - 商业 rerank
   - 后置硬护栏
   - 项目锚点判断
4. `domainCode` 继续保留在：
   - `KnowledgeDocument`
   - Qdrant payload
   - `RagContext.RetrievedMaterial`
   - 入库校验、审计、展示

### 非目标

本次设计不做这些事：

- 不同时引入新的项目锚点字段
- 不把 `projectName` 伪装成当前已生效的锚点机制
- 不改动题卡 JSONL 的 `domainCode` 元数据输入格式
- 不删除 `domainCode` 在其他非 RAG 业务链路中的使用

## 设计原则

### 1. 元数据与执行语义分离

一个字段如果是纯元数据，就不能再被 RAG 拿来：

- 过滤候选
- 提高排序分数
- 拦截最终结果

否则它就不是纯元数据，而是检索特征。

### 2. 项目锚点不能继续偷用 domainCode

如果未来项目题确实需要稳定锚点，应该新增显式字段，例如：

- `projectKey`
- `itemKey`
- `relatedItemKey`

而不是继续复用 `domainCode`。`domainCode` 的含义是知识域，不是项目身份。

### 3. rerank 继续保留纯语义职责

rerank 的职责是判断“哪个候选最贴近当前出题意图”，不是元数据对齐器。

因此本次设计明确：

- 继续保留 rerank
- 但不允许 `domainCode` 进入 rerank 输入或后置加权

## 新边界定义

### 1. 请求层：RagRetrievalRequest

`RagRetrievalRequest` 只保留真正影响检索执行的字段：

- `shouldRetrieve`
- `queryText`
- `denseQueryText`
- `sparseQueryText`
- `keywordQueries`
- `questionType`
- `difficultyHint`
- `focusPoint`
- `positionCode`
- `experienceLevel`
- `projectName`

`domainCode` 从该 DTO 中删除。

### 2. 编译层：RagPlanCompiler

`RagPlanCompiler` 不再把 `DecisionExecutionPlan.targetDomainCode` 编进 RAG 请求。

保留现有 query 编译职责：

- `denseQueryText <- queryText`
- `sparseQueryText <- keywordHints join`

`targetDomainCode` 仍可继续存在于上游决策语义中，但不下推进 RAG 请求。

### 3. 执行层：RagRetrievalServiceImpl

硬护栏改为只按题型约束：

- `BEHAVIORAL` 只能保留行为题
- `PROJECT` 只能保留项目题
- 其他题型要求题型一致

不再出现：

- 行为题 `domainCode` 必须为空
- 项目题 `domainCode` 必须相同
- 其他题型 `domainCode` 一致性约束

同时，日志中不再把请求侧 `domainCode` 作为检索输入打印。

### 4. 存储层：Qdrant payload

`domain_code` payload 继续保留，因为它仍有展示和审计价值。

但既然不再参与检索语义，就不再为它创建 payload index。

保留的 schema/index：

- `question_type`
- `active`

移除的 schema/index：

- `domain_code`

### 5. 返回层：RagContext

`RagContext.RetrievedMaterial.domainCode` 继续保留。

这意味着：

- 前端/日志仍可展示题卡所属领域
- 调试和人工检查仍然可见
- 但该字段不再对最终排序和筛选产生任何影响

### 6. 数据治理：KnowledgeJsonlImportService

`domainCode` 校验建议继续保留，但需要重新定义它的地位：

- 它是数据质量校验
- 不是检索规则校验

这样做的原因是：

- 行为题 `domainCode` 为空、INTRO 使用 `intro` 等规则，仍然有助于数据一致性
- 但这些规则不再被 RAG 解释成执行约束

## 对现有文档口径的影响

本次设计落地后，所有文档都必须统一成以下表述：

- `domainCode` 是题卡和结果的元数据
- 当前 RAG 仅按 `questionType` 与 `active` 做低误伤约束
- 项目题当前没有显式项目身份锚点
- rerank 仅基于查询文本与题卡语义内容

尤其需要同步：

- 当前实现报告
- cutover runbook
- 任何把 `domainCode` 写成护栏或 rerank 依据的设计说明

## 主要风险

### 1. 项目题失去显式结构化锚点

这是本次设计明确接受的结果。

删掉 `domainCode` 后，项目题只剩：

- 查询文本
- 题型约束
- rerank 语义判断

如果后续观察到项目题排序不稳定，正确补法是新增 `projectKey/itemKey`，而不是把 `domainCode` 捡回来。

### 2. 旧注释和旧测试继续传播历史语义

如果只改主代码，不改测试和文档，团队后续仍会认为：

- `domainCode` 是“理论上应该参与检索”的字段
- 只是“暂时没打开”

这是错误的。它必须被明确写死为纯元数据。

## 验收标准

设计对应的实现完成后，至少应满足：

1. `RagRetrievalRequest` 中不再存在 `domainCode`
2. `RagPlanCompiler` 不再向 RAG 请求写入 `domainCode`
3. `RagRetrievalServiceImpl` 不再读取请求侧 `domainCode`
4. 硬护栏不再检查 `domainCode`
5. `QdrantHybridCollectionManager` 不再创建 `domain_code` payload index
6. `QdrantHybridQueryExecutor` 仍可从 payload 读取 `domain_code` 并返回
7. `RagContext.RetrievedMaterial` 仍然包含 `domainCode`
8. 文档与测试不再把 `domainCode` 描述为召回、rerank 或护栏依据

## 结论

本次设计的核心不是“放松一个过滤条件”，而是重新划清边界：

- `domainCode` 继续存在
- 但只作为语料和结果的展示元数据存在

一旦这个边界落地，RAG 主链路将只由以下信息驱动：

- 查询文本
- 关键词文本
- 题型
- 候选题卡正文语义

这使得当前实现更一致，也为未来单独引入真正的项目锚点字段留下了清晰空间。
