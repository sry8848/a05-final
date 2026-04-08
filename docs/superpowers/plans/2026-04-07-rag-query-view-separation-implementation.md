# RAG Query View Separation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按已确认的 P0/P1 方案重构面试 RAG 查询输入边界：`questionType` 与 `difficultyHint` 退出 rerank，`difficultyHint` 变成可开关的相邻一级 difficulty window 硬过滤；上游继续保留 `queryText + keywordHints`，但 `queryText` 明确为独立自然语言语义查询，dense/rerank 只使用它，sparse 只使用 `keywordHints`，为空则跳过 sparse。

**Architecture:** 本次改造不重做整条 RAG 链路，而是在既有 `evaluation-decision -> RagPlanCompiler -> QdrantHybridQueryExecutor -> DashScopeRagRerankService -> RagContext` 链路中，明确拆出“语义视图”“词法视图”“业务过滤”三种职责。实现上新增一个小型 `DifficultyWindowResolver` 负责层级扩窗，一个可测试的 `RagRerankInputBuilder` 负责 rerank query 构造；其余逻辑在现有组件内就地收口，避免引入无谓抽象。

**Tech Stack:** Java 21, Spring Boot 3.2.5, Spring AI `EmbeddingModel`, Qdrant Java Client, DashScope Rerank API, JUnit 5, Mockito, AssertJ, Markdown Prompt Templates

---

## 文件结构与职责

### 需要创建的代码文件

- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\DifficultyWindowResolver.java`
  - 将 `difficultyHint` 解析为相邻一级窗口，例如 `L2 -> [L1,L2,L3]`。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\qdrant\DifficultyWindowResolverTest.java`
  - 锁定扩窗规则与边界层级。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRerankInputBuilder.java`
  - 负责构造 rerank query/document 文本，避免在服务类里硬编码标签拼接。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRerankInputBuilderTest.java`
  - 锁定 rerank query 只保留语义视图，不再混入 `questionType`、`difficultyHint`、`keywordHints`、`sparseQueryText`。

### 需要修改的代码文件

- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\config\RagProperties.java`
  - 新增 `difficultyWindowEnabled` 配置属性。
- `D:\a05-cursor\backend\src\main\resources\application.yml`
  - 新增 `rag.difficulty-window-enabled` 配置项。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridQueryExecutor.java`
  - 在 dense/sparse 两路统一追加 difficulty window filter。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagContext.java`
  - 在 `RetrievalAudit` 中增加 difficulty window 审计字段，并写入 `toAuditMap()`。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRetrievalServiceImpl.java`
  - 填充新的审计字段；保持 0 命中时不做 difficulty 回退。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\DashScopeRagRerankService.java`
  - 接入 `RagRerankInputBuilder`，移除 `questionType` 与 `difficultyHint` 的 query 注入。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java`
  - 主要更新注释与编译契约表达，确保执行视图语义与新设计一致。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\dto\EvaluationDecisionOutput.java`
  - 更新 `RetrievalPlan` 字段语义注释，明确 `queryText` 是自然语言语义查询，`keywordHints` 是术语锚点。
- `D:\a05-cursor\backend\src\main\resources\prompts\evaluation-decision.md`
  - 提示词中把 `queryText` 定义成独立完整自然语言 query，并把 `difficultyHint` 说明改成后端按相邻一级窗口做硬过滤。

### 需要修改的测试文件

- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\ai\contract\EvaluationDecisionContractTest.java`
  - 将 retrieval plan 样例改成自然语言 `queryText`。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\ai\prompt\PromptTemplateCoverageTest.java`
  - 锁定 prompt 中新的 queryText / keywordHints / difficultyHint 语义说明。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`
  - 锁定 dense 仅用自然语言 `queryText`，sparse 仅用 `keywordHints`，为空则跳过。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`
  - 锁定 difficulty filter 在两路召回都生效、开关关闭时不生效、`keywordHints=[]` 时只执行 dense。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\RagContextTest.java`
  - 锁定新的 difficulty window 审计字段。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\interview\service\QuestionStreamServiceBuildInputTest.java`
  - 若 retrievalAudit 透传字段断言是白名单式，需要补上新的 difficulty audit 字段。

### 需要同步的文档

- `D:\a05-cursor\docs\superpowers\reports\2026-04-07-interview-rag-current-implementation-report.md`
  - 更新现状说明，明确 P0/P1 完成后的真实边界。
- `D:\a05-cursor\docs\rag-technical-implementation.md`
  - 清除旧的 lexical 预过滤口径与“难度只是软提示”的表述，避免文档继续传播历史逻辑。

## Assumptions

- 当前工作区已有未提交修改，执行计划时必须只在本计划涉及文件上工作，不得回滚无关改动。
- `difficultyHint` 的业务定义已固定：
  - 开启时按相邻一级扩窗做硬过滤
  - 关闭时完全不参与过滤
  - 0 命中时不回退重查
- P1 已固定：
  - `queryText` 必须是自然语言语义查询
  - `keywordHints` 只服务 sparse
  - `keywordHints` 为空时不查 sparse
- 本计划不引入新的 query rewrite 服务，不用大模型在编译期二次改写 query。

## Chunk 1: 先用测试锁定 P0/P1 新契约

### Task 1: 锁定上游 retrieval plan 语义与 prompt 口径

**Files:**
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\ai\contract\EvaluationDecisionContractTest.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\ai\prompt\PromptTemplateCoverageTest.java`
- Modify later: `D:\a05-cursor\backend\src\main\resources\prompts\evaluation-decision.md`

