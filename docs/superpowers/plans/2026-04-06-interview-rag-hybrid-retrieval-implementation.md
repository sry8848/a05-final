# Interview RAG Hybrid Retrieval Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 `lexical prefilter -> dense -> rerank` 检索主链路，替换为基于 Qdrant 原生能力的 `dense + sparse/BM25 双路独立召回 -> RRF -> rerank -> 硬护栏`，并用新 collection 完成迁移。

**Architecture:** 保留现有 3 字段 retrieval brief 与业务层链路，删除 `VectorStore` 主实现与 lexical prefilter 旧主链路。文档侧拆分 dense 文本与 sparse 文本，检索与入库主路径改用 Qdrant Java Client，新建 hybrid collection、全量重建、切换后删除旧 collection。

**Tech Stack:** Java 21, Spring Boot 3.2.5, Spring AI `EmbeddingModel`, Qdrant Java Client, Qdrant sparse/BM25, DashScope rerank, JUnit 5, Mockito, AssertJ

---

## 文件结构与职责

### 现有文件，必须修改或删除

- `D:\a05-cursor\backend\pom.xml`
  - 显式锁定 Qdrant Java Client 能力边界，删除 `spring-ai-qdrant-store` 主职责依赖。
- `D:\a05-cursor\backend\src\main\resources\application.yml`
  - 切换到 hybrid collection 默认配置，补齐 dense/sparse/RRF 相关配置项说明。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\config\RagProperties.java`
  - 增加 hybrid collection、vector 名称、branch topK、fusion topK 等配置。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\config\RagConfiguration.java`
  - 删除 `VectorStore` Bean 与 lexical payload initializer，改为仅提供 `QdrantClient` 和 hybrid schema 初始化入口。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\KnowledgeDocument.java`
  - 从单一 `toRetrievalText()` 改为显式的 dense 文本与 sparse 文本构造。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java`
  - 表达可执行的 hybrid 查询输入，不再默认单路检索。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagContext.java`
  - 审计字段迁移到 hybrid 口径。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\KnowledgeIngestionService.java`
  - 改为原生 Qdrant hybrid 入库。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java`
  - 输出 dense/sparse/rerank 所需的可执行查询文本。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRetrievalServiceImpl.java`
  - 删除 lexical prefilter，改为双路召回与 RRF 融合。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\DashScopeRagRerankService.java`
  - 基于新的 request/audit 结构构建 rerank brief。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\config\RagConfigurationTest.java`
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\KnowledgeIngestionServiceTest.java`
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\RagContextTest.java`
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\interview\service\QuestionStreamServiceBuildInputTest.java`
- `D:\a05-cursor\docs\superpowers\reports\2026-04-03-interview-rag-baseline-and-improvement-directions.md`
- `D:\a05-cursor\docs\superpowers\reports\2026-04-06-evaluation-decision-to-question-generation-chain.md`
- Delete: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\eval\InterviewRagEvaluationTest.java`
- Delete: `D:\a05-cursor\backend\src\test\resources\rag-eval\interview-retrieval-cases.json`

### 建议新增文件

- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridCollectionManager.java`
  - 原生 collection schema、payload index、命名向量定义。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridPointMapper.java`
  - `KnowledgeDocument -> denseText/sparseText/payload/stablePointId` 映射。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridQueryExecutor.java`
  - dense 查询、sparse 查询、候选点解析。
- `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\RrfFusion.java`
  - 纯 Java RRF 融合，保证可测试与可审计。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\KnowledgeDocumentTest.java`
  - dense 文本与 sparse 文本构造测试。
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\qdrant\QdrantHybridCollectionManagerTest.java`
- `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\qdrant\RrfFusionTest.java`
- `D:\a05-cursor\docs\superpowers\reports\2026-04-06-qdrant-hybrid-cutover-runbook.md`
  - 人工迁移、切换、旧 collection 删除 runbook。

## 人工前置 Gate

这些动作不由代码代理执行，由你手动完成：

- 核对本地 Qdrant 是否支持目标 sparse/BM25 能力。
- 如果当前版本不满足，按你当前安装渠道手动升级 Qdrant。
- 在最终切换前确认是否保留旧 collection 备份。
- 在最终切换后，手动确认删除旧 collection。

没有完成这些人工动作，不进入最终 cutover。

## Chunk 1: 切换到底层原生 Qdrant 基础设施

### Task 1: 删掉 `VectorStore` 主职责，建立 hybrid schema 配置骨架

**Files:**
- Modify: `D:\a05-cursor\backend\pom.xml`
- Modify: `D:\a05-cursor\backend\src\main\resources\application.yml`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\config\RagProperties.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\config\RagConfiguration.java`
- Create: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridCollectionManager.java`
- Test: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\config\RagConfigurationTest.java`
- Test: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\qdrant\QdrantHybridCollectionManagerTest.java`

- [ ] **Step 1: 先写失败测试，锁定新的 schema 预期**

在 `QdrantHybridCollectionManagerTest` 里先表达这些预期：
- collection 名称默认切到 `interview_knowledge_hybrid`
- schema 需要 dense named vector 与 sparse named vector
- payload index 只保留最终仍需要的 metadata 索引，不再验证 lexical prefilter 专用索引初始化

同时把 `RagConfigurationTest` 从“检查 lexical payload index”改成“检查不再提供 `VectorStore` 主链路依赖，而是挂上 hybrid schema 初始化器”。

- [ ] **Step 2: 运行配置相关测试，确认先失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagConfigurationTest,QdrantHybridCollectionManagerTest" test
```

