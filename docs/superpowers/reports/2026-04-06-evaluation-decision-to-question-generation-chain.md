# evaluation_decision 到 question generation 链路解剖

## 文档目的

本文用于详细说明从 `evaluation_decision` 提示词到最终 `question generation` 出题提示词之间的完整程序链路。

本文关注的不是抽象方案，而是**当前代码真实怎么跑**，重点回答下面几个问题：

- `evaluation_decision` 提示词要求 AI 输出什么字段？
- 后端程序如何对这些字段做清洗、校验、修复和兜底？
- 哪些字段会被原样透传，哪些字段会被删减、清空、补默认值或系统派生？
- 为什么 `QuestionStreamService` 最终读取的是 `effectiveDecisionPlan`，而不是 AI 的原始 JSON？
- `RagPlanCompiler`、`RagRetrievalService` 和 `QuestionGenerationInput` 分别如何消费这些字段？

本文默认读者已经了解本项目的基础概念，但不要求提前阅读其他 RAG 基线文档。

## 总链路概览

从一次回答提交到下一题生成，真实链路不是 4 段，而是 7 段：

1. `evaluation_decision` Prompt 约束 AI 输出结构化 JSON
2. `OpenAiClient.callEvaluationDecision()` 调用模型并做第一层契约清洗
3. `DecisionExecutionPlanBuilder` 对 AI 输出做第二层决策合法性校验
4. 若失败则进入 `DecisionRepairOrchestrator` 修复；仍失败则进入系统兜底
5. 生效后的 `DecisionExecutionPlan` 写入 `attempt.evaluationJson.effectiveDecisionPlan`
6. `QuestionStreamService` 重新读取 `effectiveDecisionPlan`，调用 `RagPlanCompiler` 和 `RagRetrievalService`
7. `QuestionGenerationInput` 组装完成后，进入 `question_generation_stream` Prompt 生成下一题

这意味着：

- `QuestionStreamService` 不直接信任 AI 原始输出。
- 出题阶段依赖的是**生效计划**，不是原始 AI 返回。
- 任何字段是否最终生效，都要看它是否通过了验证、修复和兜底链。

## 第 1 层：`evaluation_decision` Prompt 与输出契约

相关文件：

- [evaluation-decision.md](D:\a05-cursor\backend\src\main\resources\prompts\evaluation-decision.md)
- [EvaluationDecisionOutput.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\dto\EvaluationDecisionOutput.java)

### Prompt 的职责

`evaluation_decision` Prompt 的职责不是直接出题，而是：

- 理解候选人当前回答在会话中的意义
- 决定下一步动作是继续、平移、切换还是结束
- 在需要时给下游提供 `retrievalPlans`

Prompt 中明确要求 AI 返回结构化 JSON，且字段顺序固定。当前 Prompt 仍要求 `retrievalPlans` 输出 7 个字段：

- `goal`
- `displayQuery`
- `queryText`
- `keywordHints`
- `difficultyHint`
- `mustHaveClues`
- `avoidClues`

这只是 **Prompt 层要求**，不等于这些字段一定全部在后端后续层级中被等价使用。

### DTO 的职责

`EvaluationDecisionOutput` 是 AI 输出 DTO，包含三类信息：

1. 辅助解释字段
- `answerUnderstanding`
- `planningIntent`
- `decisionReason`

2. 决策字段
- `interviewAction`
- `finalDecision`
- `nextFocus`
- `nextItemType`
- `nextItemName`
- `nextProjectPoint`
- `targetDomainCode`

3. 沉淀与检索字段
- `newCoveredDomains`
- `newCoveredPoints`
- `retrievalPlans`

其中 `retrievalPlans` 的每个元素当前由 `EvaluationDecisionOutput.RetrievalPlan` 表示，字段定义仍是 Prompt 层的 7 字段版本。

## 第 2 层：`OpenAiClient` 调用模型并做第一层清洗

相关文件：