- [ ] **Step 1: 先把 `EvaluationDecisionContractTest` 的 retrieval 样例改成自然语言 query**

把现有样例：

```java
"queryText": "Seata AT 模式 本地事务边界 分支事务注册"
```

改成这类自然语言形式：

```java
"queryText": "寻找考察 Seata AT 模式下本地事务边界与分支事务注册机制的题目。"
```

并保留：

```java
"keywordHints": ["Seata", "AT", "分支事务注册"]
```

- [ ] **Step 2: 在 `PromptTemplateCoverageTest` 中增加新的 prompt 断言**

在 `renderEvaluationDecision_shouldLoad()` 中新增断言，要求 system prompt：

```java
assertThat(rendered.getSystemPrompt())
        .contains("`queryText`")
        .contains("独立")
        .contains("完整")
        .contains("自然语言")
        .contains("`keywordHints`")
        .contains("术语锚点")
        .contains("相邻一级")
        .doesNotContain("软约束");
```

- [ ] **Step 3: 运行定向测试，确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest" test
```

Expected:
- `EvaluationDecisionContractTest` 可能仍 PASS
- `PromptTemplateCoverageTest` 应 FAIL，因为当前 prompt 还把 `difficultyHint` 写成软约束，且未明确要求自然语言 query

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java
git commit -m "test: lock retrieval plan semantics to natural query and sparse hints"
```

### Task 2: 锁定编译视图、rerank 输入和 difficulty 审计字段

**Files:**
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`
- Create: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRerankInputBuilderTest.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\RagContextTest.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`

- [ ] **Step 1: 修改 `RagPlanCompilerTest`，把所有 `queryText` 样例换成自然语言句**

例如把：

```java
"Redis 缓存穿透的原理与防护"
```

换成：

```java
"寻找考察 Redis 缓存穿透原理与防护方案的题目。"
```

并保留断言：

```java
assertThat(request.getDenseQueryText()).isEqualTo(request.getQueryText());
assertThat(request.getSparseQueryText()).isEqualTo("Redis 缓存穿透 布隆过滤器");
```

- [ ] **Step 2: 新建 `RagRerankInputBuilderTest` 锁定 rerank query 收缩**

至少覆盖两条用例：

```java
assertThat(builder.buildQueryText(request))
        .contains("寻找考察 Redis 缓存穿透防护方案的题目")
        .doesNotContain("题型")
        .doesNotContain("目标难度")
        .doesNotContain("Redis；缓存穿透")   // 不把 keywordHints 标签化拼进去
        .doesNotContain("术语查询");
```