Expected:
- 因 `QdrantHybridCollectionManager` 尚不存在而编译失败
- 或测试断言失败，提示仍在初始化旧 lexical payload indexes

- [ ] **Step 3: 实现最小配置骨架**

实现以下改动：
- 在 `pom.xml` 显式引入需要的 Qdrant Java Client 版本
- 删除 `spring-ai-qdrant-store` 的主职责依赖与相关说明
- 在 `RagProperties` 中新增 hybrid 相关配置，例如：
  - `collectionName`
  - `denseVectorName`
  - `sparseVectorName`
  - `denseTopK`
  - `sparseTopK`
  - `fusionTopK`
- 在 `application.yml` 中把默认 collection 名称切到 hybrid
- `RagConfiguration` 删除 `VectorStore` Bean 和 `ragLexicalIndexInitializer`
- 新增 `QdrantHybridCollectionManager`，负责 schema 初始化与必要 payload index

- [ ] **Step 4: 复跑配置测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=RagConfigurationTest,QdrantHybridCollectionManagerTest" test
```

Expected:
- PASS
- 不再出现 `VectorStore` 初始化与 lexical payload initializer 相关断言

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/java/com/a05/aiinterview/rag/config/RagProperties.java backend/src/main/java/com/a05/aiinterview/rag/config/RagConfiguration.java backend/src/main/java/com/a05/aiinterview/rag/qdrant/QdrantHybridCollectionManager.java backend/src/test/java/com/a05/aiinterview/rag/config/RagConfigurationTest.java backend/src/test/java/com/a05/aiinterview/rag/qdrant/QdrantHybridCollectionManagerTest.java
git commit -m "feat: switch rag infra to native qdrant hybrid schema"
```

## Chunk 2: 题卡拆分 dense/sparse 文本并改成原生入库

### Task 2: 为题卡定义双文本表示

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\KnowledgeDocument.java`
- Create: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\KnowledgeDocumentTest.java`

- [ ] **Step 1: 写失败测试，明确 dense/sparse 文本边界**

在 `KnowledgeDocumentTest` 里先表达：
- `toDenseRetrievalText()` 包含 `questionText / intentConcept / referenceContext / scoringKeyPoints / keywords`
- `toSparseRetrievalText()` 只包含术语化内容，不包含长段 `referenceContext` 或 `scoringPitfalls`
- 行为题与项目题都允许生成 sparse 文本，但内容必须去掉纯叙事噪音

- [ ] **Step 2: 运行文本构造测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=KnowledgeDocumentTest" test
```

Expected:
- 因双文本方法尚不存在而编译失败

- [ ] **Step 3: 实现双文本方法并删除旧单一入口**

在 `KnowledgeDocument` 中：
- 新增 `toDenseRetrievalText()`
- 新增 `toSparseRetrievalText()`
- 删除旧 `toRetrievalText()`，避免旧逻辑继续被调用

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
git commit -m "feat: split knowledge document into dense and sparse texts"
```

### Task 3: 用 Qdrant 原生 upsert 重写入库链路

