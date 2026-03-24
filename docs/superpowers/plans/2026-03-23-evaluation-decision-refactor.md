# 面试决策中枢重构实施计划

> **供 Agent 执行时使用：** 必须使用 `superpowers:subagent-driven-development`（如果当前环境支持子代理）或 `superpowers:executing-plans` 来执行本计划。所有步骤统一使用复选框语法（`- [ ]`）跟踪进度。

**目标：** 将“面试决策中枢”重构为“后端动态拼装策略 + AI 输出强类型策略编码”的模式，彻底切断 AI 对题型路由、额度计算和入口动作的控制。

**架构：** 后端维护唯一策略目录、额度状态、知识域菜单和执行路由；`evaluation_decision` Prompt 只消费后端拼装后的输入，AI 只负责输出 `StrategyCode`、`interviewAction`、`nextFocus`、`targetDomainCode`、`newCovered*` 与 `retrievalPlans`。提交回答后，后端先校验 AI 输出；若出现不可能组合，则带着结构化错误信息要求 AI repair，最多重试 2 次。repair 成功后再将 `StrategyCode` 转换成内部 `DecisionExecutionPlan`；repair 连续失败则按系统异常结束，但必须写明错误来源与审计信息。后续 quota 推进、状态沉淀、SSE 出题链路只消费该执行计划。

**技术栈：** Spring Boot、MyBatis-Plus、Lombok、Jackson、JUnit 5、AssertJ

---

## Chunk 1：新契约收口

### 任务 1：将评估决策输入 DTO 改造成后端主导的新契约

**涉及文件：**
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionInput.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
- 测试：`backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java`

- [ ] **步骤 1：将输入 DTO 收缩到新 Prompt 契约**

更新 `EvaluationDecisionInput`：
- 保留：
  - `interviewId`
  - `currentQuestionId`
  - `interview`
  - `projectAndInternshipSummary`
  - `coveredKnowledgeSummary`
  - `currentQuestion`
  - `answerText`
  - `expectedPoints`
  - `retrievedMaterials`
  - `recentInterviewMemory`
- 删除：
  - `interviewGoalSummary`
  - `quotaSummary`
  - `possibleFutureDirections`
- 新增：
  - `remainingTargetDomains`
  - `availableStrategies`

新增嵌套类型：
- `RemainingTargetDomainItem`
  - `domainCode`
  - `domainName`
  - `focusPoints`
- `AvailableStrategyItem`
  - `strategyCode`
  - `label`
  - `description`
  - `applicableWhen`
  - `moveType`
  - `requiresTargetDomain`

同时补齐 `CurrentQuestionContext`：
- 新增 `domainCode`
- 明确 `PRINCIPLE` 题在输入侧必须带 `domainCode/domainName`

- [ ] **步骤 2：更新 Prompt 变量拼装逻辑**

修改 `OpenAiClient.buildEvaluationDecisionVariables(...)`：
- 不再传入：
  - `interviewGoalSummary`
  - `quotaSummary`
  - `possibleFutureDirections`
- 改为传入：
  - `remainingTargetDomains`
  - `availableStrategies`
- 这两个字段要渲染成 Prompt 友好的自然语言块，而不是直接依赖 DTO 的原始字符串表现

- [ ] **步骤 3：更新 Prompt 覆盖测试，匹配新输入结构**

在 `PromptTemplateCoverageTest` 中：
- 不再传入已删除字段
- 断言渲染后的 Prompt 包含：
  - `主考纲剩余待考察域（菜单）`
  - `当前可用策略池`
  - 来自 `availableStrategies` 的策略示例
- 断言渲染后的 Prompt 不再包含：
  - `quotaSummary`
  - `possibleFutureDirections`
  - `interviewGoalSummary`

- [ ] **步骤 4：运行聚焦 Prompt 测试**

运行：
`mvn -q "-Dtest=PromptTemplateCoverageTest" test`