和：

```java
assertThat(builder.buildQueryText(requestWithBlankFocus))
        .isEqualTo("寻找考察 Redis 缓存穿透防护方案的题目。");
```

- [ ] **Step 3: 在 `RagContextTest` 中先加新审计字段断言**

新增断言：

```java
assertThat(retrievalAudit)
        .containsEntry("difficultyWindowApplied", true)
        .containsEntry("difficultyWindowValues", List.of("L1", "L2", "L3"));
```

- [ ] **Step 4: 在 `RagRetrievalServiceImplTest` 中增加 difficulty window 相关失败用例**

增加三类断言：

1. `difficultyWindowEnabled=true` 且 `L2` 时，dense/sparse 的 filter 文本都包含 `L1`、`L2`、`L3`
2. `difficultyWindowEnabled=false` 时，query filter 中不出现 `difficulty`
3. `keywordQueries=[]` 时仍只执行 dense，一次都不额外执行 sparse

- [ ] **Step 5: 运行测试，确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest,RagRerankInputBuilderTest,RagContextTest,RagRetrievalServiceImplTest" test
```

Expected:
- `RagRerankInputBuilderTest` 编译失败或类不存在
- `RagContextTest` FAIL，因为还没有 difficulty audit 字段
- `RagRetrievalServiceImplTest` FAIL，因为还没有 difficulty filter 和开关

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java backend/src/test/java/com/a05/aiinterview/rag/dto/RagContextTest.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRerankInputBuilderTest.java
git commit -m "test: lock rag query view separation behavior"
```

## Chunk 2: 实现 P0 difficulty window 配置、过滤与审计

### Task 3: 引入全局 difficulty window 配置与解析器

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\config\RagProperties.java`
- Modify: `D:\a05-cursor\backend\src\main\resources\application.yml`
- Create: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\DifficultyWindowResolver.java`
- Create: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\qdrant\DifficultyWindowResolverTest.java`

- [ ] **Step 1: 先写 `DifficultyWindowResolverTest`**

覆盖 5 条主规则和 2 条边界：

```java
assertThat(resolver.resolve("L1")).containsExactly("L1", "L2");
assertThat(resolver.resolve("L2")).containsExactly("L1", "L2", "L3");
assertThat(resolver.resolve("L3")).containsExactly("L2", "L3", "L4");
assertThat(resolver.resolve("L4")).containsExactly("L3", "L4", "L5");
assertThat(resolver.resolve("L5")).containsExactly("L4", "L5");
assertThat(resolver.resolve("")).isEmpty();
assertThat(resolver.resolve("UNKNOWN")).isEmpty();
```

- [ ] **Step 2: 运行新测试，确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=DifficultyWindowResolverTest" test
```

Expected:
- FAIL，类不存在

- [ ] **Step 3: 写最小实现并接入配置**

实现要求：
- `RagProperties` 增加 `difficultyWindowEnabled`，默认 `false`
- `application.yml` 增加：

```yaml
rag:
  difficulty-window-enabled: ${RAG_DIFFICULTY_WINDOW_ENABLED:false}
```

- `DifficultyWindowResolver` 返回有序 `List<String>`，不要返回 `Set`

- [ ] **Step 4: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=DifficultyWindowResolverTest" test
```

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/config/RagProperties.java backend/src/main/resources/application.yml backend/src/main/java/com/a05/aiinterview/rag/qdrant/DifficultyWindowResolver.java backend/src/test/java/com/a05/aiinterview/rag/qdrant/DifficultyWindowResolverTest.java
git commit -m "feat: add rag difficulty window configuration"
```

### Task 4: 在 Qdrant 召回与审计里落地 difficulty window

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridQueryExecutor.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagContext.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRetrievalServiceImpl.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\RagContextTest.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`
- Optional Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\interview\service\QuestionStreamServiceBuildInputTest.java`

- [ ] **Step 1: 先让 `RagRetrievalServiceImplTest` 失败得更精确**