**Files:**
- Create: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridPointMapper.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\KnowledgeIngestionService.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\KnowledgeIngestionServiceTest.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\KnowledgeJsonlImportServiceTest.java`

- [ ] **Step 1: 先改失败测试，锁定 native upsert 形态**

把 `KnowledgeIngestionServiceTest` 改成验证：
- 不再调用 `VectorStore.add(...)`
- 会生成 stable point id
- payload 同时包含 dense 文本与 sparse 文本所需元数据
- 入库时显式写入 dense vector 与 sparse source text

同步补一个 `KnowledgeJsonlImportServiceTest` 用例，保证外层调用不因构造器签名变化而失真。

- [ ] **Step 2: 运行入库测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=KnowledgeIngestionServiceTest,KnowledgeJsonlImportServiceTest" test
```

Expected:
- 因 `KnowledgeIngestionService` 仍依赖 `VectorStore` 而失败

- [ ] **Step 3: 实现原生 Qdrant 入库**

实现要点：
- `KnowledgeIngestionService` 改为依赖 `EmbeddingModel + QdrantClient + RagProperties`
- `QdrantHybridPointMapper` 负责：
  - stable point id
  - dense text
  - sparse text
  - payload map
- 入库时显式计算 dense embedding
- 通过 Qdrant 原生 upsert 写入 named dense vector、sparse 文本/稀疏表示、payload
- 彻底删除 `VectorStore` 参与入库的主路径

- [ ] **Step 4: 复跑入库测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=KnowledgeDocumentTest,KnowledgeIngestionServiceTest,KnowledgeJsonlImportServiceTest" test
```

Expected:
- PASS
- 旧 `VectorStore.add(...)` 断言全部移除

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/qdrant/QdrantHybridPointMapper.java backend/src/main/java/com/a05/aiinterview/rag/service/KnowledgeIngestionService.java backend/src/test/java/com/a05/aiinterview/rag/service/KnowledgeIngestionServiceTest.java backend/src/test/java/com/a05/aiinterview/rag/service/KnowledgeJsonlImportServiceTest.java
git commit -m "feat: rewrite rag ingestion with native qdrant hybrid upsert"
```

## Chunk 3: 编译器输出可执行的 hybrid 查询

### Task 4: 重塑 `RagRetrievalRequest` 与 `RagPlanCompiler`

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagRetrievalRequest.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\RagPlanCompiler.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`

- [ ] **Step 1: 先改失败测试，锁定 compiler 输出**

在 `RagPlanCompilerTest` 中补充并收紧这些断言：
- `queryText` 只表达原始语义意图
- request 中显式提供 `denseQueryText`
- request 中显式提供 `sparseQueryText`
- `keywordHints=[]` 时 `sparseQueryText` 允许为空，但 `denseQueryText` 仍可执行
- `focusPoint` 不能再偷偷补进 sparse 查询

- [ ] **Step 2: 运行编译器测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest" test
```

Expected:
- 因 `RagRetrievalRequest` 尚无 dense/sparse 显式字段而失败

- [ ] **Step 3: 实现 compiler 与 request 新结构**

实现要点：
- `RagRetrievalRequest` 至少补齐：
  - `queryText`
  - `denseQueryText`
  - `sparseQueryText`
  - `keywordQueries`
  - 现有上下文字段
- `RagPlanCompiler` 显式编译：
  - `denseQueryText <- queryText`
  - `sparseQueryText <- keywordHints join`
- 不再保留任何旧的 lexical fallback 或 clue 拼接逻辑

- [ ] **Step 4: 复跑编译器测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest" test
```

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java backend/src/main/java/com/a05/aiinterview/rag/service/RagPlanCompiler.java backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java
git commit -m "feat: compile retrieval brief into explicit hybrid query fields"
```

## Chunk 4: 用 Qdrant 原生查询替换旧检索主链路

### Task 5: 先迁移审计结构与 RRF 融合工具