- [ ] **步骤 5：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionInput.java backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java
git commit -m "refactor: align evaluation decision input with prompt v2"
```

### 任务 2：将评估决策输出 DTO 收缩为基于策略编码的新 Schema

**涉及文件：**
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/contract/AiOutputContractValidator.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/impl/MockAiClient.java`
- 测试：`backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java`

- [ ] **步骤 1：从 DTO 中删除旧输出字段**

更新 `EvaluationDecisionOutput`：
- 保留：
  - `decisionReason`
  - `interviewAction`
  - `finalDecision`
  - `nextFocus`
  - `targetDomainCode`
  - `newCoveredDomains`
  - `newCoveredPoints`
  - `retrievalPlans`
- 删除：
  - `answerSummary`
  - `answerAssessment`
  - `candidateStrategies`
  - `nextEntryAction`
  - `nextQuestionType`
  - `expectedAnswerPoints`

更新 `CoveredDomain`：
- 用 `domainCode` 替换 `domainId`
- 保留 `domainName`

- [ ] **步骤 2：将校验器重写为新 Schema**

在 `AiOutputContractValidator.validateEvaluationDecision(...)` 中：
- 校验 `interviewAction`
- 校验 `finalDecision` 必须属于本轮注入的策略集合，或属于静态策略目录
- 校验 `targetDomainCode`：
  - 对 `S_SWITCH_DOMAIN`、`S_ENTER_PRINCIPLE`：必须显式填写
  - 对同域理论推进策略（如 `S_P_VERIFY`、`S_P_DEEP_LINK`、`S_P_VARIANT`、`S_P_SAME_DOMAIN_SHIFT`）：允许 AI 输出空字符串，后端随后继承 `currentQuestion.domainCode/domainName`
  - 其他策略必须是 `""`
- 校验 `newCoveredDomains` 必须使用 `domainCode`，不能再使用 `domainId`
- 校验 `retrievalPlans` 结构
- 删除所有与以下旧字段有关的校验分支：
  - `candidateStrategies`
  - `nextEntryAction`
  - `nextQuestionType`
  - `expectedAnswerPoints`

校验结果不要直接以异常结束主流程，而要返回结构化错误码，供后续 repair 流程消费。

- [ ] **步骤 3：更新 Mock 输出与契约测试**

调整 `MockAiClient` 使其输出新 JSON 结构。

重写 `EvaluationDecisionContractTest`，覆盖：
- 合法 `CONTINUE`
- 合法 `WRAPUP`
- 非法策略编码
- 应填 `targetDomainCode` 却缺失
- 不应填 `targetDomainCode` 却填写
- `newCoveredDomains` 使用 `domainCode`

- [ ] **步骤 4：运行聚焦契约测试**

运行：
`mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest" test`

