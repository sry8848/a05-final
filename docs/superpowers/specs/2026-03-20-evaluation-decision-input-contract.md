# Evaluation Decision 输入契约

## 背景

本分支正在围绕新的提示词重构面试提问逻辑，不需要兼容旧版 `evaluation-decision` 契约。旧字段如果已经不再服务新逻辑，就应该删除，而不是继续保留。

这份文档用于记录当前已经确认的 `evaluation-decision` 输入契约，后续 `planner` 和后端重构都以它为准，避免口头约定在实现过程中漂移。

## 范围

本文档定义以下内容：

- `EvaluationDecisionInput` 的业务 DTO 结构
- 每个字段的语义
- 哪些字段是 prompt 直接消费的，哪些字段只属于运行态
- 当前已经确认的限额计数语义

本文档暂不定义以下内容：

- 最终版 `EvaluationDecisionOutput`
- 完整的 `planner` 输出契约
- 最终的 state ledger patch 模型
- 实际的 RAG 执行流程

## 核心原则

- 本分支不做新旧双轨兼容。旧字段如果对新提示词逻辑无用，就直接删除。
- `EvaluationDecisionInput` 必须保持结构化，不能把大部分内容降级成自由文本字符串。
- prompt 输入 DTO 和后端运行态字段是两类不同职责，不能混在一起设计。
- `roundType` 是独立字段，不能用旧设计里语义不一致的字段硬凑，例如 `mode`。
- `interviewGoalSummary` 的状态只保留 `UNASKED` 和 `COVERED`，删除 `IN_PROGRESS`。

## 业务 DTO 结构

```java
public class EvaluationDecisionInput {
    private InterviewMeta interview;
    private List<ProjectAndInternshipItem> projectAndInternshipSummary;
    private InterviewGoalSummary interviewGoalSummary;
    private List<String> coveredKnowledgeSummary;
    private QuotaSummary quotaSummary;

    private CurrentQuestionContext currentQuestion;
    private String answerText;
    private List<String> expectedPoints;

    private List<String> possibleFutureDirections;
    private List<RetrievedMaterial> retrievedMaterials;
    private List<RecentInterviewMemoryItem> recentInterviewMemory;
}
```

## 嵌套结构定义

### `InterviewMeta`

```java
public class InterviewMeta {
    private String positionCode;
    private String experienceLevel;
    private String roundType;
}
```

约束：

- `roundType` 必须独立表达。
- `experienceLevel` 可以由简历年限或后端已有的候选人分级结果推导出来。

### `ProjectAndInternshipItem`

```java
public class ProjectAndInternshipItem {
    private String itemType; // PROJECT / INTERNSHIP
    private String itemName;
    private String resumeDescription;
    private List<String> techHooks;
}
```

约束：

- `itemType` 来自新的 `planner` prompt 输出。
- prompt 输入 DTO 不需要 `itemId`。
- 但后端运行态仍然需要稳定身份，用于项目级连续计数和上下文延续。这个身份建议以后端内部字段存在，例如 `itemKey`，但不放进 prompt DTO。
- `itemType` 只能表示“这是项目还是实习”，不能替代具体项目身份。

### `InterviewGoalSummary`

```java
public class InterviewGoalSummary {
    private List<GoalDomainItem> domains;
}

public class GoalDomainItem {
    private Long domainId;
    private String domainCode;
    private String domainName;
    private List<String> focusPoints;
    private String status; // UNASKED / COVERED
}
```

约束：

- 这部分不能简单等于 `planner` 原始输出。
- 它必须是“规划目标 + 当前运行进度”的合并结果。
- `status` 只允许 `UNASKED` 和 `COVERED`。
- 当前正在问的内容由 `currentQuestion` 表达，不再额外引入第三种 domain 状态。

### `coveredKnowledgeSummary`

```java
private List<String> coveredKnowledgeSummary;
```

约束：

- 这里保持字符串数组，不强制对象化。
- 每一项都要有稳定、可读的格式，不能只塞孤立关键词。
- 推荐格式示例：
  - `Redis / 缓存击穿基础方案`
  - `MySQL / 事务隔离级别`
  - `项目 / 订单超时关闭链路`

### `QuotaSummary`