**Files:**
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\dto\RagContext.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\dto\RagContextTest.java`
- Create: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\RrfFusion.java`
- Create: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\qdrant\RrfFusionTest.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\interview\service\QuestionStreamServiceBuildInputTest.java`

- [ ] **Step 1: 写失败测试，锁定 hybrid 审计字段**

把 `RagContextTest` 和 `QuestionStreamServiceBuildInputTest` 的审计断言改成 hybrid 口径：
- `denseCandidateCount`
- `sparseCandidateCount`
- `fusionTopQuestionIds`
- `rerankPreTopQuestionIds`
- `rerankPostTopQuestionIds`
- `injectedQuestionIds`

同时在 `RrfFusionTest` 里固定 RRF 排序规则：
- 同时命中的题卡优先
- 单路命中依 rank 衰减
- 相同得分时按 questionId 稳定排序

- [ ] **Step 2: 运行审计与融合测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagContextTest,RrfFusionTest,QuestionStreamServiceBuildInputTest" test
```

Expected:
- 因 `RagContext.RetrievalAudit` 仍是旧字段而失败
- 因 `RrfFusion` 尚不存在而编译失败

- [ ] **Step 3: 实现新审计结构与 RRF 工具**

实现要点：
- `RagContext.RetrievalAudit` 删除 `lexicalCandidateCount`
- 新增 `sparseCandidateCount`
- 新增 `fusionTopQuestionIds`
- `toAuditMap()` 改成输出 hybrid 审计字段
- 新增纯 Java `RrfFusion`

- [ ] **Step 4: 复跑测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=RagContextTest,RrfFusionTest,QuestionStreamServiceBuildInputTest" test
```

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/dto/RagContext.java backend/src/test/java/com/a05/aiinterview/rag/dto/RagContextTest.java backend/src/main/java/com/a05/aiinterview/rag/qdrant/RrfFusion.java backend/src/test/java/com/a05/aiinterview/rag/qdrant/RrfFusionTest.java backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java
git commit -m "feat: migrate rag audit and fusion model to hybrid retrieval"
```

### Task 6: 用原生 Qdrant query 重写 `RagRetrievalServiceImpl`

**Files:**
- Create: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\qdrant\QdrantHybridQueryExecutor.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\RagRetrievalServiceImpl.java`
- Modify: `D:\a05-cursor\backend\src\main\java\com\a05\aiinterview\rag\service\impl\DashScopeRagRerankService.java`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagRetrievalServiceImplTest.java`

- [ ] **Step 1: 先重写失败测试，锁定新检索主链路**

把 `RagRetrievalServiceImplTest` 改成验证：
- 不再调用 lexical prefilter scroll 作为前置裁剪
- dense 分支与 sparse 分支各自独立执行
- `keywordQueries=[]` 时，sparse 分支为空，但 dense 仍执行
- RRF 融合后再调用 rerank
- 审计字段记录 dense/sparse/fusion/rerank 各阶段结果
- 行为题和项目题护栏仍然有效

- [ ] **Step 2: 运行检索服务测试，确认失败**

Run:

```powershell
rtk mvn -q "-Dtest=RagRetrievalServiceImplTest" test
```

Expected:
- 因测试已切到双路召回语义而失败
- 旧 `VectorStore.similaritySearch()` 相关依赖断言不再成立

- [ ] **Step 3: 实现原生 hybrid 检索**

实现要点：
- `QdrantHybridQueryExecutor` 负责：
  - dense branch 查询
  - sparse/BM25 branch 查询
  - payload/point -> candidate 映射
- `RagRetrievalServiceImpl` 删除：
  - `lexicalPrefilter`
  - `buildLexicalPrefilterFilter`
  - `buildLexicalPrefilterScrollRequest`
  - `allowedQuestionIds` 这类前置裁剪逻辑
- 新链路改成：
  1. 前置低误伤 filter
  2. dense 查询
  3. sparse 查询
  4. RRF 融合
  5. rerank
  6. 硬护栏
- `DashScopeRagRerankService` 改为基于新的 request/audit 字段构建 brief

- [ ] **Step 4: 复跑检索层测试，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=RagRetrievalServiceImplTest,QuestionStreamServiceBuildInputTest" test
```

Expected:
- PASS
- 旧 lexical prefilter 断言全部消失

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/qdrant/QdrantHybridQueryExecutor.java backend/src/main/java/com/a05/aiinterview/rag/service/impl/RagRetrievalServiceImpl.java backend/src/main/java/com/a05/aiinterview/rag/service/impl/DashScopeRagRerankService.java backend/src/test/java/com/a05/aiinterview/rag/service/RagRetrievalServiceImplTest.java
git commit -m "feat: replace rag retrieval chain with native dense sparse hybrid recall"
```

## Chunk 5: 删除主观评测夹具并收口到明确契约

