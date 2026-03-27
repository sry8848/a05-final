# Evaluation Decision 输入契约

## 背景

当前 `evaluation-decision` 输入已经统一到 `domainCode` 单一身份。本文档只保留当前仍有效的 DTO 结构和字段语义。

## 核心原则

- 不做旧字段兼容。
- prompt 输入 DTO 与后端运行态分层设计。
- 当前题、历史题、剩余知识域菜单统一使用 `domainCode` 和 `domainName`。
- 知识域状态只保留 `UNASKED` 和 `COVERED` 两档。

## 业务 DTO

```java
public class EvaluationDecisionInput {
    private InterviewMeta interview;
    private List<ProjectAndInternshipItem> projectAndInternshipSummary;
    private InterviewGoalSummary interviewGoalSummary;
    private List<String> coveredKnowledgeSummary;
    private Map<String, QuotaCounter> quotaSnapshot;

    private CurrentQuestionContext currentQuestion;
    private String answerText;
    private List<String> expectedPoints;

    private List<AvailableStrategy> availableStrategies;
    private List<RemainingTargetDomain> remainingTargetDomains;
    private List<RetrievedMaterial> retrievedMaterials;
    private List<RecentInterviewMemoryItem> recentInterviewMemory;
}
```

## 嵌套结构

### `InterviewMeta`

```java
public class InterviewMeta {
    private String positionCode;
    private String experienceLevel;
    private String roundType;
}
```

### `ProjectAndInternshipItem`

```java
public class ProjectAndInternshipItem {
    private String itemType;
    private String itemName;
    private String resumeDescription;
    private List<String> techHooks;
    private List<String> blockedEntryPoints;
}
```

### `InterviewGoalSummary`

```java
public class InterviewGoalSummary {
    private List<GoalDomainItem> domains;
}

public class GoalDomainItem {
    private String domainCode;
    private String domainName;
    private List<String> focusPoints;
    private String status;
}
```

### `CurrentQuestionContext`

```java
public class CurrentQuestionContext {
    private String stem;
    private String questionType;
    private String domainCode;
    private String domainName;
    private String currentFocus;
    private String relatedItemType;
    private String relatedItemName;
}
```

### `RecentInterviewMemoryItem`

```java
public class RecentInterviewMemoryItem {
    private Integer questionNo;
    private String questionType;
    private String domainCode;
    private String domainName;
    private String focusPoint;
    private String relatedItemType;
    private String relatedItemName;
    private String questionStem;
    private String answerSummary;
    private String answerAssessment;
}
```

### `RemainingTargetDomain`

```java
public class RemainingTargetDomain {
    private String domainCode;
    private String domainName;
    private List<String> focusPoints;
}
```

## 渲染规则

- `currentQuestion`、`remainingTargetDomains`、`recentInterviewMemory` 直接以结构化 JSON 传给 prompt。
- `domainCode` 必须使用真实小写编码。
- 不允许把知识域身份退化成数字编号、中文名或自由文本。

## 运行态专属数据

以下信息允许留在后端运行态，但不直接进入 prompt DTO：

- 项目稳定身份，例如 `itemKey`
- 账本内部计数器
- 不适合直接暴露给模型的底层 patch 细节

## 当前结论

- 评估输入所有知识域位置都只认 `domainCode`
- `domainName` 只承担展示和可读性
- 旧知识域主键字段不再属于当前输入契约