- [OpenAiClient.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\impl\OpenAiClient.java)
- [AiOutputContractValidator.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\contract\AiOutputContractValidator.java)

### 调用过程

`OpenAiClient.callEvaluationDecision()` 的主要步骤是：

1. 用 `BeanOutputConverter<EvaluationDecisionOutput>` 生成 JSON Schema
2. 渲染 `evaluation_decision` Prompt
3. 调用模型
4. 将模型输出解析为 `EvaluationDecisionOutput`
5. 交给 `AiOutputContractValidator.validateEvaluationDecision()` 做第一层清洗

### 第一层清洗做了什么

`AiOutputContractValidator` 不负责判断策略是否合理，但会做**基础契约清洗**。

#### 字符串字段

下面这些字段都会做 `null -> ""` 和 `trim()`：

- `answerUnderstanding`
- `planningIntent`
- `decisionReason`
- `finalDecision`
- `nextFocus`
- `nextItemType`
- `nextItemName`
- `nextProjectPoint`
- `targetDomainCode`

这意味着：

- AI 即使输出 `null`，后端也会把它变成空字符串。
- 这一层已经消除了大部分 `null` 风险。

#### 数组字段

下面这些字段会被清洗成非 null 的列表：

- `newCoveredDomains`
- `newCoveredPoints`
- `retrievalPlans`

#### `newCoveredDomains`

`newCoveredDomains` 会被过滤掉以下非法项：

- `item == null`
- `domainCode` 为空
- `domainName` 为空

如果 AI 原始输出里有脏项，清洗后数量会减少。

#### `newCoveredPoints`

会执行：

- 过滤空值和空白字符串
- `trim()`
- `distinct()`

这意味着这里允许**删减**，但不新增程序派生值。

#### `retrievalPlans`

`sanitizeRetrievalPlans()` 会对每个计划做以下处理：

- `goal -> defaultString(..., "")`
- `displayQuery -> defaultString(..., "")`
- `queryText -> defaultString(..., "")`
- `keywordHints -> sanitizeStringList()`
- `difficultyHint -> defaultString(..., "")`
- `mustHaveClues -> sanitizeStringList()`
- `avoidClues -> sanitizeStringList()`

这里的关键点是：

- `retrievalPlans` 的每个字段都可能被清洗、去空、去重
- 后端不会在这一层**新增** AI 没给出的语义字段
- 但会删除空白元素、把 null 变成空字符串或空列表

### 第一层清洗之后仍会发生什么

`AiOutputContractValidator` 校验通过不代表“决策已经可执行”。  
后面还要进入 `DecisionExecutionPlanBuilder` 做第二层合法性校验。

## 第 3 层：`DecisionExecutionPlanBuilder` 做第二层决策合法性校验

相关文件：

- [DecisionExecutionPlanBuilder.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\DecisionExecutionPlanBuilder.java)
- [DecisionExecutionPlan.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\DecisionExecutionPlan.java)

### 为什么还需要这一层

第一层只解决“格式像不像 DTO”，第二层才解决“这个决策在当前上下文里是否合法、可执行”。

`DecisionExecutionPlanBuilder.build()` 输入包括：

- 当前题目 `currentQuestion`
- 已经清洗过的 `EvaluationDecisionOutput`
- 剩余待考察知识域菜单 `remainingTargetDomains`
- 当前可用策略池 `availableStrategies`
- 决策来源 `RAW_AI / REPAIRED_AI`

### 第二层校验的核心逻辑

#### 1. `interviewAction` 必须合法

只允许：

- `CONTINUE`
- `WRAPUP`

否则直接失败，错误码：

- `INVALID_STRATEGY_CODE`

#### 2. `WRAPUP` 分支的强约束

当 `interviewAction == WRAPUP` 时，必须同时满足：

- `finalDecision == S_WRAPUP`
- `nextFocus == ""`
- `targetDomainCode == ""`
- `retrievalPlans` 为空

否则返回失败：

- `WRAPUP_FIELDS_MUST_BE_EMPTY`

