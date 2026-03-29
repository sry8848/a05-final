# Follow-up Term Alignment Cleanup Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理剩余 5 组同类口径漂移，但避免误伤当前已跑通的 `intro` 首题链路；优先收紧 RAG 真源、岗位命名、焦点字段和 AI 默认配置。

**Architecture:** 这轮继续做语义收紧，但不再采用“一刀切删除所有特殊值”的方案。`domainCode` 对岗位知识域仍保持强约束；`behavioral/项目落地伪域值` 这类运行态伪域要清理；`intro` 先单独审查并在当前计划中按“受控例外”处理。AI 配置层保持“百炼为默认操作入口，OpenAI-compatible 为底层协议抽象”，避免把当前本地联调入口改坏。

**Tech Stack:** Spring Boot 3, MyBatis-Plus, MySQL schema SQL, Spring AI/OpenAI-compatible config, Markdown docs/tests

---

## Scope

本计划只覆盖以下 5 个问题：

1. RAG 样例数据仍使用旧 `positionCode/domainCode`
2. `domainCode` 被题型阶段语义污染
3. `positionCode` / `positionCode` 双命名并存
4. 不包含 `roundType` 清理
5. `nextFocus / focusPoint / 旧题目焦点字段` 三套焦点词并存
6. AI 提供商配置同时使用百炼口径和 OpenAI 口径

## Recommended Decisions

- `domainCode`：默认只允许来自 `position_skill_domains.domain_code`
- `questionType`：承载 `INTRO / BEHAVIORAL / PROJECT_DEEP_DIVE / ...`
- `INTRO`：当前阶段保留为受控例外，不纳入本轮破坏性迁移
  - 原因：首题生成、详情快照、单题评估输入、问答库收藏、报告生成和现有测试都显式依赖 `domainCode=intro`
  - 本轮动作：先补全链路审计与文档定义，不直接改行为
- `BEHAVIORAL / PROJECT_DEEP_DIVE`：不应继续伪装成 `behavioral / 项目落地伪域值` 知识域
- `positionCode`：作为岗位编码唯一对外名称
- `focusPoint`：作为题目当前焦点唯一持久化/展示名称
- `nextFocus`：保留为决策阶段字段，只表示“下一题目标焦点”
- `AI_BAILIAN_API_KEY`：保持默认操作入口
- `OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL`：作为 OpenAI-compatible 兼容层变量保留，不作为新手默认入口

## Current Intro Flow Findings

这部分是本轮新补的结论，避免误改：

