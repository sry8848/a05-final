# RAG Retrieval Brief Contract Reset Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `evaluation_decision -> RagPlanCompiler -> RagRetrievalService -> question generation` 链路中的检索 brief 从 7 字段彻底收缩为 3 字段，并物理删除项目题技术钩子和所有旧字段消费逻辑，不做兼容映射。

**Architecture:** 保留当前整体链路和题库检索模式，不重做架构；本次只重置 retrieval brief 契约、收紧执行校验、瘦身编译器与检索请求对象、删除旧字段消费点，并同步清理测试夹具与说明文档。`shouldRetrieve` 的唯一依据收敛为“是否存在合法 retrievalPlan”，不再允许程序用技术钩子词表二次猜测。

**Tech Stack:** Java 21, Spring Boot, Spring AI, Qdrant, DashScope Rerank, JUnit 5, AssertJ, Markdown prompts

---

## Scope

本计划只覆盖 retrieval brief 重置及其直接影响面：

1. `evaluation_decision` Prompt 与 AI DTO 契约
2. `AiOutputContractValidator` 基础清洗
3. `DecisionExecutionPlanBuilder` 语义校验
4. `RagPlanCompiler`、`RagRetrievalRequest`、`RagRetrievalServiceImpl`、`DashScopeRagRerankService`
5. 相关单元测试、契约测试、Prompt 覆盖测试、评测夹具
6. 两份现有中文文档的同步更新

不包含：

1. dense + sparse 双路召回重构
2. Qdrant BM25 / sparse vector 落地
3. question generation Prompt 结构改版
4. 任何“兼容旧字段但内部偷偷映射”的过渡层

## Hard Rules

实现时必须遵守以下硬规则：

1. `retrievalPlans` 只保留：
   - `queryText`
   - `keywordHints`
   - `difficultyHint`
2. 直接删除：
   - `goal`
   - `displayQuery`
   - `mustHaveClues`
   - `avoidClues`
3. 删除项目题技术钩子整套逻辑：
   - `TECH_HOOK_TOKENS`
   - `hasExplicitProjectTechHook(...)`
   - `containsTechnicalHint(...)`
   - Prompt 中“项目题只有出现技术钩子才应请求检索”的约束
4. 不允许新增任何兼容性映射，例如：
   - `displayQuery = queryText`
   - `mustHaveClues -> keywordHints`
   - `avoidClues` 迁移到别的字段继续活
5. `keywordHints` 允许为空数组；为空时 lexical 侧直接为空，不得再回退到 `focusPoint` 或旧字段造词
6. `difficultyHint` 继续只做软约束，不做硬过滤；若当前实现无消费，就保持为透传字段，不得凭空加复杂逻辑

## File Map

- `backend/src/main/resources/prompts/evaluation-decision.md`
  - 将 retrieval brief 规则改成 3 字段，并删除技术钩子描述与旧示例字段
- `backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java`
  - 收缩 `RetrievalPlan` DTO
- `backend/src/main/java/com/a05/aiinterview/ai/contract/AiOutputContractValidator.java`
  - 只清洗 3 个 retrieval 字段
- `backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java`
  - 锁定新的 retrieval brief 契约与清洗行为
- `backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java`
  - 锁定 Prompt 文案不再包含旧字段与技术钩子
- `backend/src/main/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilder.java`
  - 增加 retrievalPlan 数量、字段合法性和 `difficultyHint` 合法值校验
- `backend/src/test/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilderTest.java`
  - 覆盖新的 retrievalPlan 语义校验
- `backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java`
  - 校准下游读取新 retrievalPlan 结构后的输入组装断言
- `backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java`
  - 删除死字段和旧字段
- `backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java`
  - 删除技术钩子与旧字段编译逻辑
- `backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java`
  - 锁定新的 `shouldRetrieve` 规则和请求对象结构