- [ ] **步骤 5：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java backend/src/main/java/com/a05/aiinterview/ai/contract/AiOutputContractValidator.java backend/src/main/java/com/a05/aiinterview/ai/impl/MockAiClient.java backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java
git commit -m "refactor: slim evaluation decision output contract"
```

## Chunk 2：策略目录与后端注入

### 任务 3：用规范化策略目录替换旧动作目录

**涉及文件：**
- 新建：`backend/src/main/java/com/a05/aiinterview/ai/contract/StrategyCode.java`
- 新建：`backend/src/main/java/com/a05/aiinterview/ai/contract/StrategyDefinition.java`
- 新建：`backend/src/main/java/com/a05/aiinterview/ai/contract/StrategyCatalog.java`
- 删除：`backend/src/main/java/com/a05/aiinterview/ai/contract/EvaluationDecisionActionCatalog.java`
- 测试：`backend/src/test/java/com/a05/aiinterview/ai/contract/StrategyCatalogTest.java`

- [ ] **步骤 1：定义规范化策略枚举**

新建 `StrategyCode`，第一版稳定集合为：
- `S_P_VERIFY`
- `S_P_DEEP_LINK`
- `S_P_VARIANT`
- `S_P_SAME_DOMAIN_SHIFT`
- `S_SWITCH_DOMAIN`
- `S_J_RECONSTRUCT`
- `S_J_RESPONSIBILITY`
- `S_J_PRESSURE`
- `S_J_TRADEOFF`
- `S_J_GUARDRAILS`
- `S_J_EVOLUTION`
- `S_J_SWITCH_POINT`
- `S_J_SWITCH_PROJECT`
- `S_B_REAL_EVENT`
- `S_B_DECISION`
- `S_B_REFLECTION`
- `S_B_TRANSFER`
- `S_B_CONFLICT`
- `S_ENTER_PRINCIPLE`
- `S_ENTER_PROJECT`
- `S_ENTER_SCENARIO`
- `S_ENTER_BEHAVIORAL`
- `S_WRAPUP`

这套编码反映了已确认的合并规则：
- 旧 `收敛并项目外扩` -> 合并进 `S_J_SWITCH_POINT`
- 旧 `真实情景` -> 合并进 `S_J_PRESSURE`

- [ ] **步骤 2：定义策略元信息**

新建 `StrategyDefinition`，字段包括：
- `StrategyCode code`
- `String label`
- `String description`
- `String applicableWhen`
- `MoveType moveType`
- `QuestionType[] allowedCurrentQuestionTypes`
- `QuestionType targetQuestionType`
- `boolean requiresTargetDomain`
- `boolean wrapup`

- [ ] **步骤 3：实现目录访问接口**

在 `StrategyCatalog` 中提供：
- `all()`
- `byCode(String code)`
- `availableFor(QuestionType currentType, StrategyContext context)`
- `requiresTargetDomain(StrategyCode code)`
- `targetQuestionType(StrategyCode code)`
- `isWrapup(StrategyCode code)`

- [ ] **步骤 4：编写目录测试**

覆盖：
- 被删的旧动作不再存在
- `S_ENTER_PRINCIPLE` 必须要求目标域
- `S_SWITCH_DOMAIN` 必须要求目标域
- `S_J_PRESSURE` 不要求目标域
- `S_WRAPUP` 是唯一结束策略

- [ ] **步骤 5：运行测试**

运行：
`mvn -q "-Dtest=StrategyCatalogTest,EvaluationDecisionContractTest" test`

- [ ] **步骤 6：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/ai/contract backend/src/test/java/com/a05/aiinterview/ai/contract/StrategyCatalogTest.java
git commit -m "refactor: introduce canonical evaluation strategy catalog"
```

### 任务 4：构建后端主导的策略注入与剩余域菜单

**涉及文件：**
- 新建：`backend/src/main/java/com/a05/aiinterview/interview/engine/AvailableStrategyAssembler.java`
- 新建：`backend/src/main/java/com/a05/aiinterview/interview/engine/RemainingDomainMenuBuilder.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitService.java`
- 测试：`backend/src/test/java/com/a05/aiinterview/interview/engine/AvailableStrategyAssemblerTest.java`
- 测试：`backend/src/test/java/com/a05/aiinterview/interview/engine/AnswerSubmitServiceEvaluationInputTest.java`

- [ ] **步骤 1：根据 syllabus + ledger 构建剩余域菜单**

`RemainingDomainMenuBuilder` 需要：
- 读取 syllabus 中的 domain
- 读取 ledger 中已覆盖/已关闭的 domain 状态
- 只输出仍然允许被理论题路由选中的 domain
- 为每个 domain 挂载对应的 `focusPoints`

- [ ] **步骤 2：构建运行时策略可用性**

`AvailableStrategyAssembler` 需要根据以下上下文决定当前可用策略：
- 当前题型
- quota 状态
- 是否存在项目/实习经历
- 剩余理论域菜单
- 必要时结合轮次 / 年限

输出：
- 有序的 `List<EvaluationDecisionInput.AvailableStrategyItem>`
- 每个条目能被 Prompt 渲染为你要求的自然语言策略段落

- [ ] **步骤 3：把它们注入评估输入**

