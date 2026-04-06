# evaluation_decision 到 question generation 链路解剖

## 文档目的

本文说明从 `evaluation_decision` 到 `question generation` 的真实程序链路，重点回答：

- AI 当前被要求输出哪些 retrieval 字段
- 后端如何清洗、校验、修复这些字段
- 哪些字段会被原样透传，哪些字段会被程序派生
- `RagPlanCompiler`、`RagRetrievalService`、`QuestionGenerationInput` 分别怎么消费它们

本文描述的是**当前代码真实实现**，不是旧方案，也不是未来理想方案。

## 总链路概览

当前真实链路是：

1. `evaluation_decision` Prompt 要求 AI 输出结构化 JSON
2. `OpenAiClient` 调模型并解析 DTO
3. `AiOutputContractValidator` 做第一层契约清洗
4. `DecisionExecutionPlanBuilder` 做第二层执行语义校验
5. 如校验失败，走 `DecisionRepairOrchestrator`；再失败则走系统兜底
6. 生效后的 `effectiveDecisionPlan` 写入 `attempt.evaluationJson`
7. `QuestionStreamService` 读取 `effectiveDecisionPlan`
8. `RagPlanCompiler` 编译检索请求
9. `RagRetrievalServiceImpl` 执行检索并返回 `RagContext`
10. `QuestionGenerationInput` 组装完成后进入 question generation Prompt

关键结论：

- question generation 不直接信任 AI 原始 JSON
- 后续链路只信任 `effectiveDecisionPlan`

## 第 1 层：Prompt 与 DTO 契约

相关文件：

- [evaluation-decision.md](D:\a05-cursor\backend\src\main\resources\prompts\evaluation-decision.md)
- [EvaluationDecisionOutput.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\dto\EvaluationDecisionOutput.java)

当前 Prompt 对 retrieval brief 的要求已经收缩为 3 字段：

- `queryText`
- `keywordHints`
- `difficultyHint`

`EvaluationDecisionOutput.RetrievalPlan` 也只保留这 3 个字段。

已经物理删除，不再属于当前契约的字段：

- `goal`
- `displayQuery`
- `mustHaveClues`
- `avoidClues`

这意味着：

- AI 已经不能再输出旧字段
- 后端 DTO 也不会再接收旧字段

## 第 2 层：`AiOutputContractValidator` 的第一层清洗

相关文件：

- [AiOutputContractValidator.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\contract\AiOutputContractValidator.java)

这一层不负责判断策略是否合理，只负责基础契约清洗。

### retrievalPlans 的清洗规则

对每条 retrieval plan，只做这三件事：

- `queryText -> defaultString(..., "")`
- `keywordHints -> sanitizeStringList(...)`
- `difficultyHint -> defaultString(..., "")`

这里的效果是：

- `null` 会被清成空字符串或空数组
- `keywordHints` 会去空白、去重复
- 不会新增任何程序猜测字段

这层不会做的事：

- 不会把 `nextFocus` 补成 `queryText`
- 不会生成 `displayQuery`
- 不会生成负向 clue

## 第 3 层：`DecisionExecutionPlanBuilder` 的第二层校验

相关文件：

- [DecisionExecutionPlanBuilder.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\DecisionExecutionPlanBuilder.java)

这一层负责判断：**这份 AI 决策是否可执行**。

### `retrievalPlans` 的当前规则

`CONTINUE` 分支下，当前已经新增了 3 条 retrieval 语义校验：

1. `retrievalPlans` 只能有 `0` 或 `1` 条  
   错误码：`RETRIEVAL_PLAN_COUNT_INVALID`

2. 只要存在 retrieval plan，`queryText` 必须非空  
   错误码：`RETRIEVAL_QUERY_TEXT_REQUIRED`

3. `difficultyHint` 只能是：
   - `""`
   - `L1`
   - `L2`
   - `L3`
   - `L4`
   - `L5`  
   错误码：`RETRIEVAL_DIFFICULTY_HINT_INVALID`

### 这一层不会做的事

- 不会把多条 retrieval plan 合并
- 不会自动补全缺失的 `queryText`
- 不会把 `difficultyHint` 改写成合法值

也就是说，builder 现在是**硬校验**，不是兜底编译器。

## 第 4 层：repair 与系统兜底

相关文件：

- [DecisionRepairOrchestrator.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\DecisionRepairOrchestrator.java)
- [AnswerSubmitService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\AnswerSubmitService.java)

如果 builder 校验失败：

1. 先进入 repair
2. repair 仍失败，进入系统兜底

这意味着：

- question generation 后续看到的 retrieval plan，可能来自 repair 后的结果
- 也可能完全来自 system fallback
- 不应把原始 AI 输出等同于最终生效计划

## 第 5 层：`effectiveDecisionPlan` 落库

`AnswerSubmitService` 会把生效后的执行计划写入：

- `attempt.evaluationJson.effectiveDecisionPlan`

这里是后续出题链路唯一可信的决策来源。

同时还会保留：

- `rawAiOutput`
- `decisionValidation`
- `repairAttempts`
- `repairOutput`

这些字段是审计信息，不是执行依据。

## 第 6 层：`QuestionStreamService` 重新读取生效计划

相关文件：

- [QuestionStreamService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\service\QuestionStreamService.java)

`QuestionStreamService.extractNextQuestionPlan()` 会从：

- `attempt.evaluationJson.effectiveDecisionPlan`

重新提取：

