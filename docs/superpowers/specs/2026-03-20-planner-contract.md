# Planner 契约设计

## 背景

本分支的面试提问逻辑已经切换到新的 `planner` 提示词设计，不再需要兼容旧版 `PlannerOutput` 契约。旧契约中的题型配额、目标深度、旧项目锚点等字段，如果已经不服务当前新设计，就应直接删除。

这份文档用于锁定新的 `planner` 契约，避免在后续实现 `planner`、`syllabus` 组装、`evaluation-decision` 输入构建时反复变更字段定义。

## 范围

本文档定义以下内容：

- `PlannerInput` 的业务输入结构
- `PlannerOutput` 的 AI 原始输出结构
- 后端运行态 `syllabus` 的持久化结构
- 当前阶段允许的空值和降级策略

本文档暂不定义以下内容：

- `evaluation-decision` 输出契约
- 题目生成链路最终消费 `syllabus` 的完整逻辑
- 完整的 state ledger 最终结构
- 历史去重的真实数据抽取逻辑

## 核心原则

- 本分支不做新旧双轨兼容。旧 `PlannerOutput` 字段如果不再符合新设计，就直接删除。
- AI 输出和后端运行态结构分层处理，不让 prompt 为运行态机器字段负责。
- `planner` 的 prompt 输出只负责表达“本次该考什么、从哪些经历切入”，不负责生成后端内部稳定标识。
- `itemType` 只表达经历类型，不能替代具体经历身份。
- 当前后端还没有真实 `roundType` 来源，因此第一版允许 `roundType=""`。
- 当前后端还没有真实历史面试摘要输入，因此第一版允许 `historyInterviews=[]`。

## 整体分层

新的 `planner` 相关数据分为三层：

1. `PlannerInput`
   面向 prompt 的业务输入 DTO
2. `PlannerOutput`
   AI 原始输出 DTO，与 prompt schema 对齐
3. `InterviewSyllabus`
   后端运行态和持久化使用的结构，由 `PlannerOutput` 组装得到

这三层不能混为一谈。

## `PlannerInput` 设计

```java
public class PlannerInput {
    private Long interviewId;
    private String positionName;
    private String positionCode;
    private String experienceLevel;
    private String roundType;
    private String mode;
    private String jobDescription;
    private String resumeText;
    private String focusTopics;
    private List<DomainInfo> domains;
    private List<HistoryInterviewItem> historyInterviews;
}
```

### 字段说明

- `interviewId`
  只用于审计和日志透传，不属于 prompt 业务语义。

- `positionName`
  岗位中文名或用户可读名称，用于 prompt 中展示岗位。

- `positionCode`
  岗位编码，例如 `JAVA_BACKEND`。

- `experienceLevel`
  候选人经验层级，用于控制大纲难度。

- `roundType`
  面试轮次，例如技术一面、二面、主管面等。当前阶段后端没有真实来源，因此第一版传空字符串 `""`。

- `mode`
  面试模式，例如 `practice` / `professional`。它不等于 `roundType`，不能替代 `roundType`。

- `jobDescription`
  JD 原文。

- `resumeText`
  简历解析文本。

- `focusTopics`
  用户希望重点考察的内容。

- `domains`
  岗位可选知识域列表，由后端提供。

- `historyInterviews`
  历史面试摘要列表。当前阶段第一版统一传空数组 `[]`。

### `DomainInfo`

```java
public class DomainInfo {
    private Long domainId;
    private String domainCode;
    private String domainName;
}
```

约束：

- `domainId` 是后端知识域主键，仅用于后续运行态组装。
- prompt 关注的是 `domainCode` 和 `domainName`，但 `domainId` 在输入 DTO 中保留，便于映射。

### `HistoryInterviewItem`

```java
public class HistoryInterviewItem {
    private String roundType;
    private String interviewAt;
    private List<String> coveredKnowledgePoints;
    private List<HistoryExperienceItem> discussedItems;
    private List<String> strongPoints;
    private List<String> weakPoints;
}
```

### `HistoryExperienceItem`

```java
public class HistoryExperienceItem {
    private String itemType;
    private String itemName;
    private List<String> entryPoints;
}
```

约束：

- 这套结构先保留在 DTO 里，但第一版统一传空数组 `[]`。
- 后续历史去重真正接入时，优先补充数据组装逻辑，而不是再次变更字段结构。

## Prompt 变量命名约束

`planner.md` 中相关变量命名应与 DTO 语义保持一致：