```java
public class QuotaSummary {
    private ConsecutiveLimit samePointContinue;
    private ConsecutiveLimit sameDomainContinue;
    private ConsecutiveLimit sameProjectPointContinue;
    private ConsecutiveLimit sameProjectContinue;
    private TotalLimit sameTypeTotal;
}

public class ConsecutiveLimit {
    private Integer count;
    private Integer maxCount;
}

public class TotalLimit {
    private Integer count;
    private Integer maxCount;
}
```

已确认语义：

- `samePointContinue`：同一知识点连续追问次数
- `sameDomainContinue`：同一知识域连续追问次数
- `sameProjectPointContinue`：同一项目知识点连续追问次数
- `sameProjectContinue`：同一项目连续追问次数
- `sameTypeTotal`：整场面试中同一题型的累计次数

命名约束：

- 所有连续计数统一使用 `Continue`
- 只有题型累计计数使用 `Total`

### `CurrentQuestionContext`

```java
public class CurrentQuestionContext {
    private String stem;
    private String questionType;
    private Long domainId;
    private String domainName;
    private String currentFocus;
    private String currentTargetDepth;
    private String relatedItemType; // nullable
    private String relatedItemName; // nullable
}
```

约束：
- 当当前题目绑定到具体项目或实习时，使用 `relatedItemType` 和 `relatedItemName` 表达。

### `possibleFutureDirections`

```java
private List<String> possibleFutureDirections;
```

约束：

- 这里保持字符串数组。
- 每项内容要短、稳定、可读。

### `RetrievedMaterial`

```java
public class RetrievedMaterial {
    private String retrievalType;
    private String retrievalGoal;
    private String query;
    private List<String> materials;
}
```

约束：

- 即使当前阶段暂缓 RAG 执行，这个字段也要保留在契约里。
- 在最早可运行版本中，它可以是空数组。
- 后续真正接 RAG 时，应和最终传入评估 prompt 的检索结果结构保持一致。

### `RecentInterviewMemoryItem`

```java
public class RecentInterviewMemoryItem {
    private Integer questionNo;
    private String questionType;
    private Long domainId;
    private String domainName;
    private String focusPoint;
    private String relatedItemType; // nullable
    private String relatedItemName; // nullable
    private String questionStem;
    private String answerSummary;
    private String answerAssessment;
}
```

约束：

- 这是结构化历史，不是大段自由文本。
- 必须包含本场面试历史题目、回答概要以及回答评价。
- `answerAssessment` 表示供后续 prompt 推理使用的简短评估结论。

## 渲染规则

- `answerText` 直接传用户原始回答。
- 结构化字段优先渲染为 JSON，而不是提前展开成松散 prose。
- `expectedPoints`、`coveredKnowledgeSummary`、`possibleFutureDirections` 这类数组，也可以直接渲染成 JSON array。
- `outputSchema` 不属于业务 DTO，它是 prompt 渲染层的技术字段，应由 AI client 注入。

## 运行态专属数据

下面这些数据允许存在于后端运行态，但不应直接混进 prompt DTO，除非 prompt 明确需要：

- 项目或实习项的稳定身份，例如 `itemKey`
- 用于计算 `QuotaSummary` 的内部计数器
- 过于底层、过于冗长、不适合直接暴露给 prompt 的 ledger 细节

这些字段可以存在于 service、state、persistence 层，但不应污染 prompt 输入结构。

## 上游依赖

虽然这份契约先被记录下来，但它的最终落地依赖新的 `planner` 契约：

- `projectAndInternshipSummary` 要和 planner 输出项对齐
- `interviewGoalSummary` 要合并 planner 规划结果和运行态进度
- 部分 quota 上下文也可能依赖 planner 如何定义提问推进逻辑

因此实现顺序应为：

1. 先锁定这份输入契约
2. 再改 `planner` 契约及其消费链路
3. 再实现 `EvaluationDecisionInput` builder 和 prompt 渲染
4. 最后进入 `EvaluationDecisionOutput`

## 待后续确认的问题

- `retrievalType` 后续是否要收敛成枚举，例如 `QUESTIONS`、`DOMAIN`
- 答：要
- `experienceLevel` 是由 planner 提供，还是完全由后端推导
- 答：面试准备页面填写，枚举值为实习/应届/1-3年/3-5年/5年以上
- `possibleFutureDirections` 后续是否升级为结构化建议，而不是简单字符串
- 答：不用升级
