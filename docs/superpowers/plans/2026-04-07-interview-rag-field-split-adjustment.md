# Interview RAG Field Split Adjustment Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 hybrid 检索字段职责收缩为 `dense=questionText+intentConcept`、`sparse=questionText+keywords+scoringKeyPoints`，并移除 `domainCode` 作为 query filter 的参与方式。

**Architecture:** 保持现有 `dense recall -> sparse recall -> RRF -> rerank -> hard guardrails` 主链路不变，只调整题卡的 dense/sparse 表示、compiler 生成的查询文本，以及 query executor 的过滤口径。`domainCode` 继续作为 payload 与下游返回字段存在，但不再进入 Qdrant 查询 filter。关于 `domainCode` 在护栏与请求契约中的进一步降级，现行口径已由 `2026-04-07-rag-domaincode-metadata-downgrade-implementation.md` 取代。`referenceContext`、`scoringPitfalls` 保留给 rerank 和最终返回，不再进入主检索表示。

**Tech Stack:** Java 21, Spring Boot 3.2.5, Spring AI `EmbeddingModel`, Qdrant Java Client, Qdrant sparse/BM25, JUnit 5, Mockito, AssertJ

---

## 文件结构与职责

### 需要修改的文件

- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\KnowledgeDocument.java`
  - 收紧题卡的 dense/sparse 文本构造规则。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java`
  - 让 compiler 生成符合新字段职责的 `denseQueryText` 与 `sparseQueryText`。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridQueryExecutor.java`
  - 移除 `domainCode` 的 query filter，仅保留 `active` 与 `questionType`。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java`
  - 同步注释口径，明确 `domainCode` 暂不作为 query filter。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\KnowledgeDocumentTest.java`
  - 改成断言新的 dense/sparse 构造边界。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`
  - 改成断言新的 `denseQueryText` / `sparseQueryText`。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`
  - 改成断言 query executor 不再带 `domainCode` filter，且新 query 文本能正确流入 dense/sparse 分支。

### 可能需要同步注释/文档的文件

- `D:\a05-cursor\docs\superpowers\reports\2026-04-06-qdrant-hybrid-cutover-runbook.md`
  - 如果文档里把 `domainCode` 写成现行 query filter，需要同步改为“当前仅保留 questionType filter，domainCode 暂不参与召回过滤”。

## Assumptions

- 本计划完成的范围是字段拆分与召回 filter 收缩。
- `domainCode` 在 RAG 中彻底降级为纯元数据的现行口径，已由 `2026-04-07-rag-domaincode-metadata-downgrade-implementation.md` 接管；本文件中的旧“保留给护栏”假设不再有效。

## Chunk 1: 收紧题卡检索表示

### Task 1: 用测试锁定新的 dense/sparse 字段职责

**Files:**
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\KnowledgeDocumentTest.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\KnowledgeDocument.java`

- [ ] **Step 1: 先改失败测试，固定新的字段边界**

在 `KnowledgeDocumentTest` 里补充并收紧这些断言：
- `toDenseRetrievalText()` 只包含 `questionText` 与 `intentConcept`
- `toDenseRetrievalText()` 不再包含 `referenceContext`、`scoringKeyPoints`、`keywords`
- `toSparseRetrievalText()` 包含 `questionText`、`keywords`、`scoringKeyPoints`
- `toSparseRetrievalText()` 不再包含 `referenceContext` 与 `intentConcept`

- [ ] **Step 2: 运行文本构造测试，确认先失败**

Run:

```powershell
rtk mvn -q "-Dtest=KnowledgeDocumentTest" test
```

Expected:
- FAIL
- 当前实现仍把 `referenceContext`、`keywords` 等字段混进 dense，或把 `intentConcept` 混进 sparse

- [ ] **Step 3: 实现最小字段收缩**

在 `KnowledgeDocument` 中：
- `toDenseRetrievalText()` 改成只拼 `questionText`、`intentConcept`
- `toSparseRetrievalText()` 改成只拼 `questionText`、`scoringKeyPoints`、`keywords`
- 删除 sparse 里对 `intentConcept` 的参与
- 不把 `referenceContext` 放入任何主检索表示

- [ ] **Step 4: 复跑测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=KnowledgeDocumentTest" test
```

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/dto/KnowledgeDocument.java backend/src/test/java/com/a05/aiinterview/rag/dto/KnowledgeDocumentTest.java
git commit -m "refactor: tighten rag dense sparse document fields"
```

## Chunk 2: 收紧 compiler 输出并移除 domainCode query filter

