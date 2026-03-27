# Java 后端知识域重构实施计划（MVP 破坏性重构版）

> **面向执行代理：** 实施本计划时必须使用 `superpowers:subagent-driven-development`（若支持子代理）或 `superpowers:executing-plans`。步骤统一使用复选框语法 `- [ ]` 追踪。

**目标：** 在开发阶段一次性把 `JAVA_BACKEND` 知识域模型重构到位，彻底放弃旧知识域版本、旧 `domainId` 身份和所有兼容桥接逻辑，只保留 `domainCode` 这一套主链路真源。

**架构：** 本计划默认项目仍处于 0→1 / MVP 阶段，历史会话和测试数据可以直接丢弃，因此采用破坏性重构策略。核心动作是：直接覆写 Java 后端知识域种子、把运行态和持久化统一到 `domainCode`、删掉所有 `domainId`/名称兜底/fallback 代码、清空开发库旧数据、让系统对非法旧数据直接 Fail Fast。

**技术栈：** Spring Boot、MyBatis-Plus、MySQL schema SQL、MyBatis XML Mapper、JSON 状态账本、Markdown Prompt 模板、JUnit 5 / AssertJ。

---

## 核心原则

1. **不做 v1/v2 并存**
   `JAVA_BACKEND` 知识域只保留一套最新版定义，不为旧版本保留并行配置。

2. **不做历史兼容**
   旧会话、旧题目、旧日志、旧账本都视为开发废料，不在主干代码中保留桥接层。

3. **系统只认 `domainCode`**
   从数据库、实体、DTO、Prompt、测试到账本，统一以 `domainCode` 作为知识域唯一身份。

4. **Fail Fast，不写兜底**
   遇到旧 shape、非法 code、脏 JSON，不做模糊修复，不做名称匹配，不做按 ID 猜测，直接报错或丢弃。

5. **先清库，再改主干**
   既然允许破坏性重构，就先清空开发环境历史数据，避免为了迁就脏数据污染主链路。

---

## 目标 `JAVA_BACKEND` 知识域集合

本轮重构后，`JAVA_BACKEND` 固定为这 10 个知识域：

- `java_core`：Java 基础
- `concurrency`：并发编程
- `jvm`：Java 虚拟机
- `mysql`：关系型数据库
- `redis`：分布式缓存
- `spring`：Spring 生态
- `mq`：消息队列
- `microservice`：微服务组件
- `distributed`：分布式综合
- `cs_basics`：计算机基础

### 设计约束

- 保留 `java_core`、`concurrency`、`jvm`、`mysql`、`redis`、`spring`、`mq`
- 删除 `system_design`
- 不把 `concurrency` 改成 `juc`
- 本轮不引入 `ai_infra`、`cloud_native`

### 边界说明

- `microservice`：注册中心、网关、RPC、Feign、熔断、限流、服务治理
- `distributed`：分布式锁、分布式事务、CAP/BASE、一致性、Seata
- `cs_basics`：TCP/IP、HTTP/HTTPS、进程线程、I/O 模型、Epoll/NIO、算法基础

---

## 文件范围

### 一次性真源改造

- 修改：`backend/src/main/resources/db/schema-position.sql`
- 修改：`backend/src/main/resources/db/schema-interview-v2.sql`
- 修改：`docs/db-schema.md`

### 持久化与实体改造

- 修改：`backend/src/main/java/com/a05/aiinterview/interview/entity/InterviewQuestion.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/entity/SessionSkillState.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/entity/InterviewSession.java`
- 修改：`backend/src/main/resources/mapper/interview/InterviewQuestionMapper.xml`
- 修改：`backend/src/main/resources/mapper/interview/InterviewSessionMapper.xml`
- 修改：与 `session_skill_states` 对应的 mapper / XML

### 运行态主链路改造

- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/PlannerDomainNormalizationService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/InterviewSyllabusAssembler.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/PlannerOrchestrationService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/StateLedgerInitService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/StateLedgerPatchService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/DefaultStateLedgerReducer.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/RemainingDomainMenuBuilder.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewSyllabus.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/dto/QuestionDto.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/dto/QuestionDtoAssembler.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionInput.java`

### Prompt、测试与文档

- 修改：`backend/src/main/resources/prompts/planner.md`
- 修改：`backend/src/main/resources/prompts/evaluation-decision.md`
- 修改：涉及 `DOMAIN_*`、`domainId`、ID fallback 的测试
- 删除：把旧 ID 匹配/名称匹配当成正确行为的测试
- 修改：`docs/archive/2026-03-25-doc-cleanup/面试流程策略.md`
- 修改：`docs/superpowers/specs/2026-03-20-planner-contract.md`
- 修改：`docs/superpowers/specs/2026-03-20-evaluation-decision-input-contract.md`

---

## 阶段 1：直接改真源，不保留旧版本

### 任务 1：直接覆写 `JAVA_BACKEND` 知识域种子

**文件：**
- 修改：`backend/src/main/resources/db/schema-position.sql`

- [ ] **步骤 1：删除旧的 `system_design` 域**

在 `schema-position.sql` 中直接去掉 `JAVA_BACKEND` 的 `system_design` 种子数据。

- [ ] **步骤 2：把 `JAVA_BACKEND` 原 8 个知识域原地改成最终 10 个**

这里不是新增 v2，也不是保留历史版本；而是直接让 `JAVA_BACKEND` 只剩一套最终定义。

- [ ] **步骤 3：更新 description**

每个知识域的 `description` 要写成“边界定义 + 代表考点”，供 planner/focus point 生成复用。

- [ ] **步骤 4：删除文档中的版本化叙述**

不再把本轮改造描述为“v2 发布”；文档统一描述为“当前 Java 后端知识域定义”。

- [ ] **步骤 5：提交**

```bash
git add backend/src/main/resources/db/schema-position.sql docs/db-schema.md
git commit -m "refactor: replace java backend domains with final set"
```

### 任务 2：清空开发库旧面试数据

**文件：**
- 执行对象：开发数据库

- [ ] **步骤 1：确认这是开发环境，不是共享正式环境**

本计划默认这是可清库的开发库。如果不是，先停下，不要执行破坏性清理。

- [ ] **步骤 2：优先选择“重建开发库”**

推荐直接重建本地数据库并重新执行初始化脚本，而不是在主代码里写一堆迁移兼容逻辑。

- [ ] **步骤 3：如不重建整库，则清空实际面试表**

需要清理的是真实表，而不是不存在的 `session_table`：

- `interview_sessions`
- `session_skill_states`
- `interview_questions`
- `interview_attempts`
- `question_redo_attempts`
- `interview_reports`
- `ai_invocation_logs`

- [ ] **步骤 4：提交**

如果新增了本地开发重建说明或脚本，则提交；否则此步无代码提交。

---

## 阶段 2：数据库与代码彻底只认 `domainCode`

### 任务 3：把持久化层从 `domain_id` 改成 `domain_code`

**文件：**
- 修改：`backend/src/main/resources/db/schema-interview-v2.sql`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/entity/InterviewQuestion.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/entity/SessionSkillState.java`
- 修改：`backend/src/main/resources/mapper/interview/InterviewQuestionMapper.xml`
- 修改：相关 mapper / XML / DTO / assembler

- [ ] **步骤 1：修改数据库字段**

把以下持久化字段改为 code 口径：

- `session_skill_states.domain_id` -> `domain_code`
- `interview_questions.domain_id` -> `domain_code`
- `interview_questions.secondary_domain_ids` -> `secondary_domain_codes`

- [ ] **步骤 2：移除实体中的 `domainId`**

`InterviewQuestion`、`SessionSkillState`、相关 DTO 和 mapper 全部改为 `domainCode` / `secondaryDomainCodes`。

- [ ] **步骤 3：清理 `position_domain_version`**

如果本轮确认不再保留多版本语义，则从 schema、entity、mapper、注释中一起删掉 `position_domain_version`。

- [ ] **步骤 4：让旧 SQL / 映射直接失效**

不保留兼容 XML，不做双字段并存。旧列名一旦删掉，所有没改到的地方直接在编译或运行时报错。

- [ ] **步骤 5：提交**

