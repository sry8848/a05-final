# RAG DomainCode Metadata Downgrade Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `domainCode` 在 RAG 中彻底降级为纯元数据：保留在 payload、返回结果、审计和展示里，但不再参与请求编译、召回、rerank、硬护栏或项目锚点判断。

**Architecture:** 本次改造不是局部删条件，而是重划边界。`RagRetrievalRequest` 删除 `domainCode`，`RagPlanCompiler` 不再下推它，`RagRetrievalServiceImpl` 只按题型做后置硬护栏，`QdrantHybridCollectionManager` 不再为 `domain_code` 建索引。`domainCode` 继续保留在 `KnowledgeDocument`、Qdrant payload、`RagContext.RetrievedMaterial` 以及入库校验中，明确作为展示元数据存在。

**Tech Stack:** Java 21, Spring Boot 3.2.5, Qdrant Java Client, Spring AI `EmbeddingModel`, DashScope rerank, JUnit 5, Mockito, AssertJ

---

## 文件结构与职责

### 需要修改的代码文件

- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java`
  - 删除 `domainCode` 字段，收紧 DTO 职责。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java`
  - 停止把 `targetDomainCode` 编译进 RAG 请求。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagRetrievalService.java`
  - 更新接口注释，不再宣称请求包含 `domainCode` 维度。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\NoopRagRetrievalService.java`
  - 删除无意义的 `domainCode` 日志输出。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRetrievalServiceImpl.java`
  - 删除请求侧 `domainCode` 日志与全部 `domainCode` 硬护栏逻辑。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridCollectionManager.java`
  - 不再创建 `domain_code` payload index。

### 需要修改的测试文件

- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`
  - 删除对 `domainCode` 请求编译的任何预期。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`
  - 改成断言硬护栏只看题型，不再看 `domainCode`。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\qdrant\QdrantHybridCollectionManagerTest.java`
  - 改成只校验 `question_type` 和 `active` 索引。

### 需要同步的文档

- `D:\a05-cursor\docs\superpowers\reports\2026-04-07-interview-rag-current-implementation-report.md`
  - 改掉把 `domainCode` 写成护栏依据的描述。
- `D:\a05-cursor\docs\superpowers\reports\2026-04-06-qdrant-hybrid-cutover-runbook.md`
  - 如仍保留 `domainCode` 的执行语义口径，统一改掉。
- `D:\a05-cursor\docs\superpowers\plans\2026-04-07-interview-rag-field-split-adjustment.md`
  - 补一句说明：该计划里“`domainCode` 保留给护栏”的假设已被新计划取代，避免文档互相打架。

## Assumptions

- 本计划按最新确认执行：`domainCode` 在 RAG 中是纯元数据，不参与任何执行语义。
- 本计划不新增新的项目锚点机制；项目题暂时只依赖题型和语义排序。
- 本计划不删除 `KnowledgeDocument.domainCode`、Qdrant payload 中的 `domain_code`，也不修改 JSONL 输入格式。

## Chunk 1: 清理请求契约与编译链路

### Task 1: 从 RagRetrievalRequest 中删除 domainCode

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagRetrievalService.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\NoopRagRetrievalService.java`

- [ ] **Step 1: 先修改接口注释与 DTO 预期**

调整这些代码口径：
- `RagRetrievalRequest` 删除 `domainCode`
- `RagRetrievalService` 注释去掉 “包含 domainCode 维度”
- `NoopRagRetrievalService` 日志不再打印 `domainCode`

- [ ] **Step 2: 运行编译相关测试，确认因为字段删除而失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest,RagRetrievalServiceImplTest" test
```

Expected:
- FAIL
- 失败点包括 `request.getDomainCode()` 或 `builder.domainCode(...)` 不再存在

- [ ] **Step 3: 实现 DTO/接口清理**

实现要点：
- 删除 `RagRetrievalRequest.domainCode`
- 调整相关注释和日志
- 不动 `RagContext.RetrievedMaterial.domainCode`

- [ ] **Step 4: 复跑定向测试，确认失败点已收缩到编译器与服务实现**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest,RagRetrievalServiceImplTest" test
```

Expected:
- 仍可能 FAIL
- 但失败点已收缩到 `RagPlanCompiler` 和 `RagRetrievalServiceImpl` 仍引用旧字段

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java backend/src/main/java/com/a05/aiinterview/rag/service/RagRetrievalService.java backend/src/main/java/com/a05/aiinterview/rag/service/impl/NoopRagRetrievalService.java
git commit -m "refactor: remove domain code from rag request contract"
```

### Task 2: 停止 RagPlanCompiler 下推 domainCode

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`

- [ ] **Step 1: 先改测试，锁定新的编译契约**

在 `RagPlanCompilerTest` 中明确：
- `shouldRetrieve`、`questionType`、`queryText`、`denseQueryText`、`sparseQueryText` 继续是核心契约
- 不再断言 `domainCode` 会出现在请求里
- `projectName` 是否保留，不代表项目锚点已经生效

- [ ] **Step 2: 运行 compiler 测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest" test
```

Expected:
- FAIL
- `RagPlanCompiler` 仍在 `.domainCode(...)`

- [ ] **Step 3: 实现最小编译收缩**

在 `RagPlanCompiler` 中：
- 删除 `.domainCode(resolveDomainCode(...))`
- 删除只为 RAG 请求服务的 `resolveDomainCode(...)`
- 保持现有 `denseQueryText` / `sparseQueryText` 编译规则不变

- [ ] **Step 4: 复跑 compiler 测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest" test
```

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java
git commit -m "refactor: stop compiling domain code into rag requests"
```

## Chunk 2: 清理运行时语义与索引

### Task 3: 删除 RagRetrievalServiceImpl 中的 domainCode 护栏语义

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRetrievalServiceImpl.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`