### Task 2: 重写 compiler 的 dense/sparse query 生成口径

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`

- [ ] **Step 1: 先改失败测试，锁定 query 文本职责**

在 `RagPlanCompilerTest` 中改成断言：
- `denseQueryText` 只等于 `queryText`
- `sparseQueryText` 只来自 `keywordHints`
- `focusPoint` 不能偷偷补进 sparse
- `keywordHints=[]` 时 `sparseQueryText` 允许为空
- 不对 `domainCode` 的 query filter 语义做任何断言

- [ ] **Step 2: 运行 compiler 测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest" test
```

Expected:
- FAIL
- 当前断言与新字段职责不完全一致，或注释仍把 `domainCode` 写成 filter 输入

- [ ] **Step 3: 实现 compiler 与 request 注释修正**

实现要点：
- 保持 `denseQueryText <- queryText`
- 保持 `sparseQueryText <- keywordHints join`
- `RagRetrievalRequest` 注释改成：`domainCode` 当前仅作上下文/护栏字段，不作为 query filter 的硬约束

- [ ] **Step 4: 复跑 compiler 测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest" test
```

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java
git commit -m "refactor: align rag query texts with new field split"
```

### Task 3: 让召回 filter 暂时退出 domainCode

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridQueryExecutor.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`

- [ ] **Step 1: 先改失败测试，锁定 filter 行为**

在 `RagRetrievalServiceImplTest` 中补充并收紧这些断言：
- dense query 的 filter 只包含 `active` 和 `question_type`
- sparse query 的 filter 只包含 `active` 和 `question_type`
- 即使 request 带了 `domainCode=redis`，query executor 也不再把它编译进 must filter

- [ ] **Step 2: 运行检索测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagRetrievalServiceImplTest" test
```

Expected:
- FAIL
- 当前 `QdrantHybridQueryExecutor.buildBaseFilter()` 仍会追加 `domain_code`

- [ ] **Step 3: 实现最小 filter 调整**

在 `QdrantHybridQueryExecutor` 中：
- 删除 `domain_code` 的 `must` 条件
- 保留 `active=true`
- 保留 `question_type`
- 不改 payload 映射，不改 `SearchHit` 与最终返回结构

- [ ] **Step 4: 复跑检索测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=RagRetrievalServiceImplTest" test
```

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/qdrant/QdrantHybridQueryExecutor.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java
git commit -m "refactor: remove domain filter from rag recall queries"
```

## Chunk 3: 回归验证与文档同步

### Task 4: 做定向回归并同步当前口径

**Files:**
- Verify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\KnowledgeDocumentTest.java`
- Verify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`
- Verify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`
- Optional Modify: `D:\a05-cursor\docs\superpowers\reports\2026-04-06-qdrant-hybrid-cutover-runbook.md`

- [ ] **Step 1: 跑字段收缩相关核心测试**

Run:

```powershell
rtk mvn -q "-Dtest=KnowledgeDocumentTest,RagPlanCompilerTest,RagRetrievalServiceImplTest" test
```

Expected:
- PASS

- [ ] **Step 2: 做残留搜索，确认旧字段职责已消失**

Run:

```powershell
rtk rg -n "referenceContext|intentConcept|domain_code" D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag
```

Expected:
- `referenceContext` 不再出现在 `KnowledgeDocument.toDenseRetrievalText()` 或 `toSparseRetrievalText()` 中
- `intentConcept` 不再出现在 `toSparseRetrievalText()` 中
- `domain_code` 不再出现在 `QdrantHybridQueryExecutor.buildBaseFilter()` 中
- 允许 `domainCode/domain_code` 继续出现在 payload 映射、返回结构和护栏逻辑中

- [ ] **Step 3: 如 runbook 写错现行 filter，补文档**

如果 [2026-04-06-qdrant-hybrid-cutover-runbook.md](D:\a05-cursor\docs\superpowers\reports\2026-04-06-qdrant-hybrid-cutover-runbook.md) 仍把 `domainCode` 写成召回 filter：
- 改成“当前召回阶段只按 `questionType` 和 `active` 做低误伤过滤”
- 说明 `domainCode` 仍保留在 payload 与下游结果中，但暂不参与召回过滤

- [ ] **Step 4: Commit（仅在文档或清理改动存在时执行）**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/dto/KnowledgeDocument.java backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java backend/src/main/java/com/a05/aiinterview/rag/qdrant/QdrantHybridQueryExecutor.java backend/src/test/java/com/a05/aiinterview/rag/dto/KnowledgeDocumentTest.java backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java docs/superpowers/reports/2026-04-06-qdrant-hybrid-cutover-runbook.md
git commit -m "refactor: tighten rag field split and relax recall filters"
```
