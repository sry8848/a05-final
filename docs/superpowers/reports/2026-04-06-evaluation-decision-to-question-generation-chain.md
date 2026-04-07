# evaluation_decision 到 question generation 链路解剖

## 文档目的

本文说明从 `evaluation_decision` 到 `question generation` 的当前真实程序链路，重点回答：

- AI 当前输出哪些 retrieval 字段
- 后端如何清洗、校验、修复这些字段
- 哪些字段是现行执行字段，哪些只是实现细节
- `RagPlanCompiler`、`RagRetrievalServiceImpl`、`DashScopeRagRerankService`、`QuestionGenerationInput` 分别怎么消费它们

本文描述的是**当前代码真实实现**。

## 总链路概览

当前真实链路是：

1. `evaluation_decision` Prompt 要求 AI 输出结构化 JSON
2. `OpenAiClient` 调模型并解析 DTO
3. `AiOutputContractValidator` 做第一层契约清洗
4. `DecisionExecutionPlanBuilder` 做第二层执行语义校验
5. 如校验失败，走 `DecisionRepairOrchestrator`；再失败则走系统兜底
6. 生效后的 `effectiveDecisionPlan` 写入 `attempt.evaluationJson`
7. `QuestionStreamService` 重新读取 `effectiveDecisionPlan`
8. `RagPlanCompiler` 编译 `RagRetrievalRequest`
9. `RagRetrievalServiceImpl` 执行 hybrid 检索并返回 `RagContext`
10. `QuestionGenerationInput` 组装完成后进入 question generation Prompt

关键结论：

- question generation 不直接信任 AI 原始 JSON
- 后续链路只信任 `effectiveDecisionPlan`

## 第 1 层：Prompt 与 DTO 契约

相关文件：

- [evaluation-decision.md](D:\a05-cursor\backend\src\main\resources\prompts\evaluation-decision.md)
- [EvaluationDecisionOutput.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\dto\EvaluationDecisionOutput.java)

当前 Prompt 与 DTO 对 retrieval brief 都只保留 3 个字段：

- `queryText`
- `keywordHints`
- `difficultyHint`

已经不在现行契约中的旧字段，不再接收也不再透传。

## 第 2 层：`AiOutputContractValidator`

相关文件：

- [AiOutputContractValidator.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\contract\AiOutputContractValidator.java)

这一层只负责基础清洗：

- `queryText -> defaultString(..., "")`
- `keywordHints -> sanitizeStringList(...)`
- `difficultyHint -> defaultString(..., "")`

这一层不会：

- 自动补全缺失的检索意图
- 生成额外的检索字段
- 恢复旧 retrieval 字段

## 第 3 层：`DecisionExecutionPlanBuilder`

相关文件：

- [DecisionExecutionPlanBuilder.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\DecisionExecutionPlanBuilder.java)

这一层负责判断决策是否可执行。

当前与 retrieval plan 直接相关的规则是：

1. `retrievalPlans` 只能是 `0` 或 `1` 条
2. 只要存在 retrieval plan，`queryText` 必须非空
3. `difficultyHint` 只能是 `"" / L1 / L2 / L3 / L4 / L5`

也就是说，builder 当前是硬校验层，不负责兜底编译。

## 第 4 层：repair 与系统兜底

相关文件：

- [DecisionRepairOrchestrator.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\DecisionRepairOrchestrator.java)
- [AnswerSubmitService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\AnswerSubmitService.java)

如果 builder 校验失败：

1. 先进入 repair
2. repair 仍失败，再进入系统兜底

因此后续链路看到的是生效计划，不是模型原始输出。

## 第 5 层：`effectiveDecisionPlan` 落库

`AnswerSubmitService` 会把生效后的执行计划写入：

- `attempt.evaluationJson.effectiveDecisionPlan`

这里是后续出题链路唯一可信的 retrieval 决策来源。

## 第 6 层：`QuestionStreamService`

相关文件：

- [QuestionStreamService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\service\QuestionStreamService.java)

`QuestionStreamService.extractNextQuestionPlan()` 会从生效计划中重新提取：

- `targetQuestionType`
- `nextFocus`
- `nextItemName`
- `targetDomainCode`
- `decisionReason`
- `retrievalPlans`

所以 question generation 后续消费的是落库后的执行语义。

## 第 7 层：`RagPlanCompiler`

相关文件：

- [RagPlanCompiler.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java)
- [RagRetrievalRequest.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java)

### 当前 `shouldRetrieve` 规则

当前规则很简单：