在 `AnswerSubmitService.buildEvaluationInput(...)` 中：
- 彻底移除 `buildInterviewGoalSummary`、`buildQuotaSummary`、`buildPossibleFutureDirections` 在 Prompt 输入路径中的使用
- 改为调用：
  - `RemainingDomainMenuBuilder`
  - `AvailableStrategyAssembler`

- [ ] **步骤 4：更新测试**

`AnswerSubmitServiceEvaluationInputTest` 需要断言：
- 输入中不再出现旧字段
- 输入中包含 `remainingTargetDomains`
- 输入中包含 `availableStrategies`
- 不可用策略会被正确过滤掉

- [ ] **步骤 5：运行测试**

运行：
`mvn -q "-Dtest=AvailableStrategyAssemblerTest,AnswerSubmitServiceEvaluationInputTest" test`

- [ ] **步骤 6：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/engine backend/src/test/java/com/a05/aiinterview/interview/engine
git commit -m "refactor: inject available strategies and remaining target domains"
```

## Chunk 3：执行计划与状态机解耦

### 任务 5：引入 DecisionExecutionPlan，作为唯一运行时计划对象

**涉及文件：**
- 新建：`backend/src/main/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlan.java`
- 新建：`backend/src/main/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilder.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitService.java`
- 测试：`backend/src/test/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilderTest.java`

- [ ] **步骤 1：定义内部执行计划**

`DecisionExecutionPlan` 建议字段：
- `interviewAction`
- `strategyCode`
- `targetQuestionType`
- `nextFocus`
- `targetDomainCode`
- `targetDomainName`
- `newCoveredDomains`
- `newCoveredPoints`
- `retrievalPlans`
- `decisionReason`

- [ ] **步骤 2：从校验后的 AI 输出构建执行计划**

`DecisionExecutionPlanBuilder` 负责：
- 把 `finalDecision` 解析成 `StrategyCode`
- 通过 `StrategyCatalog` 将策略映射为目标题型
- 当策略要求目标域时，校验 `targetDomainCode` 是否在剩余域菜单中
- 如果当前题是 `PRINCIPLE` 且策略属于同域理论推进，允许继承 `currentQuestion.domainCode/domainName`，确保同域推进时上下文不断层
- 规范化 `newCoveredDomains`
- 对不可能组合返回结构化错误码，而不是直接终止主链路

- [ ] **步骤 3：让 AnswerSubmitService 改用执行计划**

新流程：
1. 构建 evaluation input
2. 调用 AI
3. 校验原始输出
4. 若校验失败，则带着错误码和错误信息调用 repair prompt，最多重试 2 次
5. repair 成功后构建 `DecisionExecutionPlan`
6. repair 连续失败后，按系统异常结束面试，但必须写入单独的终止原因和审计信息
7. 同时持久化原始输出、repair 审计与有效执行计划
8. 下游服务只消费执行计划，不再消费 AI 原始路由字段

- [ ] **步骤 4：补 plan builder 测试**

覆盖：
- `S_ENTER_PROJECT` -> `PROJECT_DEEP_DIVE`
- `S_ENTER_PRINCIPLE` -> `PRINCIPLE` 且要求目标域
- `S_SWITCH_DOMAIN` -> `PRINCIPLE` 且要求目标域
- `S_WRAPUP` -> `WRAPUP`
- 同域理论推进策略在 `targetDomainCode=""` 时继承当前题域
- `S_SWITCH_DOMAIN` / `S_ENTER_PRINCIPLE` 缺少目标域时进入 repair
- repair 连续失败后，产生 `SYSTEM_DECISION_ERROR` 终止

- [ ] **步骤 5：运行测试**

运行：
`mvn -q "-Dtest=DecisionExecutionPlanBuilderTest,AnswerSubmitServiceEvaluationInputTest" test`

- [ ] **步骤 6：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/engine backend/src/test/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilderTest.java
git commit -m "refactor: route evaluation decisions through execution plan"
```

### 任务 6：让 QuestionStreamService 停止读取 AI 原始路由字段