并且成功时会构造一个新的 `DecisionExecutionPlan`：

- `retrievalPlans` 被强制写成 `List.of()`
- 其他继续面试字段被强制清空

这意味着：

- AI 原始输出里如果结束面试却仍带检索计划，后续不会生效
- 这是**明确删减字段**，不是保留原值

#### 3. `CONTINUE` 分支的强约束

当 `interviewAction == CONTINUE` 时，至少检查：

- `finalDecision` 必须是合法策略编码
- `finalDecision` 必须出现在 `availableStrategies` 里
- `nextFocus` 不允许为空

对应错误码包括：

- `INVALID_STRATEGY_CODE`
- `STRATEGY_NOT_IN_AVAILABLE_POOL`
- `NEXT_FOCUS_REQUIRED`

#### 4. 知识域相关约束

如果所选策略要求必须指定目标知识域：

- `targetDomainCode` 不能为空
- 且必须存在于剩余知识域菜单里

否则错误码可能是：

- `TARGET_DOMAIN_REQUIRED`
- `TARGET_DOMAIN_NOT_IN_MENU`

如果是“同知识域原则题”策略且 AI 没写 `targetDomainCode`：

- 系统会尝试从当前题的 `generationContextJson` 继承知识域
- 继承失败则报错：
  - `PRINCIPLE_DOMAIN_INHERITANCE_FAILED`

如果策略本身不需要知识域但 AI 却提供了：

- 报错 `TARGET_DOMAIN_MUST_BE_EMPTY`

### 第二层成功后会发生什么

如果通过校验，系统不会继续沿用原始 `EvaluationDecisionOutput` 作为执行依据，而是生成一个新的 `DecisionExecutionPlan`。

这个计划里：

- `targetQuestionType` 由 `StrategyCatalog.targetQuestionType(strategyCode)` 推导
- `targetDomainName` 由剩余知识域菜单推导或继承得到
- `retrievalPlans` 直接来自 `output.getRetrievalPlans()` 的只读拷贝
- `effectiveDecisionSource` 被明确标记为当前来源

这意味着程序开始进入“**生效计划**”语义，而不是“AI 原始输出”语义。

## 第 4 层：`DecisionRepairOrchestrator` 修复非法决策

相关文件：

- [DecisionRepairOrchestrator.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\DecisionRepairOrchestrator.java)
- [AnswerSubmitService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\AnswerSubmitService.java)

### 进入修复的时机

在 `AnswerSubmitService.resolveDecision()` 中：

1. 先提取 AI 原始输出
2. 调 `DecisionExecutionPlanBuilder` 校验
3. 如果失败，进入 `DecisionRepairOrchestrator.repair()`

### 修复模式怎么构造

修复时并不是直接拿原输出原地修，而是：

- 构造一个新的 `EvaluationDecisionInput`
- `repairMode = true`
- 带上 `validationErrors`
- 带上 `rawDecisionOutput` 摘要

然后再次调用 `OpenAiClient.callEvaluationDecision()`

### 修复后的结果怎么处理

修复后的输出会再次经过：

1. JSON 解析
2. `AiOutputContractValidator`
3. `DecisionExecutionPlanBuilder`

且这次 `effectiveDecisionSource` 标记为：

- `REPAIRED_AI`

如果修复成功：

- 生效计划来自修复后的输出
- 原始输出不会继续直接生效

这意味着 AI 原始字段有可能在 repair 之后发生变化，最终出题阶段看到的是**修复后的字段版本**。

## 第 5 层：系统兜底会替换整份决策计划

相关文件：

- [AnswerSubmitService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\AnswerSubmitService.java)
- `SystemFallbackPlanBuilder`

### 进入兜底的条件

如果 repair 之后仍然非法：

- 系统不再信任 AI 输出
- 直接根据状态构造兜底计划

可能是：

- `buildContinuePlan(...)`
- `buildSystemErrorPlan(...)`

### 兜底意味着什么

一旦进入 `SYSTEM_FALLBACK`：