- 首题生成时，`FirstQuestionGenerationService` 直接写入 `questionType=INTRO`、`domainCode=intro`、`generationContextJson.domainCode=intro`，并同步写 `domainName` 与 `focusPoint` [`FirstQuestionGenerationService.java`](/D:/a05-cursor/worktrees/java-backend-domains-task1-2/backend/src/main/java/com/a05/aiinterview/interview/engine/FirstQuestionGenerationService.java#L160)
- 题目详情和首题快照会直接读取 `domainCode/domainName`，并且 `QuestionDtoAssembler` 会把 `intro` 当成可显示的特殊值 [`QuestionDtoAssembler.java`](/D:/a05-cursor/worktrees/java-backend-domains-task1-2/backend/src/main/java/com/a05/aiinterview/interview/dto/QuestionDtoAssembler.java#L19)
- 单题详细评估输入会把 `generationContextJson.domainCode` 直接传给 AI [`QuestionDetailEvaluationInputFactory.java`](/D:/a05-cursor/worktrees/java-backend-domains-task1-2/backend/src/main/java/com/a05/aiinterview/interview/service/QuestionDetailEvaluationInputFactory.java#L23)
- 问答库收藏会把 `intro` 落到 `question_bank_items.domain_code`，并在快照里展示“自我介绍” [`QuestionBankService.java`](/D:/a05-cursor/worktrees/java-backend-domains-task1-2/backend/src/main/java/com/a05/aiinterview/questionbank/service/QuestionBankService.java#L220)
- 报告生成在缺少知识域时还会直接兜底成 `intro` [`ReportGenerationService.java`](/D:/a05-cursor/worktrees/java-backend-domains-task1-2/backend/src/main/java/com/a05/aiinterview/interview/engine/ReportGenerationService.java#L231)
- 相关测试已经把这些行为固化：详情、收藏、首题快照都断言 `domainCode=intro` [`InterviewServiceCurrentQuestionDetailTest.java`](/D:/a05-cursor/worktrees/java-backend-domains-task1-2/backend/src/test/java/com/a05/aiinterview/interview/service/InterviewServiceCurrentQuestionDetailTest.java#L116), [`QuestionBankServiceTest.java`](/D:/a05-cursor/worktrees/java-backend-domains-task1-2/backend/src/test/java/com/a05/aiinterview/questionbank/service/QuestionBankServiceTest.java#L130), [`PlannerOrchestrationServiceJsonUpdateTest.java`](/D:/a05-cursor/worktrees/java-backend-domains-task1-2/backend/src/test/java/com/a05/aiinterview/interview/engine/PlannerOrchestrationServiceJsonUpdateTest.java#L91)

结论：

- `intro` 目前不是“随手加的伪 domain”，而是首题链路的结构化锚点
- 本轮不应把 `intro` 和 `behavioral/项目落地伪域值` 一并删除
- 更合理的做法是：
  - 先把 `intro` 定义成“当前系统保留的首题阶段码”
  - 清掉 `behavioral/项目落地伪域值`
  - 后续如果还想让 `intro` 退出 `domainCode`，必须单独做一轮链路迁移

## Design Notes

### Why RAG sample data still must be cleaned immediately

- 这部分不是“历史兼容”，而是现在一开启 `RAG_INIT_SAMPLE=true` 就会写入非法 `positionCode/domainCode`
- 当前真源已经是 `JAVA_BACKEND` + 10 个岗位知识域，样例数据继续写 `旧岗位码/旧知识点码A` 属于直接污染

### Why `intro` should be separated from `behavioral/项目落地伪域值`

- `intro` 已经深入首题、快照、报告、问答库链路
- `behavioral/项目落地伪域值` 则主要是后来为了补显示和兜底而塞进 `domainCode`
- 它们虽然都不是岗位知识域，但工程地位完全不同，必须拆开处理

### Why `positionCode` should beat `positionCode`

- AI DTO、RAG、岗位服务、知识库文档都已偏向 `positionCode`
- `positionCode` 主要残留在会话/API 层
- 继续双命名只会逼着代码里保留 `normalizePositionCode(positionCode)` 一类桥接

### Why `focusPoint` should beat the old question-focus field

- 旧题目焦点字段已被混用成“项目点 / 理论点 / 域名兜底”
- `focusPoint` 更接近 planner / evaluation / RAG 三段的真实语义
- `nextFocus` 是流程字段，不该直接作为持久化字段名

### Why `AI_BAILIAN_API_KEY` should remain the default operation key

- 当前仓库 README、`.env.example`、`application-local.yml`、TTS/ASR 体验都围绕百炼本地联调构建
- 对新手来说，`AI_BAILIAN_API_KEY` 是更稳定的默认入口
- 但底层客户端仍是 OpenAI-compatible，因此兼容层变量不能删，只能降级为后备/高级配置

## Chunk 1: Fix RAG Sample Data Truth Source

### Task 1: 把样例知识数据改成当前岗位/知识域真源

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/data/SampleKnowledgeDataLoader.java`
- Modify: `docs/superpowers/specs/2026-03-12-rag-knowledge-collection-requirements.md`
- Test: `backend/src/test/java/com/a05/aiinterview/rag/data/SampleKnowledgeDataLoaderTest.java`（如不存在则新增）

- [ ] **Step 1: 写失败测试，锁定样例数据必须使用合法 `positionCode/domainCode`**

```java
assertThat(allDocs)
    .extracting(KnowledgeDocument::getPositionCode)
    .containsOnly("JAVA_BACKEND");

assertThat(allDocs)
    .extracting(KnowledgeDocument::getDomainCode)
    .allMatch(code -> allowedDomainCodes.contains(code));
```

- [ ] **Step 2: 运行测试确认当前失败**

Run: `mvn -q "-Dtest=SampleKnowledgeDataLoaderTest" test`

- [ ] **Step 3: 写最小实现**

实现规则：

- `positionCode("旧岗位码")` -> `positionCode("JAVA_BACKEND")`
- 旧知识点型 `domainCode` 改成合法岗位域：
  - JMM / volatile / 并发内存归 `concurrency`
  - GC / 类加载 / OOM 归 `jvm`
- 细粒度知识点保留在 `source / content / tags / aliases`

- [ ] **Step 4: 更新文档样例**

清掉 `旧岗位码`、`js_core`、`旧知识点码A` 这类已失效样例。

- [ ] **Step 5: 复跑测试**

Run: `mvn -q "-Dtest=SampleKnowledgeDataLoaderTest" test`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/rag/data/SampleKnowledgeDataLoader.java backend/src/test/java/com/a05/aiinterview/rag/data/SampleKnowledgeDataLoaderTest.java docs/superpowers/specs/2026-03-12-rag-knowledge-collection-requirements.md
git commit -m "refactor: align rag sample data with canonical role domains"
```

## Chunk 2: Clean Pseudo Domains Without Breaking Intro

### Task 2: 只清理 `behavioral / 项目落地伪域值`，并把 `intro` 固化为受控例外

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/support/InterviewDomainDisplaySupport.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/engine/SystemFallbackPlanBuilder.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/engine/ReportGenerationService.java`
- Modify: `backend/src/main/resources/db/schema-interview-v2.sql`
- Modify: `docs/superpowers/specs/2026-03-20-planner-contract.md`
- Modify: `docs/superpowers/specs/2026-03-20-evaluation-decision-input-contract.md`
- Modify: `docs/superpowers/specs/2026-03-12-rag-knowledge-collection-requirements.md`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/engine/SystemFallbackPlanBuilderTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/engine/ReportGenerationServiceTest.java`

- [ ] **Step 1: 写失败测试**

断言方向：

```java
assertThat(behavioralQuestion.getDomainCode()).isBlank();
assertThat(projectQuestion.getDomainCode()).isBlank();
assertThat(introQuestion.getDomainCode()).isEqualTo("intro");
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q "-Dtest=QuestionStreamServiceBuildInputTest,SystemFallbackPlanBuilderTest,ReportGenerationServiceTest" test`

- [ ] **Step 3: 写最小实现**

实现规则：

- `INTRO`：本轮保持 `domainCode=intro`
- `BEHAVIORAL / PROJECT_DEEP_DIVE`：停止写入伪 domainCode
- `InterviewDomainDisplaySupport` 只保留：
  - `intro` 受控例外的显示名
  - 题型展示名工具
- `ReportGenerationService` 不再把“缺域”一律兜成 `intro`
  - `INTRO` 题保留 `intro`
  - 其他无域题按 `questionType` 生成展示标签和统计策略

- [ ] **Step 4: 更新 schema 与契约文档**

文档必须明确：

- `domain_code` 主语义仍是岗位知识域
- 当前系统允许一个受控例外：`INTRO -> intro`
- `behavioral/项目落地伪域值` 不再作为 `domainCode` 合法值

- [ ] **Step 5: 复跑测试**

Run: `mvn -q "-Dtest=QuestionStreamServiceBuildInputTest,SystemFallbackPlanBuilderTest,ReportGenerationServiceTest" test`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/service/support/InterviewDomainDisplaySupport.java backend/src/main/java/com/a05/aiinterview/interview/engine/SystemFallbackPlanBuilder.java backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java backend/src/main/java/com/a05/aiinterview/interview/engine/ReportGenerationService.java backend/src/main/resources/db/schema-interview-v2.sql docs/superpowers/specs/2026-03-20-planner-contract.md docs/superpowers/specs/2026-03-20-evaluation-decision-input-contract.md docs/superpowers/specs/2026-03-12-rag-knowledge-collection-requirements.md
git commit -m "refactor: remove non-intro pseudo domain codes"
```

### Task 2B: 为 `intro` 单独补链路审计说明，不在本轮迁出

**Files:**
- Modify: `docs/superpowers/specs/2026-03-20-planner-contract.md`
- Modify: `docs/superpowers/specs/2026-03-20-evaluation-decision-input-contract.md`
- Modify: `docs/db-schema.md`
- Create: `docs/superpowers/specs/2026-03-27-intro-domain-exception-design.md`

- [ ] **Step 1: 写 `intro` 例外设计说明**

内容要点：

- 现在为什么保留 `domainCode=intro`
- 哪些链路依赖它
- 后续如果要移除，需要一次什么级别的迁移

- [ ] **Step 2: 文档引用回填**

在主契约文档里引用该说明，避免以后有人把 `intro` 和 `behavioral` 再混成一类。

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-03-20-planner-contract.md docs/superpowers/specs/2026-03-20-evaluation-decision-input-contract.md docs/db-schema.md docs/superpowers/specs/2026-03-27-intro-domain-exception-design.md
git commit -m "docs: document intro domain exception in interview flow"
```

## Chunk 3: Unify Role Naming On `positionCode`

### Task 3: 把 `positionCode` 改成 `positionCode`

**Files:**
- Modify: `backend/src/main/resources/db/schema-interview-v2.sql`
- Modify: `backend/src/main/resources/mapper/interview/InterviewSessionMapper.xml`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/entity/InterviewSession.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/entity/InterviewPreference.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/dto/CreateInterviewRequest.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewDetailDto.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewHistoryItemDto.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewPreferenceDto.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewReportDto.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/InterviewService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/profile/service/ProfileService.java`
- Modify: `docs/api-design.md`
- Modify: `docs/db-schema.md`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/service/InterviewServiceTest.java`（按实际文件名）
- Test: `backend/src/test/java/com/a05/aiinterview/profile/service/ProfileServiceTest.java`

- [ ] **Step 1: 写失败测试**

锁定：

- 创建会话请求字段改为 `positionCode`
- 返回 DTO 改为 `positionCode`
- profile 统计/概览过滤参数只认 `positionCode`

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q "-Dtest=InterviewServiceTest,ProfileServiceTest" test`

- [ ] **Step 3: 写最小实现**

实现规则：

- 数据库列改成 `position_code`
- Java 实体/DTO/mapper 全量统一成 `positionCode`
- 删除 `normalizePositionCode(positionCode)` 这类桥接命名
- `TargetRole` 枚举名字可暂不改，但注释要改成“岗位编码枚举”

- [ ] **Step 4: 更新接口文档**

`docs/api-design.md`、OpenAPI 注解、README 示例统一使用 `positionCode`

- [ ] **Step 5: 复跑测试**

Run: `mvn -q "-Dtest=InterviewServiceTest,ProfileServiceTest" test`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/schema-interview-v2.sql backend/src/main/resources/mapper/interview/InterviewSessionMapper.xml backend/src/main/java/com/a05/aiinterview/interview backend/src/main/java/com/a05/aiinterview/profile/service/ProfileService.java docs/api-design.md docs/db-schema.md
git commit -m "refactor: unify interview role naming on position code"
```

## Chunk 4: Unify Question Focus Semantics

### Task 4: 把题目持久化/展示字段从旧题目焦点字段收口到 `focusPoint`

**Files:**
- Modify: `backend/src/main/resources/db/schema-interview-v2.sql`
- Modify: `backend/src/main/resources/mapper/interview/InterviewQuestionMapper.xml`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/entity/InterviewQuestion.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/dto/QuestionDto.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/dto/QuestionDtoAssembler.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionRedoService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/questionbank/service/QuestionBankService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/service/QuestionStreamServiceBuildInputTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/questionbank/service/QuestionBankServiceTest.java`

- [ ] **Step 1: 写失败测试**

锁定：

- `QuestionDto` 暴露 `focusPoint`
- `generationContextJson` 存 `focusPoint`
- `question_bank` 快照里的当前题焦点字段也叫 `focusPoint`
- 不再断言旧题目焦点字段

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q "-Dtest=QuestionStreamServiceBuildInputTest,QuestionBankServiceTest" test`

- [ ] **Step 3: 写最小实现**

语义划分：

- `nextFocus`：只存在于决策执行计划
- `focusPoint`：题目落库、快照、DTO、RAG 请求的当前焦点
- `nextProjectPoint`：若保留，只是内部中间信号；落库前归并成 `focusPoint`
- 删除旧题目焦点字段、注释、mapper、快照残留

- [ ] **Step 4: 更新 DTO/文档**

对外接口、快照、测试样例全部统一成 `focusPoint`

- [ ] **Step 5: 复跑测试**

Run: `mvn -q "-Dtest=QuestionStreamServiceBuildInputTest,QuestionBankServiceTest" test`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/schema-interview-v2.sql backend/src/main/resources/mapper/interview/InterviewQuestionMapper.xml backend/src/main/java/com/a05/aiinterview/interview backend/src/main/java/com/a05/aiinterview/questionbank/service/QuestionBankService.java backend/src/main/java/com/a05/aiinterview/rag/dto/RagRetrievalRequest.java
git commit -m "refactor: unify persisted question focus on focus point"
```

## Chunk 5: Keep Bailian As Default Operation While Cleaning Config Semantics

### Task 5: 统一成“`AI_BAILIAN_API_KEY` 为默认入口 + OpenAI-compatible 兼容层保留”

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `backend/README.md`
- Modify: `backend/src/main/java/com/a05/aiinterview/speech/service/TtsService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`（仅注释/默认值说明，如需要）
- Test: `backend/src/test/java/com/a05/aiinterview/ApplicationProfileConfigTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/LocalProfileRedisConfigTest.java`

- [ ] **Step 1: 写失败测试**

锁定方向：

```java
assertThat(localYaml).contains("AI_BAILIAN_API_KEY");
assertThat(readme).contains("AI_BAILIAN_API_KEY");
assertThat(localYaml).contains("OPENAI_BASE_URL");
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q "-Dtest=ApplicationProfileConfigTest,LocalProfileRedisConfigTest" test`

- [ ] **Step 3: 写最小实现**

统一规则：

- 新手默认入口：`AI_BAILIAN_API_KEY`
- 兼容层回退：`${AI_BAILIAN_API_KEY:${OPENAI_API_KEY:...}}`
- `application-local.yml` 保持百炼兼容模式默认值
- README 明确：
  - 默认按百炼走
  - 若你用别的 OpenAI-compatible 服务，再改 `OPENAI_BASE_URL / OPENAI_API_KEY / OPENAI_MODEL`
- 错误提示改成同时提示：
  - `AI_BAILIAN_API_KEY`
  - 或兼容层 `OPENAI_API_KEY`

- [ ] **Step 4: 文档清理**

去掉“必须把 `AI_BAILIAN_API_KEY` 移出主设计”的旧说法；同时也避免再出现“只认 OpenAI_*”的口径。

- [ ] **Step 5: 复跑测试**

Run: `mvn -q "-Dtest=ApplicationProfileConfigTest,LocalProfileRedisConfigTest" test`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/application.yml backend/src/main/resources/application-local.yml .env.example README.md backend/README.md backend/src/main/java/com/a05/aiinterview/speech/service/TtsService.java backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java
git commit -m "refactor: keep bailian as default ai operation config"
```

## Final Verification

- [ ] **Step 1: 跑定向 grep 验收**

```bash
rg -n "旧岗位码|旧知识点码A|旧知识点码B|项目落地伪域值\\b|positionCode\\b|position_code\\b|旧题目焦点字段\\b|focus_point_old\\b" backend docs README.md .env.example
```

额外抽查：

```bash
rg -n "AI_BAILIAN_API_KEY|OPENAI_API_KEY|OPENAI_BASE_URL|intro\\b|behavioral\\b" backend docs README.md .env.example
```

预期：

- `旧岗位码|旧知识点码A|旧知识点码B|项目落地伪域值` 无当前主链路匹配
- `positionCode|position_code|旧题目焦点字段|focus_point_old` 最终清理后无当前主链路匹配
- `AI_BAILIAN_API_KEY` 仍作为默认入口存在于 README / `.env.example` / local config
- `intro` 仍只作为受控例外存在，不再和 `behavioral/项目落地伪域值` 并列当成“伪 domain 清理项”

- [ ] **Step 2: 跑全量回归**

Run: `mvn -q test`

- [ ] **Step 3: SQL / 配置抽查**

抽查点：

- `position_skill_domains` 中 Java 后端仍为 10 个域
- `interview_questions`：
  - `INTRO` 题当前仍允许 `domain_code=intro`
  - `BEHAVIORAL / PROJECT_DEEP_DIVE` 不再写伪 domainCode
- `question_bank` / `currentQuestion` / SSE `done` 使用 `focusPoint`
- 本地联调文档默认走 `AI_BAILIAN_API_KEY`

- [ ] **Step 4: 最终整理提交**

建议每个 Chunk 单独 commit，不压成一个大 commit。

## Risks To Watch

- 如果直接删除 `intro`，会同时打坏首题快照、详情页、单题评估、问答库和报告测试
- 如果只改 docs 不改运行态，`behavioral/项目落地伪域值` 仍会污染知识域统计
- 如果第 3 项只改 DTO 不改 schema，`positionCode/positionCode` 会形成第二层桥接
- 如果第 4 项只改 DTO 不改落库字段，`focusPoint/旧题目焦点字段` 会长期双写
- 如果第 5 项只保留 `AI_BAILIAN_API_KEY` 不保留兼容回退，会把 OpenAI-compatible 可扩展性一起删掉

## Suggested Execution Order

1. 先做 Task 1：修 RAG 样例数据污染源
2. 再做 Task 2：只清理 `behavioral/项目落地伪域值`，同时把 `intro` 例外文档补齐
3. 再做 Task 3：统一 `positionCode`
4. 然后做 Task 4：统一 `focusPoint`
5. 最后做 Task 5：保留百炼默认入口，清理 AI 配置语义