- `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
  - 删除 `displayQuery` / `mustHaveClues` 兜底与拼接
- `backend/src/main/java/com/a05/aiinterview/rag/service/impl/DashScopeRagRerankService.java`
  - 重写 rerank brief，只消费新字段与程序上下文
- `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`
  - 锁定 lexical 为空时的行为与删除旧字段后的检索路径
- `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`
  - 删除对旧字段的评测断言
- `backend/src/test/resources/rag-eval/interview-retrieval-cases.json`
  - 将 fixture 收缩为 3 字段 retrieval brief
- `docs/superpowers/reports/2026-04-03-interview-rag-baseline-and-improvement-directions.md`
  - 删除旧字段与技术钩子口径
- `docs/superpowers/reports/2026-04-06-evaluation-decision-to-question-generation-chain.md`
  - 更新链路说明，反映新契约和删除的字段

## Chunk 1: 重置 AI Retrieval Brief 契约

### Task 1: 先用测试锁定 Prompt 与契约必须只剩 3 字段

**Files:**
- Modify: `backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java`

- [ ] **Step 1: 修改 `EvaluationDecisionContractTest` 的 CONTINUE 用例**

将样例 JSON 中的 `retrievalPlans` 收缩为：

```json
{
  "queryText": "Seata AT 模式 本地事务边界 分支事务注册",
  "keywordHints": ["Seata", "AT", "分支事务注册"],
  "difficultyHint": "L4"
}
```

并删除所有关于下列 getter 的断言：

```java
getGoal()
getDisplayQuery()
getMustHaveClues()
getAvoidClues()
```

- [ ] **Step 2: 在 `EvaluationDecisionContractTest` 中补一条“空数组 keywordHints 合法”用例**

断言：

```java
assertThat(output.getRetrievalPlans().getFirst().getKeywordHints()).isEmpty();
assertThat(output.getRetrievalPlans().getFirst().getQueryText()).isNotBlank();
```

- [ ] **Step 3: 修改 `PromptTemplateCoverageTest`**

将 `renderEvaluationDecision_shouldLoad()` 中对 system prompt 的断言改为：

```java
assertThat(rendered.getSystemPrompt())
        .contains("queryText")
        .contains("keywordHints")
        .contains("difficultyHint")
        .doesNotContain("goal")
        .doesNotContain("displayQuery")
        .doesNotContain("mustHaveClues")
        .doesNotContain("avoidClues")
        .doesNotContain("技术钩子");
```

- [ ] **Step 4: 运行测试确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest" test
```

Expected:

- `EvaluationDecisionContractTest` 因 DTO 和 validator 仍是 7 字段而失败
- `PromptTemplateCoverageTest` 因 Prompt 仍包含旧字段和技术钩子描述而失败

- [ ] **Step 5: Commit**

```powershell
git add backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java
git commit -m "test: lock retrieval brief to three fields"
```

### Task 2: 修改 Prompt、DTO 和第一层清洗

**Files:**
- Modify: `backend/src/main/resources/prompts/evaluation-decision.md`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/contract/AiOutputContractValidator.java`

- [ ] **Step 1: 修改 `evaluation-decision.md` 的 retrieval brief 描述**

将：

```md
goal / displayQuery / queryText / keywordHints / difficultyHint / mustHaveClues / avoidClues
```

改成：

```md
queryText / keywordHints / difficultyHint
```

同时删除：

- “项目题只有在出现明确技术钩子时才应请求检索”
- 所有旧字段示例

- [ ] **Step 2: 修改 `EvaluationDecisionOutput.RetrievalPlan`**

只保留：

```java
private String queryText;
private List<String> keywordHints;
private String difficultyHint;
```

- [ ] **Step 3: 修改 `AiOutputContractValidator.sanitizeRetrievalPlans(...)`**

只清洗：

```java
.queryText(defaultString(item.getQueryText(), ""))
.keywordHints(sanitizeStringList(item.getKeywordHints()))
.difficultyHint(defaultString(item.getDifficultyHint(), ""))
```

并删除旧字段的默认化与去重逻辑。

- [ ] **Step 4: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest" test
```

Expected:

- `EvaluationDecisionContractTest` 通过
- `PromptTemplateCoverageTest` 通过

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/resources/prompts/evaluation-decision.md backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java backend/src/main/java/com/a05/aiinterview/ai/contract/AiOutputContractValidator.java backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java
git commit -m "refactor: shrink retrieval brief contract to three fields"
```

## Chunk 2: 让执行计划层成为唯一语义校验入口

### Task 3: 用测试锁定 retrievalPlan 的数量和字段合法性

**Files:**
- Modify: `backend/src/test/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilderTest.java`

- [ ] **Step 1: 增加“retrievalPlans 只能有 0 或 1 条”的失败用例**

构造 `retrievalPlans` 含 2 条的 `EvaluationDecisionOutput`，断言：

```java
assertThat(result.isValid()).isFalse();
assertThat(result.getErrorCodes()).contains("RETRIEVAL_PLAN_COUNT_INVALID");
```

- [ ] **Step 2: 增加“有 retrievalPlan 时 queryText 必填”的失败用例**

断言：

```java
assertThat(result.isValid()).isFalse();
assertThat(result.getErrorCodes()).contains("RETRIEVAL_QUERY_TEXT_REQUIRED");
```

- [ ] **Step 3: 增加 `difficultyHint` 非法值失败用例**

例如传入 `L6`，断言：

```java
assertThat(result.isValid()).isFalse();
assertThat(result.getErrorCodes()).contains("RETRIEVAL_DIFFICULTY_HINT_INVALID");
```

- [ ] **Step 4: 增加“keywordHints 可为空数组”的成功用例**

断言：

```java
assertThat(result.isValid()).isTrue();
assertThat(result.getPlan().getRetrievalPlans().getFirst().getKeywordHints()).isEmpty();
```

- [ ] **Step 5: 运行测试确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=DecisionExecutionPlanBuilderTest" test
```

Expected:

- 新增用例失败，因为 builder 目前不检查 retrievalPlan 数量和语义

- [ ] **Step 6: Commit**

```powershell
git add backend/src/test/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilderTest.java
git commit -m "test: lock retrieval plan semantic validation"
```

### Task 4: 在 `DecisionExecutionPlanBuilder` 中实现新校验，并同步下游输入测试

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilder.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java`

- [ ] **Step 1: 在 builder 中新增 retrievalPlan 校验块**

为 `CONTINUE` 分支新增：

1. `retrievalPlans.size() > 1` -> `RETRIEVAL_PLAN_COUNT_INVALID`
2. 存在 retrievalPlan 且 `queryText` 为空 -> `RETRIEVAL_QUERY_TEXT_REQUIRED`
3. `difficultyHint` 非 `""/L1/L2/L3/L4/L5` -> `RETRIEVAL_DIFFICULTY_HINT_INVALID`

不要在这里引入任何字段映射或自动补值。

- [ ] **Step 2: 保持 `WRAPUP` 分支的空字段约束**

确认 `WRAPUP` 仍要求：

- `retrievalPlans` 为空
- `nextFocus` 为空
- `targetDomainCode` 为空

不要放松这些约束。

- [ ] **Step 3: 修改 `QuestionStreamServiceBuildInputTest` 的 retrievalPlan 样例**

把所有 `goal / displayQuery / mustHaveClues / avoidClues` 删掉，只保留：

```java
EvaluationDecisionOutput.RetrievalPlan.builder()
        .queryText("缓存击穿 互斥锁 逻辑过期 热点 key 失效")
        .keywordHints(List.of("缓存击穿", "互斥锁", "逻辑过期"))
        .difficultyHint("L3")
        .build()
```

并把相关断言改为：

```java
assertThat(input.getRetrievalContext().getRetrievalPlans().getFirst().getQueryText()).contains("缓存击穿");
assertThat(input.getRetrievalContext().getRetrievalPlans().getFirst().getKeywordHints()).containsExactly("缓存击穿", "互斥锁", "逻辑过期");
assertThat(input.getRetrievalContext().getRetrievalPlans().getFirst().getDifficultyHint()).isEqualTo("L3");
```

- [ ] **Step 4: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=DecisionExecutionPlanBuilderTest,QuestionStreamServiceBuildInputTest" test
```

Expected:

- 两个测试类通过
- 没有旧 retrieval 字段相关断言残留

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilder.java backend/src/test/java/com/a05/aiinterview/interview/engine/DecisionExecutionPlanBuilderTest.java backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java
git commit -m "refactor: validate retrieval plans in execution plan builder"
```

## Chunk 3: 删除技术钩子并重置编译器与请求对象

### Task 5: 先用测试锁定 `shouldRetrieve` 的新规则和请求对象的瘦身

**Files:**
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java`

- [ ] **Step 1: 修改所有测试样例，只构造 3 字段 retrievalPlan**

辅助构造器改成：

```java
private EvaluationDecisionOutput.RetrievalPlan retrievalPlan(
        String queryText,
        List<String> keywordHints,
        String difficultyHint
) {
    return EvaluationDecisionOutput.RetrievalPlan.builder()
            .queryText(queryText)
            .keywordHints(keywordHints)
            .difficultyHint(difficultyHint)
            .build();
}
```