- 原 `turn` 改为 `roundType`
- 原 `projects/projectName` 改为 `experienceItems/itemName`

并且 prompt 中需要明确约束：

- 当 `roundType` 为空时，不允许模型臆造轮次背景，只能基于 `experienceLevel`、JD、简历、自定义重点和历史数据进行规划

## `PlannerOutput` 设计

```java
public class PlannerOutput {
    private String planningReasoning;
    private List<PlannerDomain> domains;
    private List<PlannerExperienceItem> experienceItems;
}
```

### `PlannerDomain`

```java
public class PlannerDomain {
    private String domainCode;
    private String domainName;
    private List<String> focusPoints;
}
```

### `PlannerExperienceItem`

```java
public class PlannerExperienceItem {
    private String itemType; // PROJECT / INTERNSHIP
    private String itemName;
    private String resumeDescription;
    private List<String> techHooks;
}
```

### 输出语义

- `planningReasoning`
  极简说明本次考纲规划逻辑，控制在 prompt 要求的长度内。

- `domains`
  表达本次值得重点考察的知识域和知识点。

- `experienceItems`
  表达本次适合从哪些真实项目或实习经历切入。

### 已确认删除的旧字段

以下旧字段不再保留：

- `title`
- `questionMixPlan`
- `focusAreas`
- `priority`
- `projectId`
- `bizGoal`
- `role`
- `techStack`
- `responsibilities`
- `hardPoints`
- `metrics`
- `personalContribution`

删除原因：

- 这些字段属于旧版题型配额和项目锚点设计，不再符合当前新 prompt 的职责边界。
- 它们要么已经不再需要，要么应由后端运行态生成，而不是继续要求 AI 输出。

## 运行态 `InterviewSyllabus` 设计

`PlannerOutput` 不应直接原样写入 `session.syllabusJson`。后端应在持久化前做一次组装，形成自己的运行态结构。

```java
public class InterviewSyllabus {
    private String planningReasoning;
    private List<SyllabusDomain> domains;
    private List<SyllabusExperienceItem> experienceItems;
}
```

### `SyllabusDomain`

```java
public class SyllabusDomain {
    private Long domainId;
    private String domainCode;
    private String domainName;
    private List<String> focusPoints;
}
```

### `SyllabusExperienceItem`

```java
public class SyllabusExperienceItem {
    private String itemKey;
    private String itemType;
    private String itemName;
    private String resumeDescription;
    private List<String> techHooks;
}
```

### 运行态规则

- `itemKey` 由后端生成，作为稳定经历身份。
- `itemKey` 不进入 prompt DTO，也不要求 AI 输出。
- `domains` 中的 `domainId` 由后端根据 `domainCode` 或输入域表映射补齐。
- 列表顺序本身就表达优先级，不再额外引入 `priority` 字段。

## 与 `evaluation-decision` 的衔接

后续 `evaluation-decision` 输入契约中的以下字段应直接来自 `InterviewSyllabus`：

- `projectAndInternshipSummary` <- `syllabus.experienceItems`
- `interviewGoalSummary` <- `syllabus.domains` + 运行态进度

因此 `PlannerOutput` 保持轻量，`InterviewSyllabus` 保持运行态可消费，能够减少后续返工。

## 第一版降级策略

为尽快跑通新链路，当前阶段允许以下降级：

- `roundType=""`
- `historyInterviews=[]`

但必须明确：

- 这只是第一版的临时取舍，不是最终设计完成态
- 由于 `roundType` 为空，模型按轮次动态调难度的能力会被削弱
- 由于 `historyInterviews=[]`，模型无法做真正的历史去重，只能先依赖本场运行态

## 实现顺序

建议按照以下顺序落地：

1. 修改 `planner.md`，让 prompt 变量名和输出 schema 与本文档一致
2. 修改 `PlannerInput` 和 `PlannerOutput` DTO
3. 新增 `Planner -> InterviewSyllabus` 组装层
4. 修改 `PlannerOrchestrationService` 持久化 `syllabus` 的逻辑
5. 修改 `StateLedgerInitService`，去掉对 `questionMixPlan`、旧难度字段等旧字段的依赖
6. 补充最小契约测试和组装测试

## 待后续确认的问题

- `positionName` 是否统一为中文名，还是允许英文名
- 答：中文
- `planningReasoning` 是否最终保留在 `syllabusJson`
- 答：保留
- `HistoryInterviewItem` 的真实数据来源和抽取粒度
- 答：暂不考虑
- `StateLedgerInitService` 第一版最小初始化结构具体保留哪些字段