```bash
git add backend/src/main/resources/db/schema-interview-v2.sql backend/src/main/java/com/a05/aiinterview/interview/entity/InterviewQuestion.java backend/src/main/java/com/a05/aiinterview/interview/entity/SessionSkillState.java backend/src/main/resources/mapper/interview/InterviewQuestionMapper.xml
git commit -m "refactor: persist interview domains by domain code only"
```

### 任务 4：屠宰所有知识域兼容桥接逻辑

**文件：**
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/PlannerDomainNormalizationService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/InterviewSyllabusAssembler.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/PlannerOrchestrationService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/StateLedgerInitService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/StateLedgerPatchService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/DefaultStateLedgerReducer.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java`
- 修改：`backend/src/main/java/com/a05/aiinterview/interview/dto/QuestionDtoAssembler.java`

- [ ] **步骤 1：删除 `byIdText`、按名称匹配、按旧 ID 猜测的逻辑**

任何 `domainCode <- domainId`、`domainCode <- domainName` 的修复逻辑都从主链路移除。

- [ ] **步骤 2：删除旧题目兼容桥**

不再允许“如果旧题没有 `domainCode`，就查一次 syllabus 或 `domainId` 补齐”。

- [ ] **步骤 3：删除 `covered_domains` 的字符串兼容**

`covered_domains` 必须是结构化对象并包含合法 `domainCode`；字符串型脏数据直接判非法。

- [ ] **步骤 4：保留 Fail Fast**

当题目、账本、考纲中出现空 `domainCode` 或非法 `domainCode` 时，直接抛异常或拒绝继续，而不是偷偷兜底。

- [ ] **步骤 5：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/engine/PlannerDomainNormalizationService.java backend/src/main/java/com/a05/aiinterview/interview/engine/InterviewSyllabusAssembler.java backend/src/main/java/com/a05/aiinterview/interview/engine/PlannerOrchestrationService.java backend/src/main/java/com/a05/aiinterview/interview/engine/StateLedgerInitService.java backend/src/main/java/com/a05/aiinterview/interview/engine/StateLedgerPatchService.java backend/src/main/java/com/a05/aiinterview/interview/engine/DefaultStateLedgerReducer.java backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitService.java backend/src/main/java/com/a05/aiinterview/interview/service/QuestionStreamService.java backend/src/main/java/com/a05/aiinterview/interview/dto/QuestionDtoAssembler.java
git commit -m "refactor: remove all legacy domain fallback paths"
```

---

## 阶段 3：统一 AI 契约、Prompt、测试

### 任务 5：统一 AI 输入输出契约为 `domainCode`

**文件：**
- 修改：`backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionInput.java`
- 修改：相关题目生成输入 DTO
- 修改：`backend/src/main/resources/prompts/planner.md`
- 修改：`backend/src/main/resources/prompts/evaluation-decision.md`

- [ ] **步骤 1：删掉 AI 侧 `domainId` 表达**

`CurrentQuestionContext`、`RecentInterviewMemoryItem`、相关输入 DTO 只保留 `domainCode` / `domainName`。

- [ ] **步骤 2：Prompt 示例全部使用真实小写 code**

把 `DOMAIN_REDIS`、`DOMAIN_SPRING` 这类伪枚举从 Prompt 和测试里彻底删掉。

- [ ] **步骤 3：Prompt 中明确写死规则**

告诉模型：`domainCode` 必须直接来自系统给定的配置集合，不能输出 ID、序号、中文名或自造 code。