- 生效字段不再来自 AI
- `retrievalPlans` 也可能变成系统兜底值，或者为空
- `QuestionStreamService` 后续读取的是系统兜底计划，不是 AI 输出

这一步是当前链路里最容易被忽略的一层。

## 第 6 层：生效计划写入 `evaluationJson`

相关文件：

- [AnswerSubmitService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\engine\AnswerSubmitService.java)

最终，`DecisionResolution` 会把下面这些信息打进 `evaluationJson`：

- `rawAiOutput`
- `rawResponse`
- `decisionValidation`
- `repairAttempts`
- `repairInput`
- `repairOutput`
- `effectiveDecisionPlan`

这里最关键的是：

- 出题阶段依赖的是 `effectiveDecisionPlan`
- 原始输出只是审计信息

## 第 7 层：`QuestionStreamService` 重新读取 `effectiveDecisionPlan`

相关文件：

- [QuestionStreamService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\service\QuestionStreamService.java)

### `extractNextQuestionPlan()`

`QuestionStreamService` 不直接读取 `EvaluationDecisionOutput`，而是从：

- `attempt.evaluationJson.effectiveDecisionPlan`

重新提取一个 `NextQuestionPlan`。

具体行为：

- 如果 `evaluationJson == null`，返回 `null`
- 如果 `effectiveDecisionPlan == null`，返回 `null`
- 如果 `interviewAction == WRAPUP`，直接返回 `null`

### 字段读取与再包装

`extractNextQuestionPlan()` 会把 `effectiveDecisionPlan` 重新映射为：

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

这里有两个关键细节：

#### 1. `decisionReason` 在 `SYSTEM_FALLBACK` 情况下会被清空

如果 `effectiveDecisionSource == SYSTEM_FALLBACK`：

- `decisionReason` 会被写成 `""`

这说明出题阶段不会继续沿用 AI 或修复失败后的原始理由。

#### 2. `retrievalPlans` 来自落库后的生效计划

不是 AI 原始输出，也不是 Prompt 当时的字符串。  
如果前面修复或兜底改过，这里读到的就是改后的版本。

## 第 8 层：`RagPlanCompiler` 把生效计划编译成检索请求

相关文件：

- [RagPlanCompiler.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java)
- [RagRetrievalRequest.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java)

### 输入是什么

`safeRetrieveRag()` 会把 `NextQuestionPlan` 转成 `DecisionExecutionPlan`，然后交给：

- `ragPlanCompiler.compile(...)`

### 编译器做了什么

#### 1. 判断是否需要检索

`shouldRetrieve()` 的规则是：

- `PRINCIPLE / SCENARIO / BEHAVIORAL`：只要有 `retrievalPlans` 就检索
- `PROJECT_DEEP_DIVE`：只有有明确技术钩子才检索

技术钩子当前会检查：

- `nextFocus`
- `nextProjectPoint`
- `retrievalPlan.displayQuery`
- `retrievalPlan.queryText`
- `retrievalPlan.keywordHints`

这意味着：

- `displayQuery` 虽然不是主检索字段，但当前仍参与“是否检索”的判断
- 当前实现还没有完全摆脱旧 7 字段契约

#### 2. 生成 `RagRetrievalRequest`

如果 `shouldRetrieve == false`，编译器会返回一个空请求：

- `shouldRetrieve=false`
- `displayQuery=""`
- `queryText=""`
- `difficultyHint=""`
- `keywordQueries=[]`
- `preferredDifficultyLevels=[]`
- `mustHaveClues=[]`
- `avoidClues=[]`

这是**程序补默认值**，不是 AI 输出。

如果 `shouldRetrieve == true`，会构造一个完整请求：

- `displayQuery <- retrievalPlan.displayQuery`，缺失时退回 `nextFocus`
- `queryText <- retrievalPlan.queryText`，缺失时退回 `nextFocus`
- `keywordQueries <- retrievalPlan.keywordHints`
- `difficultyHint <- retrievalPlan.difficultyHint`
- `preferredDifficultyLevels <- 程序按 difficultyHint 派生`
- `positionCode / questionType / experienceLevel / domainCode / projectName <- 程序上下文补齐`
- `mustHaveClues / avoidClues <- retrievalPlan` 原样清洗后透传