**涉及文件：**
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitPersistenceService.java`
- 测试：`backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java`

- [ ] **步骤 1：显式持久化执行计划字段**

在 `AnswerSubmitPersistenceService.saveAttempt(...)` 中：
- 将原始 AI 输出保存到 `rawEvaluationDecision`
- 将 repair 审计保存到 `decisionRepairAudit`
- 将真正执行的计划保存到顶层字段或 `effectiveDecisionPlan`
- 若属于系统异常结束，显式保存：
  - `terminationSource = SYSTEM_ERROR`
  - `terminationReason = SYSTEM_DECISION_ERROR`
  - `repairAttempts`

- [ ] **步骤 2：在 QuestionStreamService 中只读取执行计划**

重写 `extractNextQuestionPlan(...)`：
- 不再读取 `nextEntryAction`
- 不再读取 `nextQuestionType`
- 不再读取 `expectedAnswerPoints`
- 改为读取：
  - strategy code
  - 执行计划中推导出的目标题型
  - `nextFocus`
  - `targetDomainCode / targetDomainName`
  - `retrievalPlans`

- [ ] **步骤 3：更新 goal summary 生成方式**

`QuestionStreamService.buildGoalSummary(...)` 改为基于 `StrategyCode` 生成，不再依赖 `nextEntryAction`。

- [ ] **步骤 4：更新流式出题测试**

`QuestionStreamServiceBuildInputTest` 需要断言：
- 出题输入使用的是执行计划
- evaluationJson 中移除的旧字段不再是必需项
- 理论题路由依赖 `targetDomainCode`
- 同域理论推进时，即使 AI 未回填 `targetDomainCode`，也能从当前题域继承得到连续上下文

- [ ] **步骤 5：运行测试**

运行：
`mvn -q "-Dtest=QuestionStreamServiceBuildInputTest,QuestionStreamServiceReconnectTest" test`

- [ ] **步骤 6：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitPersistenceService.java backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java
git commit -m "refactor: consume execution plan in question streaming"
```

## Chunk 4：状态沉淀、账本与持久化清理

### 任务 7：围绕 canonical domain code 重建 ledger reduction

**涉及文件：**
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/LedgerMutation.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/StateLedgerPatchService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/DefaultStateLedgerReducer.java`
- 测试：`backend/src/test/java/com/a05/aiinterview/interview/engine/StateLedgerPatchServiceUpdatedLedgerTest.java`

- [ ] **步骤 1：替换被沉淀 domain 的数据结构**

`LedgerMutation` 中的 covered domain 结构改为：
- `List<CoveredDomainByCode>`
  - `domainCode`
  - `domainName`

- [ ] **步骤 2：按 domain code 执行沉淀和关闭**

`StateLedgerPatchService` 需要：
- 通过 `domainCode` 关闭知识域
- 不再依赖 AI 提供的 `domainId`
- 保持 `newCoveredPoints` 语义不变

同时增加理论题域不变量：
- `PRINCIPLE` 题持久化时必须写入 `domainCode/domainName`
- 读取历史脏数据时，如发现理论题缺失域信息，应优先从 `generationContextJson` / syllabus 恢复
- 仍无法恢复时，按系统异常路径处理，不能继续让“无域理论题”进入主链路

- [ ] **步骤 3：修正 asked_total 语义**

当前 `DefaultStateLedgerReducer` 会在“提交回答”时直接给 `asked_total` 加一，这个语义是错的。
修改为：
- 提交回答时不推进 `asked_total`
- 只有“新题真正落库成功”时才增加 `asked_total`

- [ ] **步骤 4：更新 ledger 测试**

覆盖：
- 通过 `domainCode` 关闭 domain
- 完全不再使用 `domainId`
- 答题提交时 `asked_total` 不变

- [ ] **步骤 5：运行测试**

运行：
`mvn -q "-Dtest=StateLedgerPatchServiceUpdatedLedgerTest,StateLedgerPatchServiceDebugLoggingTest" test`

- [ ] **步骤 6：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/engine backend/src/test/java/com/a05/aiinterview/interview/engine
git commit -m "refactor: reduce ledger by canonical domain code"
```