把对 query filter 的断言写成显式文本匹配：

```java
assertThat(denseRequest.getFilter().toString())
        .contains("question_type")
        .contains("difficulty")
        .contains("L1")
        .contains("L2")
        .contains("L3");
```

以及：

```java
assertThat(context.getRetrievalAudit())
        .extracting(
                RagContext.RetrievalAudit::isDifficultyWindowApplied,
                RagContext.RetrievalAudit::getDifficultyWindowValues
        )
        .containsExactly(true, List.of("L1", "L2", "L3"));
```

- [ ] **Step 2: 运行定向测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagContextTest,RagRetrievalServiceImplTest,QuestionStreamServiceBuildInputTest" test
```

Expected:
- FAIL，因为 `RetrievalAudit` 还没有新增字段
- FAIL，因为 `QdrantHybridQueryExecutor` 尚未追加 `difficulty` filter

- [ ] **Step 3: 最小实现召回过滤**

实现要点：
- `QdrantHybridQueryExecutor.buildBaseFilter()` 在开关开启且 `difficultyHint` 非空时，追加 `difficulty` 条件
- 过滤值来自 `DifficultyWindowResolver`
- dense/sparse 共用同一个 filter 构造逻辑

实现形态建议：

```java
List<String> difficultyWindow = resolveDifficultyWindow(request);
if (!difficultyWindow.isEmpty()) {
    must.add(ConditionFactory.matchKeywords("difficulty", difficultyWindow));
}
```

如果 `matchKeywords(...)` 在当前 client API 中不适配，就改成等价 OR 条件；不要为了省事把多个层级拼成字符串比较。

- [ ] **Step 4: 实现审计字段**

在 `RagContext.RetrievalAudit` 中新增：

```java
private boolean difficultyWindowApplied;
@Builder.Default
private List<String> difficultyWindowValues = new ArrayList<>();
```

并更新：
- `RetrievalAudit.empty(...)`
- `RagContext.toAuditMap()`
- `RagRetrievalServiceImpl` 内所有 `RetrievalAudit.builder()` 填充点

- [ ] **Step 5: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=DifficultyWindowResolverTest,RagContextTest,RagRetrievalServiceImplTest,QuestionStreamServiceBuildInputTest" test
```

Expected:
- PASS
- 启用开关时 audit 能看到 difficulty window
- 关闭开关时 filter 不包含 `difficulty`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/qdrant/QdrantHybridQueryExecutor.java backend/src/main/java/com/a05/aiinterview/rag/dto/RagContext.java backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java backend/src/test/java/com/a05/aiinterview/rag/dto/RagContextTest.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java
git commit -m "feat: apply difficulty window filter across hybrid recall"
```

## Chunk 3: 实现 P1 查询视图分离与 rerank 输入收缩

### Task 5: 抽出可测试的 rerank 输入构造器

**Files:**
- Create: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRerankInputBuilder.java`
- Create: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRerankInputBuilderTest.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\DashScopeRagRerankService.java`

- [ ] **Step 1: 先写 builder 测试**

至少锁定以下行为：

1. `buildQueryText(request)` 只使用 `queryText`，可选补一个 `focusPoint` 辅助语义，但不能替代 `queryText`
2. query 文本中不出现：
   - `题型`
   - `目标难度`
   - `术语查询`
   - `关键词`
3. `buildDocumentText(candidate)` 暂时维持现有文档字段顺序，不在本次计划中扩展新逻辑

测试示例：

```java
assertThat(builder.buildQueryText(request))
        .isEqualTo("寻找考察 Redis 缓存穿透防护方案的题目，重点包含布隆过滤器与空值缓存。");
```

- [ ] **Step 2: 运行测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagRerankInputBuilderTest" test
```

Expected:
- FAIL，类不存在

- [ ] **Step 3: 最小实现 builder 并接入服务**

实现要求：
- `DashScopeRagRerankService` 不再自己拼 query brief
- query 侧仅调用 `builder.buildQueryText(request)`
- document 侧调用 `builder.buildDocumentText(candidate)`
- 不改 DashScope API 调用协议