### Task 7: 删除评测夹具并把契约收口到现有单测

**Files:**
- Delete: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\eval\InterviewRagEvaluationTest.java`
- Delete: `D:\a05-cursor\backend\src\test\resources\rag-eval\interview-retrieval-cases.json`
- Modify: `D:\a05-cursor\backend\src\test\java\com\a05\aiinterview\rag\service\RagPlanCompilerTest.java`
- Modify: `D:\a05-cursor\docs\superpowers\plans\2026-04-06-interview-rag-hybrid-retrieval-implementation.md`

- [ ] **Step 1: 先运行旧夹具，确认它仍是历史技术债**

Run:

```powershell
rtk mvn -q "-Dtest=InterviewRagEvaluationTest" test
```

Expected:
- FAIL
- 失败原因来自旧主观断言或旧实现耦合，而不是当前 chunk5 所需的明确契约

- [ ] **Step 2: 删除主观评测夹具与样例资源**

删除：
- `InterviewRagEvaluationTest.java`
- `interview-retrieval-cases.json`

不保留缩减版夹具，避免仓库继续暗示“当前已有一套有效的 RAG 质量评测体系”。

- [ ] **Step 3: 把剩余有效契约收口到 `RagPlanCompilerTest`**

在 `RagPlanCompilerTest` 中只保留这些断言：
- `shouldRetrieve`
- `questionType`
- `queryText`
- `denseQueryText`
- `sparseQueryText`

显式删除这些不再属于当前契约的断言：
- `domainCode`
- `keywordQueries`
- `focusPoint`
- `projectName`
- `difficultyHint`

- [ ] **Step 4: 同步计划文档里的 chunk5 与回归命令**

更新这份计划文档，使其与最新口径一致：
- chunk5 改成“删除评测夹具并收口到明确契约”
- `Files` 从 `Modify` 改成 `Delete + Modify`
- 核心回归命令里移除 `InterviewRagEvaluationTest`
- 完成定义改成：主观评测夹具已删除，剩余契约由 `RagPlanCompilerTest` 和 `RagRetrievalServiceImplTest` 承担

- [ ] **Step 5: 运行定向回归，确认通过**

Run:

```powershell
rtk mvn -q "-Dtest=RagPlanCompilerTest,RagRetrievalServiceImplTest" test
```

Expected:
- PASS

- [ ] **Step 6: 做残留搜索，确认代码侧已去掉旧夹具**

Run:

```powershell
rtk rg -n "InterviewRagEvaluationTest|interview-retrieval-cases\\.json|rag-eval" D:\a05-cursor\backend\src\test D:\a05-cursor\backend\src\main
```

Expected:
- 代码侧不再命中旧评测夹具与样例资源
- 仅允许历史文档命中

- [ ] **Step 7: Commit**

```bash
git add backend/src/test/java/com/a05/aiinterview/rag/service/RagPlanCompilerTest.java docs/superpowers/plans/2026-04-06-interview-rag-hybrid-retrieval-implementation.md
git rm backend/src/test/java/com/a05/aiinterview/rag/eval/InterviewRagEvaluationTest.java backend/src/test/resources/rag-eval/interview-retrieval-cases.json
git commit -m "test: remove subjective rag evaluation harness"
```

## Chunk 6: 切换说明、中文文档与全链路回归

### Task 8: 写 cutover runbook 并更新两份现行中文文档

**Files:**
- Create: `D:\a05-cursor\docs\superpowers\reports\2026-04-06-qdrant-hybrid-cutover-runbook.md`
- Modify: `D:\a05-cursor\docs\superpowers\reports\2026-04-03-interview-rag-baseline-and-improvement-directions.md`
- Modify: `D:\a05-cursor\docs\superpowers\reports\2026-04-06-evaluation-decision-to-question-generation-chain.md`

- [ ] **Step 1: 先列出必须同步的文档事实**

先把文档必须同步的事实写进草稿：
- 现行主链路已改为 `dense + sparse -> RRF -> rerank`
- `VectorStore` 已退出主职责
- collection 迁移路径是新建、重建、切换、删除旧 collection
- cutover 中哪些步骤需要你手动执行

- [ ] **Step 2: 更新文档与 runbook**

runbook 至少包含：
- 人工前置检查
- 启动前配置项
- 新 collection 创建/重建验证
- 切换 `QDRANT_COLLECTION`
- 删除旧 collection 前的确认项
- 删除后的验收项

两份中文文档要与代码口径完全一致，不再混入旧 `lexical prefilter` 作为现行实现。

- [ ] **Step 3: 运行定向文档残留检查**

Run:

```powershell
rtk rg -n "VectorStore|lexical prefilter|QdrantVectorStore|payload full-text filter" D:\a05-cursor\docs\superpowers\reports\2026-04-03-interview-rag-baseline-and-improvement-directions.md D:\a05-cursor\docs\superpowers\reports\2026-04-06-evaluation-decision-to-question-generation-chain.md D:\a05-cursor\docs\superpowers\reports\2026-04-06-qdrant-hybrid-cutover-runbook.md
```

Expected:
- 只允许出现在“已删除旧逻辑/历史说明”的上下文
- 不再把旧链路写成现行实现

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/reports/2026-04-03-interview-rag-baseline-and-improvement-directions.md docs/superpowers/reports/2026-04-06-evaluation-decision-to-question-generation-chain.md docs/superpowers/reports/2026-04-06-qdrant-hybrid-cutover-runbook.md
git commit -m "docs: document hybrid rag cutover and current retrieval chain"
```