这里的关键是：

- `RagRetrievalRequest` 字段比 AI `retrievalPlan` 多
- 多出来的字段主要由编译器派生或补齐
- 这一步既有“原样透传”，也有“系统派生”

## 第 9 层：`RagRetrievalServiceImpl` 消费检索请求

相关文件：

- [RagRetrievalServiceImpl.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRetrievalServiceImpl.java)
- [DashScopeRagRerankService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\DashScopeRagRerankService.java)
- [RagContext.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagContext.java)

### 入口判定

如果：

- `request == null`
- 或 `request.shouldRetrieve == false`

直接返回：

- `RagContext.empty()`

### 当前真实检索链路

当前 `RagRetrievalServiceImpl` 实现仍是：

1. lexical 预过滤
2. dense 召回
3. rerank
4. 最终硬护栏
5. 构造 `RagContext`

### 当前字段如何被使用

#### `displayQuery`

当前主要用于：

- 日志打印
- rerank 失败日志
- lexical 词法查询缺词时兜底

它不是主 dense 查询，但在当前实现中仍然有兜底作用。

#### `queryText`

当前主要用于：

- dense 查询的第一优先级输入
- rerank brief 的“目标”字段

如果 `queryText` 为空，dense 会退到：

- `focusPoint`
- `keywordQueries`
- `mustHaveClues`

#### `keywordQueries`

当前主要用于：

- lexical 预过滤的主词源

#### `difficultyHint`

当前会出现在日志中，并被编译器转成 `preferredDifficultyLevels`，  
但在当前 `RagRetrievalServiceImpl` 里并没有真正被检索主流程强使用。

这意味着：

- 它当前是弱生效字段
- 不是完全没用，但也不是强影响字段

#### `mustHaveClues / avoidClues`

当前主要不参与召回主流程，而是进入 rerank brief：

- `mustHaveClues -> "必须覆盖"`
- `avoidClues -> "避免内容"`

如果 rerank 失败，本地回退排序只按 `denseRank` 打分，不再看这些字段。

### 输出的 `RagContext`

最终返回给出题链路的不是原始候选，而是：

- `summary`
- `contextText`
- `retrievedMaterials`
- `followUpCandidates`
- `retrievalAudit`

其中 `retrievalAudit` 会记录：

- `retrievalTriggered`
- `lexicalCandidateCount`
- `denseCandidateCount`
- `rerankPreTopQuestionIds`
- `rerankPostTopQuestionIds`
- `injectedQuestionIds`

## 第 10 层：`QuestionGenerationInput` 重组出题上下文

相关文件：

- [QuestionStreamService.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\interview\service\QuestionStreamService.java)
- [QuestionGenerationInput.java](D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\dto\QuestionGenerationInput.java)
- [question-generation-stream.md](D:\a05-cursor\backend\src\main\resources\prompts\question-generation-stream.md)

### `safeRetrieveRag()`

`QuestionStreamService.safeRetrieveRag()` 的特点是：

- RAG 失败不阻断主流程
- 编译失败、检索异常都会回退为 `RagContext.empty()`

也就是说：

- 检索不是硬依赖
- 它是增强链路，不是必须成功的主链路

### `buildRetrievalContext()`

这里会把两类东西同时放进 `QuestionGenerationInput.RetrievalContext`：

1. `retrievalPlans`
- 来自 `plan.getRetrievalPlans()`

2. `retrievedMaterials`
- 来自 `ragContext.getRetrievedMaterials()`

同时还会补：

- `followUpCandidates`
- `retrievalAudit`
- `summary`

### `summary` 的 fallback

如果：

- `ragContext` 为空
- 或 `ragContext.summary` 为空

则 `summary` 会被强制写成：