- 题型属于 `PRINCIPLE / SCENARIO / BEHAVIORAL / PROJECT_DEEP_DIVE`
- 且存在 retrieval plan
- 则 `shouldRetrieve = true`

否则：

- `shouldRetrieve = false`

### 当前编译输出

当前编译后的 `RagRetrievalRequest` 包含这些核心字段：

- `shouldRetrieve`
- `queryText`
- `denseQueryText`
- `sparseQueryText`
- `keywordQueries`
- `difficultyHint`
- `domainCode`
- `questionType`
- `focusPoint`
- `positionCode`
- `experienceLevel`
- `projectName`

当前编译规则是：

- `queryText <- retrievalPlan.queryText`
- `denseQueryText <- queryText`
- `sparseQueryText <- keywordHints` 去空去重后按空格拼接
- `keywordQueries <- keywordHints` 去空去重后得到
- `difficultyHint <- retrievalPlan.difficultyHint`
- `focusPoint <- effectiveDecisionPlan.nextFocus`

这里要特别区分两件事：

- `domainCode`、`keywordQueries` 仍然存在于运行时请求里
- 但当前代码回归契约只硬性绑定 `shouldRetrieve / questionType / queryText / denseQueryText / sparseQueryText`

## 第 8 层：`RagRetrievalServiceImpl`

相关文件：

- [RagRetrievalServiceImpl.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRetrievalServiceImpl.java)
- [QdrantHybridQueryExecutor.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridQueryExecutor.java)

### 当前真实链路

当前实现已经是：

1. `shouldRetrieve=false` 时直接短路返回
2. dense 分支查询
3. sparse/BM25 分支查询
4. `RrfFusion` 融合
5. 商业 rerank
6. 最终硬护栏
7. 组装 `RagContext`

### 当前字段消费方式

#### dense 分支

dense 分支当前只消费：

- `denseQueryText`

#### sparse 分支

sparse 分支当前只消费：

- `sparseQueryText`

如果 `sparseQueryText` 为空：

- sparse 分支直接跳过

#### 查询前过滤

当前查询执行前只下推这些元数据边界：

- `active=true`
- `question_type`
- `domain_code` 可选精确过滤

### 当前审计字段

当前 `RagContext.RetrievalAudit` 已统一为：

- `retrievalTriggered`
- `denseCandidateCount`
- `sparseCandidateCount`
- `fusionTopQuestionIds`
- `rerankPreTopQuestionIds`
- `rerankPostTopQuestionIds`
- `injectedQuestionIds`

## 第 9 层：`DashScopeRagRerankService`

相关文件：

- [DashScopeRagRerankService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\DashScopeRagRerankService.java)

当前 rerank brief 会带上这些信息：

- `题型`
- `语义查询`
- `术语查询`
- `焦点`
- `关键词`
- `目标难度`

也就是说，rerank 当前会同时看到 dense 查询文本、sparse 查询文本以及实现细节字段 `keywordQueries`。

## 第 10 层：`QuestionGenerationInput`

相关文件：

- [QuestionGenerationInput.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\dto\QuestionGenerationInput.java)
- [QuestionStreamService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\service\QuestionStreamService.java)

`QuestionStreamService.buildRetrievalContext()` 当前会同时放入：

1. `retrievalPlans`
- 来自生效计划
- 作用是弱提示

2. `retrievedMaterials`
- 来自真实检索结果
- 作用是主要 grounding 材料

还有：

- `followUpCandidates`
- `retrievalAudit`
- `summary`

## 当前回归边界

当前代码库已经删除旧的主观评测夹具。当前显式保留的回归边界是：

- [RagPlanCompilerTest.java](D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java)
  - 只校验 `shouldRetrieve / questionType / queryText / denseQueryText / sparseQueryText`
- [RagRetrievalServiceImplTest.java](D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java)
  - 校验 `shouldRetrieve=false` 短路、双路召回、融合、rerank 与硬护栏

更专业的 RAG 质量评估需要后续单独建设，不再混在现行代码正确性测试里。

## 结论

当前链路已经完成两件关键收敛：

1. retrieval brief 继续保持 3 字段
2. 检索执行层已经切换为 dense + sparse/BM25 + RRF + rerank + 硬护栏

因此现在判断一个 retrieval 字段或链路环节是否仍然有效，只需要看两点：

- 它是否仍被 `RagPlanCompiler / RagRetrievalServiceImpl / DashScopeRagRerankService` 真实消费
- 它是否仍在现行契约测试中被明确保护