- [ ] **Step 2: 删除对下列字段的断言**

```java
request.getDisplayQuery()
request.getMustHaveClues()
request.getAvoidClues()
request.getPreferredDifficultyLevels()
```

- [ ] **Step 3: 改写项目题用例**

把原来的：

- “无技术钩子时跳过检索”
- “有技术钩子时触发检索”

改成：

- “无 retrievalPlan 时跳过检索”
- “有 retrievalPlan 时触发检索”

不再提技术钩子。

- [ ] **Step 4: 增加 `keywordHints=[]` 仍允许 `shouldRetrieve=true` 的用例**

断言：

```java
assertThat(request.isShouldRetrieve()).isTrue();
assertThat(request.getKeywordQueries()).isEmpty();
assertThat(request.getQueryText()).isNotBlank();
```

- [ ] **Step 5: 运行测试确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest" test
```

Expected:

- 因 `RagRetrievalRequest` 和 `RagPlanCompiler` 仍依赖旧字段与技术钩子而失败

- [ ] **Step 6: Commit**

```powershell
git add backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java
git commit -m "test: lock retrieval compiler to explicit brief only"
```

### Task 6: 修改 `RagRetrievalRequest` 与 `RagPlanCompiler`

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java`

- [ ] **Step 1: 从 `RagRetrievalRequest` 删除旧字段**

直接删除：

```java
private String displayQuery;
private List<String> preferredDifficultyLevels = List.of();
private List<String> mustHaveClues = List.of();
private List<String> avoidClues = List.of();
```

保留：

```java
shouldRetrieve
queryText
keywordQueries
difficultyHint
domainCode
questionType
focusPoint
positionCode
experienceLevel
projectName
```

- [ ] **Step 2: 删除 `RagPlanCompiler` 的技术钩子逻辑**

删除：

```java
TECH_HOOK_TOKENS
hasExplicitProjectTechHook(...)
containsTechnicalHint(...)
```

并改写 `shouldRetrieve(...)` 为：

```java
if ("PRINCIPLE".equals(questionType) || "SCENARIO".equals(questionType)
        || "BEHAVIORAL".equals(questionType) || "PROJECT_DEEP_DIVE".equals(questionType)) {
    return retrievalPlan != null;
}
return false;
```

这里不要加任何“项目题例外”。

- [ ] **Step 3: 删掉编译时对旧字段的写入**

空请求和正常请求都不再设置：

- `displayQuery`
- `preferredDifficultyLevels`
- `mustHaveClues`
- `avoidClues`

- [ ] **Step 4: 保持 `queryText` 与 `keywordQueries` 的语义简单**

编译规则只保留：

```java
queryText <- retrievalPlan.getQueryText()
keywordQueries <- sanitize(keywordHints)
difficultyHint <- retrievalPlan.getDifficultyHint()
focusPoint <- plan.getNextFocus()
```

不要再根据 `nextFocus` 造 `queryText`，也不要补 `displayQuery` 兜底。

- [ ] **Step 5: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest" test
```

Expected:

- `RagPlanCompilerTest` 通过
- 不再有技术钩子相关测试和代码残留

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java
git commit -m "refactor: remove tech hook gating from rag plan compiler"
```

## Chunk 4: 重置检索与重排服务的字段消费

### Task 7: 先用测试锁定 lexical 为空时的行为与旧字段彻底失效

**Files:**
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java`

- [ ] **Step 1: 删除测试构造里的旧字段**

所有 `RagRetrievalRequest.builder()` 只保留：

```java
.shouldRetrieve(true)
.queryText("...")
.keywordQueries(List.of(...))
.domainCode("...")
.questionType("...")
.difficultyHint("L2")
.focusPoint("...")
```

- [ ] **Step 2: 改写 dense 查询断言**

把“dense query 来自 `displayQuery` 或 `mustHaveClues`”相关断言删掉，只保留：

```java
assertThat(denseRequest.getQuery()).isEqualTo(request.getQueryText());
```

- [ ] **Step 3: 新增 `keywordQueries=[]` 时跳过 lexical prefilter 的测试**

构造：

```java
RagRetrievalRequest.builder()
        .shouldRetrieve(true)
        .queryText("行为面试 与产品意见不一致 冲突沟通 推进结果 复盘")
        .keywordQueries(List.of())
        .questionType("BEHAVIORAL")
        .domainCode("")
        .build();
