# Planner 契约设计

## 背景

当前 `planner` 链路已经收口到一套单一契约：

- 候选人资历使用 `experienceLevel`
- 知识域唯一身份使用 `domainCode`
- 展示文本使用 `domainName`
- 运行态和持久化不再传播旧知识域主键字段

本文档只记录当前仍然有效的 `planner` 输入、输出和 `syllabus` 运行态结构。

## 核心原则

- 不做新旧双轨兼容。
- `planner` 只负责规划“本次该考什么、从哪些经历切入”。
- `planner` 输入中的知识域列表只提供 `domainCode` 和 `domainName`。
- `InterviewSyllabus` 只保留运行态真正需要的字段，不再补写旧整数主键。

## `PlannerInput`

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

### `DomainInfo`

```java
public class DomainInfo {
    private String domainCode;
    private String domainName;
}
```

约束：

- `domainCode` 必须直接来自岗位知识域配置。
- 当前唯一例外是 `INTRO -> intro`，仅用于首题自我介绍快照。
- `BEHAVIORAL` 和 `PROJECT_DEEP_DIVE` 不允许伪造额外知识域编码。
- `domainName` 只作为展示字段。
- prompt 不允许输出输入列表之外的编码。

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

## `PlannerOutput`

```java
public class PlannerOutput {
    private String planningReasoning;
    private List<DomainPlan> domains;
    private List<PlannerExperienceItem> experienceItems;
}
```

### `DomainPlan`

```java
public class DomainPlan {
    private String domainCode;
    private String domainName;
    private List<String> focusPoints;
}
```

### `PlannerExperienceItem`

```java
public class PlannerExperienceItem {
    private String itemType;
    private String itemName;
    private String resumeDescription;
    private List<String> techHooks;
}
```

约束：

- `domainCode` 必须复用输入知识域列表中的原始编码。
- `focusPoints` 必须是可追问、可判断的具体焦点。
- `experienceItems` 只保留项目或实习的真实入口，不承载运行态内部键。

## `InterviewSyllabus`

`PlannerOutput` 不直接原样写入 `session.syllabusJson`。后端会组装成自己的运行态结构：

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

运行态规则：

- `itemKey` 由后端生成，作为稳定经历身份。
- `itemKey` 不进入 prompt DTO。
- `domains` 的唯一身份始终是 `domainCode`。
- 列表顺序天然表达优先级，不额外引入 `priority`。

## Prompt 命名约束

`planner.md` 中的变量名必须与 DTO 语义一致：

- `roundType`
- `experienceLevel`
- `domains`
- `experienceItems`

并且 prompt 中需要明确约束：

- 当 `roundType` 为空时，不允许模型臆造轮次背景。
- 知识域只能从输入列表中选择。
- 编码必须使用真实小写配置编码。

## 第一版降级策略

当前仍允许：

- `roundType=""`
- `historyInterviews=[]`

但这只是链路初期的输入简化，不改变知识域身份规则。

## 当前结论

- `planner` 输入、输出、运行态全部只认 `domainCode`
- `domainName` 只是展示字段
- 旧知识域主键字段不再属于当前契约