### 任务 8：去掉基于原始 finalDecision 字符串的 quota 更新

**涉及文件：**
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/QuotaStateSupport.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- 测试：`backend/src/test/java/com/a05/aiinterview/interview/engine/QuotaStateSupportTest.java`

- [ ] **步骤 1：让 quota reducer 改为消费 StrategyCode**

替换：
- 基于中文 `finalDecision` 字符串的分支判断

改为：
- 基于 `StrategyCode`

Reducer 需要决定：
- 连续计数是延续还是清零
- 哪个题型总额需要增加

- [ ] **步骤 2：通过执行计划推进 quota**

在下一题真正落库成功时：
- 使用 `DecisionExecutionPlan.strategyCode`
- 使用 `DecisionExecutionPlan.targetQuestionType`

- [ ] **步骤 3：更新 quota 测试**

覆盖：
- 理论题策略推进
- 项目题策略推进
- `S_ENTER_PRINCIPLE` / `S_SWITCH_DOMAIN`
- `S_WRAPUP`

- [ ] **步骤 4：运行测试**

运行：
`mvn -q "-Dtest=QuotaStateSupportTest,QuestionStreamServiceBuildInputTest" test`

- [ ] **步骤 5：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/engine/QuotaStateSupport.java backend/src/test/java/com/a05/aiinterview/interview/engine/QuotaStateSupportTest.java
git commit -m "refactor: update quota state by strategy code"
```

## Chunk 5：兼容切换与回归兜底

### 任务 9：删除旧字段与死亡兼容分支

**涉及文件：**
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitPersistenceService.java`
- 测试：所有受影响测试

- [ ] **步骤 1：删掉剩余旧路由字段的读写**

删除所有仍依赖以下字段的代码：
- `candidateStrategies`
- `nextEntryAction`
- `nextQuestionType`
- `expectedAnswerPoints`
- `answerSummary`
- `answerAssessment`

- [ ] **步骤 2：删除旧 Prompt 变量拼装**

删掉所有仍引用以下字段的 Prompt 输入路径：
- `quotaSummary`
- `possibleFutureDirections`
- `interviewGoalSummary`

- [ ] **步骤 3：运行聚焦回归测试**

运行：
`mvn -q "-Dtest=PromptTemplateCoverageTest,EvaluationDecisionContractTest,AnswerSubmitServiceEvaluationInputTest,QuestionStreamServiceBuildInputTest,QuotaStateSupportTest,StateLedgerPatchServiceUpdatedLedgerTest" test`

- [ ] **步骤 4：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview backend/src/test/java/com/a05/aiinterview
git commit -m "refactor: remove legacy evaluation decision routing fields"
```

### 任务 10：端到端验证与人工验收场景

**涉及文件：**
- 仅验证，不要求必须修改代码

- [ ] **步骤 1：跑更大范围的后端回归**

运行：
`mvn test`

- [ ] **步骤 2：手工验证核心面试流**

验证：
- intro -> project / principle / scenario 的切换仍然正常
- 理论题选择 `S_SWITCH_DOMAIN` 时必须要求目标域
- 项目题选择 `S_ENTER_PRINCIPLE` 时能正确路由到理论题生成
- `wrapup` 仍能正常结束会话并触发报告生成
- `retrievalPlans` 仍能流入后续出题链路

- [ ] **步骤 3：整理残余风险清单**

记录任何剩余风险：
- 老历史 attempt 里仍是旧 evaluation JSON
- 存量会话迁移
- 部分环境只升级了 Prompt 或只升级了后端时的半切换问题
- 系统异常结束与正常 `WRAPUP` 在前端展示上的区分策略

- [ ] **步骤 4：如果人工验收触发补丁，再提交**

```bash
git add .
git commit -m "test: verify evaluation decision refactor end to end"
```