```

断言：

```java
verify(qdrantClient, never()).scrollAsync(any());
verify(vectorStore).similaritySearch(any(SearchRequest.class));
```

这条测试是本次重构的关键哨兵，防止后续又偷偷回退到 `focusPoint` 造 lexical 词。

- [ ] **Step 4: 运行测试确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagRetrievalServiceImplTest" test
```

Expected:

- 因 `RagRetrievalRequest`、`RagRetrievalServiceImpl` 仍引用旧字段而失败

- [ ] **Step 5: Commit**

```powershell
git add backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java
git commit -m "test: lock retrieval service to new brief fields"
```

### Task 8: 修改检索服务与 rerank brief

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/service/impl/DashScopeRagRerankService.java`

- [ ] **Step 1: 修改检索日志**

把：

```java
displayQuery={}
```

改成：

```java
queryText={}
```

- [ ] **Step 2: 重写 `resolveDenseQueryText(...)`**

只保留：

```java
if (hasText(request.getQueryText())) {
    return request.getQueryText().trim();
}
return hasText(request.getFocusPoint()) ? request.getFocusPoint().trim() : "";
```

不要再拼：

- `keywordQueries`
- `mustHaveClues`
- `displayQuery`

- [ ] **Step 3: 重写 `resolveLexicalTerms(...)`**

只保留：

```java
LinkedHashSet<String> lexicalTerms = new LinkedHashSet<>();
addLexicalTerms(lexicalTerms, request.getKeywordQueries());
return List.copyOf(lexicalTerms);
```

不要再回退到：

- `displayQuery`
- `focusPoint`

- [ ] **Step 4: 重写 `DashScopeRagRerankService.buildQueryBrief(...)`**

新 brief 只保留：

```java
题型
目标(queryText)
焦点(focusPoint)
关键词(keywordQueries)
目标难度(difficultyHint)
```

不再出现：

- `displayQuery`
- `mustHaveClues`
- `avoidClues`

- [ ] **Step 5: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=RagRetrievalServiceImplTest" test
```

Expected:

- `RagRetrievalServiceImplTest` 通过
- `keywordQueries=[]` 时不会调用 `scrollAsync`
- dense 查询仍然正常进行

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java backend/src/main/java/com/a05/aiinterview/rag/service/impl/DashScopeRagRerankService.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java
git commit -m "refactor: remove legacy brief fields from retrieval pipeline"
```

## Chunk 5: 清理评测夹具与文档口径

### Task 9: 更新评测夹具与评测代码，删除旧字段依赖

**Files:**
- Modify: `backend/src/test/resources/rag-eval/interview-retrieval-cases.json`
- Modify: `backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java`

- [ ] **Step 1: 收缩 fixture 中每条 `retrievalPlans`**

把：

```json
{
  "goal": "...",
  "displayQuery": "...",
  "queryText": "...",
  "keywordHints": [...],
  "difficultyHint": "L2",
  "mustHaveClues": [...],
  "avoidClues": [...]
}
```

改成：

```json
{
  "queryText": "...",
  "keywordHints": [...],
  "difficultyHint": "L2"
}
```

- [ ] **Step 2: 删除 `InterviewRagEvaluationTest` 中所有 clue 级断言**

删除：

- `sample.mustHaveClues()`
- `sample.avoidClues()`
- `retrievalPlan.mustHaveClues()`
- `retrievalPlan.avoidClues()`
- 基于 `mustHaveClues/avoidClues` 的污染率与覆盖率计算

保留：

- `shouldRetrieve`
- `expectedKeywords`
- `expectedFollowUpIds`
- 结构化题型边界检查
- 命中率与 follow-up 检查

- [ ] **Step 3: 改写测试辅助 DTO/record**

fixture record 只保留这类 retrieval 字段：

```java
String queryText,
List<String> keywordHints,
String difficultyHint
```

不要留下空壳字段。

- [ ] **Step 4: 运行测试确认修复**

Run:

```powershell
rtk mvn -q "-Dtest=InterviewRagEvaluationTest" test
```

Expected:

- fixture 成功解析
- 评测代码不再引用已删除字段
- 若环境缺少外部依赖，仅允许因真实集成前提缺失失败，不允许因编译或 JSON schema 失败

- [ ] **Step 5: Commit**

```powershell
git add backend/src/test/resources/rag-eval/interview-retrieval-cases.json backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java
git commit -m "test: remove legacy retrieval fields from rag evaluation harness"
```

### Task 10: 更新中文文档，消灭旧口径

**Files:**
- Modify: `docs/superpowers/reports/2026-04-03-interview-rag-baseline-and-improvement-directions.md`
- Modify: `docs/superpowers/reports/2026-04-06-evaluation-decision-to-question-generation-chain.md`

- [ ] **Step 1: 修改基线文档**

删除或改写以下内容：

- 7 字段 retrieval brief
- 技术钩子作为项目题检索门槛
- `displayQuery / mustHaveClues / avoidClues` 的职责说明

明确新口径：

- AI 只输出 `queryText / keywordHints / difficultyHint`
- `shouldRetrieve` 只由 retrievalPlan 是否存在决定
- 项目题不再走技术钩子门槛

- [ ] **Step 2: 修改链路解剖文档**

逐层改写：

- Prompt 层 7 字段 -> 3 字段
- validator 清洗逻辑 -> 只剩 3 字段
- builder 增加 retrievalPlan 数量与语义校验
- `RagPlanCompiler` 删除技术钩子与旧字段
- `RagRetrievalServiceImpl` / `DashScopeRagRerankService` 删除旧字段消费

- [ ] **Step 3: 人工检查文档是否还残留旧字段名**

Run:

```powershell
rg -n "goal|displayQuery|mustHaveClues|avoidClues|技术钩子" docs/superpowers/reports/2026-04-03-interview-rag-baseline-and-improvement-directions.md docs/superpowers/reports/2026-04-06-evaluation-decision-to-question-generation-chain.md
```

Expected:

- 只允许在“已删除历史逻辑”或“迁移说明”语境中出现
- 不允许仍被写成现行契约

- [ ] **Step 4: Commit**

```powershell
git add docs/superpowers/reports/2026-04-03-interview-rag-baseline-and-improvement-directions.md docs/superpowers/reports/2026-04-06-evaluation-decision-to-question-generation-chain.md
git commit -m "docs: align retrieval brief docs with three-field contract"
```

## Chunk 6: 最终验证与回归

### Task 11: 运行目标测试集，确认没有旧字段残留

**Files:**
- Verify only

- [ ] **Step 1: 运行高相关测试集**

Run:

```powershell
rtk mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest,DecisionExecutionPlanBuilderTest,RagPlanCompilerTest,RagRetrievalServiceImplTest,QuestionStreamServiceBuildInputTest,InterviewRagEvaluationTest" test
```

Expected:

- 编译通过
- 不再出现 `cannot find symbol getDisplayQuery/getMustHaveClues/getAvoidClues` 之类错误
- 不再出现旧 JSON 字段解析失败

- [ ] **Step 2: 全局搜索残留字段**

Run:

```powershell
rg -n "goal|displayQuery|mustHaveClues|avoidClues|TECH_HOOK_TOKENS|hasExplicitProjectTechHook|containsTechnicalHint|preferredDifficultyLevels" backend/src/main backend/src/test docs/superpowers
```

Expected:

- 代码主路径中不应再出现这些标识符
- 文档中若有出现，只能是历史说明，不得作为现行逻辑

- [ ] **Step 3: 如有需要，再跑一次后端核心测试集**

Run:

```powershell
rtk mvn -q "-Dtest=AnswerSubmitServiceDecisionFlowTest,QuestionStreamServiceBuildInputTest,RagPlanCompilerTest,RagRetrievalServiceImplTest" test
```

Expected:

- 决策链路、检索编译、检索服务和出题输入组装能一起通过

- [ ] **Step 4: 最终提交**

```powershell
git add backend/src/main backend/src/test backend/src/test/resources docs/superpowers/reports
git commit -m "refactor: reset retrieval brief contract and remove tech hook gating"
```

## Success Criteria

完成后必须同时满足：

1. `EvaluationDecisionOutput.RetrievalPlan` 只剩 3 个字段
2. Prompt、DTO、validator、builder、compiler、retrieval、rerank、测试和文档全部与 3 字段契约一致
3. `TECH_HOOK_TOKENS` 与相关方法彻底删除
4. `RagRetrievalRequest` 不再保留旧字段尸体
5. `keywordHints=[]` 时 lexical 侧不会偷偷回退造词
6. 全局搜索不再出现旧字段标识符残留

Plan complete and saved to `docs/superpowers/plans/2026-04-06-rag-retrieval-brief-contract-reset.md`. Ready to execute?