- [ ] **Step 1: 先改失败测试，锁定“只按题型护栏”**

在 `RagRetrievalServiceImplTest` 中补强这些断言：
- 行为题只要求保留 `BEHAVIORAL`
- 项目题只要求保留 `PROJECT`
- 其他题型只要求题型一致
- `domain_code` 即使出现在 payload，也不再影响最终结果

- [ ] **Step 2: 运行检索服务测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagRetrievalServiceImplTest" test
```

Expected:
- FAIL
- 当前 `RagRetrievalServiceImpl` 仍调用 `request.getDomainCode()` 或仍按 `actualDomainCode` 判定

- [ ] **Step 3: 实现硬护栏去 domainCode**

在 `RagRetrievalServiceImpl` 中：
- 日志不再打印请求侧 `domainCode`
- `applyHardGuardrails(...)` 只传 `expectedQuestionType`
- `matchesHardGuardrails(...)` 删除所有 `expectedDomainCode / actualDomainCode` 逻辑
- 行为题不再要求 `candidate.domainCode` 为空

- [ ] **Step 4: 复跑测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=RagRetrievalServiceImplTest" test
```

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java
git commit -m "refactor: remove domain code from rag guardrails"
```

### Task 4: 去掉 domain_code payload index，但保留 payload 字段

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridCollectionManager.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\qdrant\QdrantHybridCollectionManagerTest.java`

- [ ] **Step 1: 先改测试，锁定新的 schema 预期**

在 `QdrantHybridCollectionManagerTest` 中明确：
- 仍创建 `question_type` keyword index
- 仍创建 `active` bool index
- 不再创建 `domain_code` keyword index

- [ ] **Step 2: 运行 schema 测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=QdrantHybridCollectionManagerTest" test
```

Expected:
- FAIL
- 当前 manager 仍创建 `domain_code` index

- [ ] **Step 3: 实现索引收缩**

在 `QdrantHybridCollectionManager` 中：
- 删除 `createKeywordPayloadIndex(collectionName, "domain_code")`
- 不改 `QdrantHybridPointMapper` 的 payload 写入
- 不改 `QdrantHybridQueryExecutor` 对 payload 的读取

- [ ] **Step 4: 复跑测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=QdrantHybridCollectionManagerTest" test
```

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/qdrant/QdrantHybridCollectionManager.java backend/src/test/java/com/a05/aiinterview/rag/qdrant/QdrantHybridCollectionManagerTest.java
git commit -m "refactor: drop domain payload index from rag schema"
```

## Chunk 3: 文档同步与回归验证

### Task 5: 统一文档口径并做残留验证

**Files:**
- Modify: `D:\a05-cursor\docs\superpowers\reports\2026-04-07-interview-rag-current-implementation-report.md`
- Optional Modify: `D:\a05-cursor\docs\superpowers\reports\2026-04-06-qdrant-hybrid-cutover-runbook.md`
- Modify: `D:\a05-cursor\docs\superpowers\plans\2026-04-07-interview-rag-field-split-adjustment.md`

- [ ] **Step 1: 更新当前实现报告**

把报告里的 RAG 口径统一为：
- `domainCode` 仅用于展示和审计
- rerank 仅基于查询文本与题卡正文
- 硬护栏只按题型，不按 `domainCode`

- [ ] **Step 2: 如有需要，更新 runbook 与旧计划**

检查并修正：
- runbook 中任何把 `domainCode` 写成召回、排序或护栏条件的内容
- field-split 计划中“保留给护栏”的旧假设

- [ ] **Step 3: 跑定向回归测试**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest,RagRetrievalServiceImplTest,QdrantHybridCollectionManagerTest" test
```

Expected:
- PASS

- [ ] **Step 4: 做残留搜索，确认 RAG 执行层不再引用请求侧 domainCode**

Run:

```powershell
rtk rg -n "request\\.getDomainCode\\(|\\.domainCode\\(|expectedDomainCode|actualDomainCode|createKeywordPayloadIndex\\(collectionName, \"domain_code\"" D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag
```

Expected:
- `RagRetrievalRequest` 不再有 `.domainCode(...)`
- `RagRetrievalServiceImpl` 不再有 `request.getDomainCode()`、`expectedDomainCode`、`actualDomainCode`
- `QdrantHybridCollectionManager` 不再创建 `domain_code` index
- 允许 `domainCode/domain_code` 继续出现在：
  - `KnowledgeDocument`
  - `KnowledgeJsonlImportService`
  - `QdrantHybridPointMapper`
  - `QdrantHybridQueryExecutor` payload 读取
  - `RagContext.RetrievedMaterial`

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/reports/2026-04-07-interview-rag-current-implementation-report.md docs/superpowers/reports/2026-04-06-qdrant-hybrid-cutover-runbook.md docs/superpowers/plans/2026-04-07-interview-rag-field-split-adjustment.md
git commit -m "docs: align rag domain code semantics with metadata-only design"
```

## Execution Notes

- 这份计划与 [2026-04-07-rag-domaincode-metadata-design.md](D:/a05-cursor/docs/superpowers/specs/2026-04-07-rag-domaincode-metadata-design.md) 配套使用。
- 当前计划只处理 RAG 包内的 `domainCode` 语义，不改动 `AnswerSubmitService`、`QuestionStreamService` 等上游业务使用 `domainCode` 的场景。
- 如果后续需要“项目锚点”，必须另起一份设计与计划，新增显式字段，不允许回滚到 `domainCode` 方案。