- `RAG_CONTEXT_FALLBACK = "无外部参考资料，请严格依赖你自身的工程师知识库进行出题。"`

这意味着：

- 即便完全没检索命中，出题 Prompt 也总能拿到一个 `retrievalContext.summary`
- 这是程序补的默认值，不来自 AI 也不来自检索

### `question_generation_stream` Prompt 如何消费这些字段

出题 Prompt 明确写了：

- `retrievedMaterials` 非空时，优先使用真实题目卡片材料
- `retrievalPlans` 仅在无真实材料时作为弱提示
- `follow_up_ids` 是可选追问候选，不是强制跳题规则
- 项目题若无检索结果，仍优先依据项目上下文自然追问

这说明：

- `retrievalPlans` 到这里已经是**弱信号**
- `retrievedMaterials` 才是检索成功后的主信号

## 字段总表：每一层到底发生了什么

### A. AI 原样输出后通常还能保留到后面的字段

- `interviewAction`
- `finalDecision`
- `nextFocus`
- `nextItemType`
- `nextItemName`
- `nextProjectPoint`
- `targetDomainCode`
- `retrievalPlans.*`

前提是它们通过了后续验证和修复。

### B. 会被第一层清洗的字段

- 所有字符串：`null -> ""`，并 `trim()`
- 所有列表：`null -> []`
- `newCoveredPoints` / `keywordHints` / `mustHaveClues` / `avoidClues`：去空、去重
- `newCoveredDomains`：非法项会被删掉

### C. 会被第二层验证强制清空或拒绝的字段

在 `WRAPUP` 时：

- `nextFocus`
- `targetDomainCode`
- `retrievalPlans`

如果非空，就整份决策失败。

### D. 会被系统派生的字段

在 `DecisionExecutionPlanBuilder`：

- `targetQuestionType`
- `targetDomainName`

在 `RagPlanCompiler`：

- `preferredDifficultyLevels`
- `positionCode`
- `experienceLevel`
- `projectName`
- `domainCode`
- fallback 的 `displayQuery/queryText`

在 `QuestionStreamService.buildGenInput()`：

- `goalSummary`
- `relatedDomainCode`
- `relatedDomainName`
- `relatedItemKey`
- `relatedItemType`
- `relatedItemName`
- `retrievalContext.summary` fallback

### E. 会被完全忽略或只用于调试的字段

当前语义下：

- `answerUnderstanding`
- `planningIntent`

它们只帮助模型先思考，不进入后端决策执行逻辑。

### F. 当前最值得注意的现实偏差

1. Prompt 仍要求 7 字段 `retrievalPlans`
- 但你们近期讨论方向已经倾向收缩到更小字段集

2. `QuestionStreamService` 只认 `effectiveDecisionPlan`
- 原始 AI 输出只是审计信息，不是执行依据

3. `RagRetrievalRequest` 里有不少字段并不是 AI 直接给的
- 它是编译后的执行请求，不是 Prompt 契约镜像

4. 当前检索服务仍保留旧字段依赖
- 例如 `displayQuery`
- 例如 `mustHaveClues / avoidClues`

这意味着如果后续要收缩检索字段，不能只改 Prompt，还必须同步：

- DTO
- Validator
- `RagPlanCompiler`
- `RagRetrievalRequest`
- `RagRetrievalServiceImpl`
- `DashScopeRagRerankService`
- `QuestionGenerationInput` 的文档口径

## 结论

这条链路的本质不是：

- `AI 输出什么，后面就直接拿什么去检索和出题`

而是：

1. AI 输出结构化意图
2. 后端先做契约清洗
3. 再做决策合法性验证
4. 必要时修复
5. 再不行则系统兜底
6. 只有“生效计划”才会进入检索与出题链路

因此，任何字段是否真正影响下一题，都必须问两个问题：

1. 它有没有通过验证/修复/兜底链条？
2. 后续执行层到底有没有真正消费它？

这也是为什么单看 Prompt 或单看 DTO，都无法正确理解这条链路。