- [ ] **Step 4: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=RagRerankInputBuilderTest,RagRetrievalServiceImplTest" test
```

Expected:
- PASS
- `RagRetrievalServiceImplTest` 继续通过，说明 rerank 服务改造未破坏主链路

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRerankInputBuilder.java backend/src/main/java/com/a05/aiinterview/rag/service/impl/DashScopeRagRerankService.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRerankInputBuilderTest.java
git commit -m "refactor: separate rerank semantic query input from retrieval metadata"
```

### Task 6: 更新 prompt、DTO 注释与 compiler 契约文案

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\resources\prompts\evaluation-decision.md`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\ai\dto\EvaluationDecisionOutput.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\ai\prompt\PromptTemplateCoverageTest.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\ai\contract\EvaluationDecisionContractTest.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`

- [ ] **Step 1: 修改 prompt，明确上游生成要求**

把 `evaluation-decision.md` 中对 `retrievalPlans` 的说明改成：

- `queryText`：独立、完整、自然语言的检索句
- `keywordHints`：术语锚点，只用于 sparse/BM25
- `difficultyHint`：后端按相邻一级窗口做硬过滤

并删除“属于软约束”的旧口径。

- [ ] **Step 2: 更新 DTO / compiler 注释**

至少修改这些注释：
- `EvaluationDecisionOutput.RetrievalPlan`
- `RagRetrievalRequest.queryText`
- `RagRetrievalRequest.sparseQueryText`
- `RagPlanCompiler` 类注释与 `compile(...)` 方法注释

目标是让后来读代码的人一眼看出：
- dense/rerank 只使用语义 query
- sparse 只使用 keyword hints
- 空 hints 直接跳过 sparse

- [ ] **Step 3: 复跑相关测试**

Run:

```powershell
rtk mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest,RagPlanCompilerTest" test
```

Expected:
- PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/prompts/evaluation-decision.md backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java
git commit -m "docs: align retrieval plan semantics with query view separation"
```

## Chunk 4: 文档同步与最终验证

> 当前会话已确认：`RagRerankInputBuilder` 保留对 `denseQueryText` 的 fallback，不作为 chunk4 的阻塞项。chunk4 的目标是同步文档口径、验证现行实现边界，并避免被历史文档或无关 prompt/测试误报。

### Task 7: 更新中文文档口径，清除历史描述

**Files:**
- Modify: `D:\a05-cursor\docs\superpowers\reports\2026-04-07-interview-rag-current-implementation-report.md`
- Modify: `D:\a05-cursor\docs\rag-technical-implementation.md`

- [ ] **Step 1: 按现行实现重写当前实现报告的关键章节**

同步以下新口径：
- rerank query 不再包含 `questionType` / `difficultyHint`
- difficulty filter 在 dense/sparse 两路召回前统一生效
- `queryText` 是自然语言语义视图
- `keywordHints` 只用于 sparse
- 当前审计字段包含 difficulty window 与 dense/sparse/fusion/rerank 各阶段候选信息
- 当前实现接受保留 `denseQueryText` fallback，但这不是本次文档要继续扩散的推荐模式

- [ ] **Step 2: 按现行实现重写旧技术文档中的过时章节**

重点删掉或改写：
- lexical 预过滤旧描述
- “difficultyHint 只是软提示”的旧表述
- dense/rerank 混吃关键词标签的旧口径
- `VectorStore.similaritySearch` / 单路 dense 召回等过时实现描述
- 已不存在的 `domainCode` 检索过滤、`lexicalCandidateCount` 等旧审计字段

- [ ] **Step 3: 做目标文档残留搜索**

Run:

```powershell
rtk rg -n "lexical 预过滤|VectorStore\.similaritySearch|术语查询（sparseQueryText）|目标难度（difficultyHint）|软提示|软约束" D:\a05-cursor\docs\superpowers\reports\2026-04-07-interview-rag-current-implementation-report.md D:\a05-cursor\docs\rag-technical-implementation.md
rtk rg -n "题型：|目标难度：|术语查询|关键词：" D:\a05-cursor\backend\src\main\resources\prompts\evaluation-decision.md
```

Expected:
- 两份目标文档不再把旧链路写成现行实现
- `evaluation-decision.md` 不再出现旧的 rerank query 标签拼接口径
- 历史 plans/specs/reports 不在这一轮判定范围内，不作为 chunk4 失败条件

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/reports/2026-04-07-interview-rag-current-implementation-report.md docs/rag-technical-implementation.md
git commit -m "docs: reflect rag query view separation and difficulty filtering"
```