- [ ] **步骤 4：提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionInput.java backend/src/main/resources/prompts/planner.md backend/src/main/resources/prompts/evaluation-decision.md
git commit -m "refactor: align ai contracts and prompts to domain code only"
```

### 任务 6：测试直接清旧补新

**文件：**
- 修改：相关 domain 测试
- 删除：把旧 fallback 当成正确行为的测试

- [ ] **步骤 1：删除旧 ID/name fallback 测试**

以下类型的测试直接删掉，不保留“兼容历史脏数据”的单测：

- `byIdText`
- `fallback to domain id or name`
- 旧 `domainId` 补齐逻辑
- 旧字符串型 `covered_domains` 容忍逻辑

- [ ] **步骤 2：新增或保留强校验测试**

只保留这类测试：

- 非法 `domainCode` 被拒绝
- Prompt 只出现真实 code
- planner 只接受配置中的 code
- ledger / question / evaluation 输入只认 `domainCode`

- [ ] **步骤 3：把伪 code 测试数据改成真实值**

统一把 `DOMAIN_REDIS`、`DOMAIN_MYSQL`、`DOMAIN_SPRING` 等替换为真实配置 code。

- [ ] **步骤 4：提交**

```bash
git add backend/src/test/java
git commit -m "test: drop legacy domain fallback coverage and enforce canonical codes"
```

---

## 阶段 4：文档与验证

### 任务 7：文档收口，不保留旧方案指导意义

**文件：**
- 修改：`docs/db-schema.md`
- 修改：`docs/superpowers/specs/2026-03-20-planner-contract.md`
- 修改：`docs/superpowers/specs/2026-03-20-evaluation-decision-input-contract.md`
- 修改：`docs/archive/2026-03-25-doc-cleanup/面试流程策略.md`

- [ ] **步骤 1：删除“历史兼容”“版本桥接”描述**

文档统一按当前唯一实现写，不为已丢弃方案保留主设计地位。

- [ ] **步骤 2：统一说明 `domainCode` 是唯一真源**

`domainName` 是展示字段，`domainId` 不再是主链路概念。

- [ ] **步骤 3：写清当前 Java 后端的 10 个域**

不要只改名称，要把边界和代表性考点写出来。

- [ ] **步骤 4：提交**

```bash
git add docs/db-schema.md docs/superpowers/specs/2026-03-20-planner-contract.md docs/superpowers/specs/2026-03-20-evaluation-decision-input-contract.md docs/archive/2026-03-25-doc-cleanup/面试流程策略.md
git commit -m "docs: rewrite domain docs for destructive refactor"
```

### 任务 8：跑最小必要验证

**文件：**
- 上述相关测试套件

- [ ] **步骤 1：运行定向测试**

```bash
mvn -q "-Dtest=PlannerDomainNormalizationServiceTest,InterviewSyllabusAssemblerTest,RemainingDomainMenuBuilderTest,AnswerSubmitServiceEvaluationInputTest,PromptTemplateCoverageTest,EvaluationDecisionContractTest" test
```

- [ ] **步骤 2：grep 确认旧模式已被物理清除**

```bash
rg -n "DOMAIN_[A-Z_]+|domainId|domain_id|secondary_domain_ids|byIdText|fallback to domain id or name|system_design" backend/src/main backend/src/test/java docs
```

预期：

- 主链路代码中不再出现 `domainId` / `domain_id`
- 运行时代码中不再出现 `byIdText`
- Prompt 和测试中不再出现 `DOMAIN_*`
- `system_design` 只允许出现在少量历史背景说明中；若无必要，连文档里也一起删

- [ ] **步骤 3：冒烟检查 schema**

确认：

- `schema-position.sql` 中 `JAVA_BACKEND` 只剩当前一套 10 域定义
- `schema-interview-v2.sql` 中知识域相关列已切为 `domain_code`

- [ ] **步骤 4：写最终发布说明**

说明只需要包含三点：

- 开发环境已经清库
- 主链路只认 `domainCode`
- Java 后端知识域已经固定为新的 10 域

---

## 风险与取舍

- 这份计划明确选择“开发期破坏性重构”，不适用于已有真实生产用户和不可丢弃历史数据的系统。
- 如果执行过程中发现某些功能仍然暗依赖 `domainId`，不要补桥接；直接继续往上游改，直到整条链路只认 `domainCode`。
- 如果开发库并非你个人可控环境，不要先斩后奏清库，应先确认范围。
- 如果后面真的出现真实用户和历史包袱，再单独设计迁移方案，不要提前把迁移复杂度预埋到当前主干。

---

## 完成标准

- `JAVA_BACKEND` 只保留一套当前知识域定义，不存在 v1/v2 并存
- `system_design` 被物理删除
- 持久化、实体、DTO、Prompt、测试、账本统一只认 `domainCode`
- 所有 `domainId` / 名称兜底 / ID fallback / 脏 JSON 兼容逻辑被删除
- 开发环境历史面试数据被清空或开发库被重建
- 定向测试通过，grep 看不到主链路旧术语