- `interviewAction`
- `effectiveDecisionSource`
- `finalDecision`
- `targetQuestionType`
- `nextFocus`
- `nextItemType`
- `nextItemName`
- `nextProjectPoint`
- `targetDomainCode`
- `targetDomainName`
- `decisionReason`
- `retrievalPlans`

所以：

- question generation 看到的是落库后的生效计划
- 不是 Prompt 原始字符串
- 也不是 DTO 解析前的模型原始输出

## 第 7 层：`RagPlanCompiler`

相关文件：

- [RagPlanCompiler.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java)
- [RagRetrievalRequest.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java)

### 当前 `shouldRetrieve` 规则

当前编译器已经删除项目题技术钩子整套逻辑。

现在的规则很简单：

- 如果题型属于 `PRINCIPLE / SCENARIO / BEHAVIORAL / PROJECT_DEEP_DIVE`
- 且存在 retrieval plan
- 则 `shouldRetrieve = true`

否则：

- `shouldRetrieve = false`

已经删除：

- `TECH_HOOK_TOKENS`
- `hasExplicitProjectTechHook(...)`
- `containsTechnicalHint(...)`

### 当前编译输出

编译后的 `RagRetrievalRequest` 当前只保留这些核心字段：

- `shouldRetrieve`
- `queryText`
- `keywordQueries`
- `difficultyHint`
- `domainCode`
- `questionType`
- `focusPoint`
- `positionCode`
- `experienceLevel`
- `projectName`

编译规则也已收缩为：

- `queryText <- retrievalPlan.queryText`
- `keywordQueries <- retrievalPlan.keywordHints` 清洗去重后得到
- `difficultyHint <- retrievalPlan.difficultyHint`
- `focusPoint <- effectiveDecisionPlan.nextFocus`

编译器当前不会再做这些事：

- 不会生成 `displayQuery`
- 不会生成 `mustHaveClues / avoidClues`
- 不会生成 `preferredDifficultyLevels`
- 不会根据 `nextFocus` 反向补 `queryText`

## 第 8 层：`RagRetrievalServiceImpl`

相关文件：

- [RagRetrievalServiceImpl.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRetrievalServiceImpl.java)

### 当前真实链路

当前实现仍然是：

1. lexical 预过滤
2. dense 召回
3. rerank
4. 硬护栏
5. 组装 `RagContext`

### 当前字段消费方式

#### dense 查询

`resolveDenseQueryText(...)` 当前规则是：

1. 先用 `request.queryText`
2. `queryText` 为空时，才回退到 `request.focusPoint`

不会再拼接：

- `keywordQueries`
- 任何旧字段

#### lexical 预过滤

`resolveLexicalTerms(...)` 当前只消费：

- `request.keywordQueries`

如果 `keywordQueries=[]`：

- lexical 预过滤直接跳过
- 不会回退到 `focusPoint`
- 不会偷偷生成关键词

这条规则已经有测试保护。

### 当前仍未改变的地方

- 当前还是 lexical 先收缩候选，再做 dense 召回
- 还没有实现 dense + sparse 双路独立召回

## 第 9 层：`DashScopeRagRerankService`

相关文件：

- [DashScopeRagRerankService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\DashScopeRagRerankService.java)

当前 rerank brief 已经收缩为：

- `题型`
- `目标(queryText)`
- `焦点(focusPoint)`
- `关键词(keywordQueries)`
- `目标难度(difficultyHint)`

已经删除：

- `displayQuery`
- `mustHaveClues`
- `avoidClues`

## 第 10 层：`QuestionGenerationInput`

相关文件：

- [QuestionGenerationInput.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\dto\QuestionGenerationInput.java)
- [QuestionStreamService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\service\QuestionStreamService.java)

`QuestionStreamService.buildRetrievalContext()` 会同时放入两类信息：

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

其中 `summary` 在没有命中时会退回系统默认文案。

## 字段生命周期总结

### AI 原样输出并可能保留到后面的字段

- `queryText`
- `keywordHints`
- `difficultyHint`

前提是：

- 通过 validator
- 通过 builder
- repair / fallback 没有替换掉它们

### 会被清洗的字段

- `queryText`：`null -> ""`，并 `trim()`
- `keywordHints`：去空、去重、`null -> []`
- `difficultyHint`：`null -> ""`

### 会被程序补齐的字段

这些不是 AI brief 的一部分，而是程序上下文派生字段：

- `questionType`
- `domainCode`
- `focusPoint`
- `positionCode`
- `experienceLevel`
- `projectName`

### 已经彻底删除的 retrieval 旧字段

- `goal`
- `displayQuery`
- `mustHaveClues`
- `avoidClues`
- `preferredDifficultyLevels`
- 项目题技术钩子相关词表和方法

## 结论

当前链路已经完成两件关键收缩：

1. retrieval brief 从 7 字段收缩为 3 字段
2. 检索执行层不再消费旧字段，也不再依赖项目题技术钩子

因此现在判断一个 retrieval 字段是否还有效，只需要看两点：

- 它是否仍在 `EvaluationDecisionOutput.RetrievalPlan` 中存在
- 它是否仍被 `RagPlanCompiler / RagRetrievalServiceImpl / DashScopeRagRerankService` 真实消费

按当前代码，答案已经很清楚：

- 真正有效的 retrieval brief 只有 `queryText / keywordHints / difficultyHint`
- 其他旧字段已经退出现行链路