### Task 8: 跑最终高相关测试集并做残留扫描

**Files:**
- Verify only

- [ ] **Step 1: 跑高相关测试集**

Run:

```powershell
rtk mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest,DifficultyWindowResolverTest,RagPlanCompilerTest,RagRerankInputBuilderTest,RagContextTest,RagRetrievalServiceImplTest,QuestionStreamServiceBuildInputTest" test
```

Expected:
- PASS

- [ ] **Step 2: 全局搜索残留口径**

Run:

```powershell
rtk rg -n "题型：|目标难度：|术语查询|关键词：|软约束|displayQuery|mustHaveClues|avoidClues" D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag D:\a05-cursor\backend\src\main\resources\prompts\evaluation-decision.md D:\a05-cursor\docs\superpowers\reports\2026-04-07-interview-rag-current-implementation-report.md D:\a05-cursor\docs\rag-technical-implementation.md
```

Expected:
- 现行主代码路径不再存在 `题型/目标难度/术语查询/关键词` 这类 rerank query 标签拼接
- 不再把 `difficultyHint` 写成软约束
- 旧字段 `displayQuery/mustHaveClues/avoidClues` 不应在现行主代码与本次同步文档中重新出现
- 其他 prompt、历史计划文档、测试断言文本不在这一轮失败判定范围内

- [ ] **Step 3: 如需补充，再跑主链路测试**

Run:

```powershell
rtk mvn -q "-Dtest=DecisionExecutionPlanBuilderTest,QuestionStreamServiceBuildInputTest,AnswerSubmitServiceDecisionFlowTest" test
```

Expected:
- PASS
- 决策链路与出题输入组装未被本次改造破坏

- [ ] **Step 4: 如有新增改动，再做最终提交**

```bash
git add docs/superpowers/reports/2026-04-07-interview-rag-current-implementation-report.md docs/rag-technical-implementation.md
git commit -m "docs: finalize rag query view separation documentation"
```

## Success Criteria

完成后必须同时满足：

1. rerank query 不再包含 `questionType`
2. rerank query 不再包含 `difficultyHint`
3. rerank query 不再包含 `keywordHints` 或 `sparseQueryText`
4. `queryText` 在 prompt、DTO、测试和注释中都被定义为自然语言语义查询
5. `keywordHints` 在 prompt、DTO、测试和注释中都被定义为 sparse 术语锚点
6. `keywordHints=[]` 时 sparse 支路显式跳过，不做回退造词
7. `difficultyWindowEnabled=true` 时 dense 和 sparse 两路都加 difficulty window filter
8. `difficultyWindowEnabled=false` 时两路都不加 difficulty filter
9. `difficultyHint` 命中为空时不发生关闭过滤后的二次召回
10. 审计中能看到本次是否应用了 difficulty window 以及实际窗口值

## Execution Notes

- 这份计划与 [2026-04-07-rag-query-view-separation-design.md](D:/a05-cursor/docs/superpowers/specs/2026-04-07-rag-query-view-separation-design.md) 配套使用。
- 当前会话未获用户授权使用子代理，因此这里仅生成执行计划，不做子代理评审循环。
- 执行时如发现现有脏工作区改动与本计划直接冲突，必须先停下并向用户报告冲突文件与原因。

Plan complete and saved to `docs/superpowers/plans/2026-04-07-rag-query-view-separation-implementation.md`. Ready to execute?