### Task 9: 做全链路回归并准备人工 cutover

**Files:**
- Verify only

- [ ] **Step 1: 运行核心测试回归**

Run:

```powershell
rtk mvn -q "-Dtest=RagConfigurationTest,QdrantHybridCollectionManagerTest,KnowledgeDocumentTest,KnowledgeIngestionServiceTest,KnowledgeJsonlImportServiceTest,RagPlanCompilerTest,RagContextTest,RrfFusionTest,RagRetrievalServiceImplTest,QuestionStreamServiceBuildInputTest,AnswerSubmitServiceDecisionFlowTest" test
```

Expected:
- PASS

- [ ] **Step 2: 做全局残留搜索**

Run:

```powershell
rtk rg -n "QdrantVectorStore|VectorStore|lexicalPrefilter|LEXICAL_PREFILTER_MULTIPLIER|similaritySearch\\(|spring-ai-qdrant-store" D:\a05-cursor\backend\src\main D:\a05-cursor\backend\src\test
```

Expected:
- 不再出现旧检索主链路残留
- 仅允许无害注释或历史说明命中；如有现行代码命中，返回上一任务清理
- `QdrantHybridCollectionManager` 当前负责合法的 payload index 初始化，不作为旧链路残留判定条件

- [ ] **Step 3: 人工 cutover Gate**

人工执行：
- 确认本地 Qdrant 版本满足 sparse/BM25 能力
- 运行新 collection 数据重建
- 验证新 collection 检索结果
- 切换到新 collection
- 最后手动删除旧 collection

Expected:
- 新 collection 成为唯一默认 collection
- 旧 collection 不再保留

- [ ] **Step 4: Commit（仅在 Step 1/2 暴露问题并产生修复文件时执行）**

```bash
git add <仅本轮修复实际修改的 hybrid retrieval 文件>
git commit -m "fix: finish hybrid rag retrieval cutover cleanup"
```

## 计划执行顺序要求

- 必须按 Chunk 顺序执行，不要跳步。
- 任何一步如果需要恢复旧 `VectorStore` 或 lexical prefilter 才能“临时跑通”，说明设计退回了旧逻辑，必须停下重做。
- Chunk 6 的人工 cutover 没有经过你确认，不执行旧 collection 删除。

## 完成定义

全部 Chunk 完成后，以下条件必须同时成立：

- 代码中不再以 `VectorStore` 作为 RAG 主入库和主检索实现
- 代码中不再存在 lexical prefilter 先裁剪 dense 的主链路
- `KnowledgeDocument` 已显式提供 dense 文本与 sparse 文本
- `KnowledgeIngestionService` 已通过原生 Qdrant upsert 写入 hybrid collection
- `RagRetrievalServiceImpl` 已改为 dense + sparse + RRF + rerank
- 主观评测夹具与 `rag-eval` 样例资源已删除；剩余契约由 `RagPlanCompilerTest` 与 `RagRetrievalServiceImplTest` 承担
- 现行中文文档与 runbook 已同步
- 新 collection 已切换为默认使用目标
- 旧 collection 仅在你手动确认后删除
