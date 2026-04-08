# Structured Output Format Authority 设计说明

## 背景

当前项目已经接入 Spring AI `BeanOutputConverter`，但结构化输出链路没有遵守单一事实来源，导致“格式控制权交叉”：

1. `evaluation-decision` 把 `converter.getFormat()` 传入模板变量 `outputSchema`，但模板未使用该变量，Spring AI 生成的 schema 被静默丢弃。
2. `planner` 同时保留手写 `Output Schema`，并在 Java 代码中额外拼接 `converter.getFormat()`，形成双重格式源。
3. `question-detail-evaluation` 的 Prompt 明确要求模型不要输出 `start/end`，但 `BeanOutputConverter` 基于 DTO 仍会把这两个字段暴露给模型。

这不是局部 Prompt 质量问题，而是职责边界不清：

- 语法格式约束由谁控制
- 业务语义约束由谁控制
- 当 Prompt 约束与 DTO/schema 冲突时谁优先

## 现状事实

### 1. `evaluation-decision` 是“schema 静默丢弃”

- `OpenAiClient.callEvaluationDecision(...)` 调用 `buildEvaluationDecisionVariables(input, converter.getFormat())`
- `buildEvaluationDecisionVariables(...)` 注入的是 `outputSchema`
- `evaluation-decision.md` 没有 `{{outputSchema}}`

结果：

- 模型只能看到手写 `[Output Schema]`
- Java Bean 结构变更后，Prompt 很容易忘记同步
- 当前能跑，只是因为手写 JSON 示例碰巧和 DTO 足够接近

### 2. `planner` 是“手写 schema + Spring AI schema 双重拼接”

- Prompt 自己写了一整段 `Output Schema`
- Java 调用又在用户提示词后直接追加 `converter.getFormat()`

结果：

- 模型在一个上下文里看到两套 JSON 定义
- token 被浪费
- 两套规则轻微漂移就会让模型处于冲突指令中

### 3. `question-detail-evaluation` 是“自定义约束与 DTO schema 冲突”

Prompt 规定：

- `highlightedAnnotations` 只允许模型输出 `{quote,label,comment}`
- `start/end` 必须由后端定位器计算

但当前 DTO 直接把 `start/end` 放在模型输出类里，意味着：

- Spring AI schema 会把 `start/end` 作为可输出字段暴露给模型
- Prompt 与 schema 对同一字段给出相反指令

## 设计目标

1. 结构化输出只有一个格式真源
2. Prompt 只表达业务语义，不再手写 JSON 结构样例
3. 自定义业务约束优先于默认 DTO 暴露
4. 结构化输出链路可以通过测试防回归
5. 不引入第二套解析框架，不把问题扩大成整套 AI SDK 重写

## 非目标

1. 不在本轮切换为 Spring AI 原生 response-format / provider-specific JSON mode
2. 不改动非结构化输出链路，如流式出题、单题追问
3. 不在本轮重构所有 Prompt 文案，只清理结构化输出职责边界

## 核心决策

### 决策一：语法格式统一由 Spring AI `BeanOutputConverter` 控制

统一规则：

- Java 负责生成 `converter.getFormat()`
- Prompt 显式预留 `{{outputSchema}}`
- 模型最终只看到一份格式 schema

禁止继续出现以下两类写法：

- 只传 `outputSchema` 但模板不使用
- 模板手写完整 JSON 结构，同时 Java 再追加 `getFormat()`

### 决策二：Prompt 只保留业务语义约束

Prompt 可以继续约束：

- `nextFocus` 必须是 4-20 字短语
- `targetDomainCode` 什么时候必须为空
- `retrievalPlans` 何时必须是空数组
- `highlightedAnnotations` 只允许引用原文片段

Prompt 不再承担：

- JSON 对象长什么样
- 字段列表完整枚举
- 数组嵌套结构示例

### 决策三：当自定义输出约束与 DTO/schema 冲突时，以自定义约束为准

这条规则要落到代码结构，而不只是写在文档里。

具体做法：

- `question-detail-evaluation` 新增专用 AI 输出 DTO，只暴露允许模型输出的字段
- 后端在 AI 输出 DTO 基础上补 `start/end`，再映射为持久化 DTO
- 不再让模型直接面对最终落库对象

### 决策四：需要稳定字段顺序的 DTO，显式声明顺序

`evaluation-decision` 的 Prompt 目前依赖固定顶层字段顺序，因此应在 DTO 上使用 `@JsonPropertyOrder`，让 Spring AI 生成的 schema 与业务要求一致。

这不是为了“美观”，而是为了消除：

- Prompt 说顺序 A
- DTO 默认导出顺序 B

这种隐性漂移。

## 目标形态

### `planner`

- 删除 Prompt 里的手写 `Output Schema`
- 改为 `{{outputSchema}}`
- Java 不再手工字符串拼接 schema，而是走统一模板注入

### `evaluation-decision`

- 删除手写 JSON 结构块
- 保留字段语义规则
- 在模板尾部显式注入 `{{outputSchema}}`
- DTO 增加稳定字段顺序声明

### `report-generation`

- 已经走 `{{outputSchema}}`
- 保持不变，只补统一性测试

### `question-detail-evaluation`

- 继续使用 `{{outputSchema}}`
- 但 schema 来源改为新的 AI 专用 DTO
- `start/end` 从模型输出面移除
- `HighlightedAnnotationLocator` 继续负责定位并回填最终字段

## 测试策略

### Prompt 覆盖测试

目标：

- 所有结构化输出 Prompt 都必须真的使用 `outputSchema`
- `planner` 和 `evaluation-decision` 不允许再遗留手写 JSON 结构块

方式：

- 用带哨兵值的 `outputSchema` 渲染 Prompt
- 断言用户 Prompt 中出现哨兵值

### wiring 测试

目标：

- 保证每条结构化输出链路只采用一种 schema 注入方式

方式：

- `planner` 不再走“render 后字符串追加”
- 结构化调用统一经过变量注入

### question-detail 契约测试

目标：

- 模型输出 DTO 不含 `start/end`
- 定位器负责把 `quote/label/comment` 变成最终 `start/end`

## 风险与控制

### 风险一：删除手写 schema 后，模型短期遵循率变化

控制：

- 保留现有业务语义规则
- 通过 `BeanOutputConverter` + 契约校验器双保险

### 风险二：question-detail DTO 拆分影响现有调用链

控制：

- 只在 AI 输入输出边界新增专用 DTO
- 对外持久化对象保持不变

### 风险三：结构化 Prompt 后续再次漏接 `outputSchema`

控制：

- 用测试兜底，不依赖日志告警人工发现

## 验收标准

1. `planner`、`evaluation-decision`、`report-generation`、`question-detail-evaluation` 都只存在一套格式源
2. `evaluation-decision` 模板明确使用 `{{outputSchema}}`
3. `planner` 不再通过字符串追加 schema
4. `question-detail-evaluation` 的模型输出 schema 不再暴露 `start/end`
5. 相关 Prompt 覆盖测试、契约测试和映射测试通过
