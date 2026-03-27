# 面试系统 RAG 知识库收集整理要求

## 1. 文档目的

本文档用于约束 AI 模拟面试系统 RAG 知识库的收集、整理、标注与交付方式。

适用范围：

- 面试题知识条目整理
- 岗位知识点知识条目整理
- 评分锚点与答题要点整理

本文档不覆盖企业级文档治理，不要求审批人、责任人、流程状态等重管理字段。

## 2. 设计目标

知识库设计必须同时满足以下目标：

1. 能按岗位、知识域、题型、难度稳定过滤
2. 能给模型提供足够清晰的问答语义上下文
3. 能支持追问、纠错、评分锚点等面试场景
4. 录入成本可控，维护成本可控
5. 结构化字段与可向量化文本可同时生成

## 3. 基本原则

1. 一条知识只表达一个核心问题或一个核心知识点
2. 结构化字段负责过滤与检索控制，自由文本负责语义表达
3. 枚举字段必须统一取值，不允许人工随意发挥
4. 标签只做辅助过滤，不承担主语义
5. 同义问法、常见误区、追问链路必须显式整理，不能只放在脑补逻辑里
6. 所有编码字段必须统一大小写和命名格式

## 4. 知识单元定义

知识库的最小整理单位为“知识卡片”。

每条知识卡片只允许对应以下一种主类型：

- 一个面试问题
- 一个岗位知识点
- 一个评分锚点模板

禁止将多道无强关联题目、多个知识点、大段面经原文直接作为一条知识入库。

## 5. 字段设计

推荐知识卡片结构如下：

```json
{
  "id": "fe-js-closure-001",
  "title": "什么是闭包",
  "knowledgeKind": "interview_question",
  "positionCode": "FRONTEND",
  "domainCode": "js_core",
  "questionType": "PRINCIPLE",
  "difficulty": "L2",
  "tags": ["javascript", "closure", "scope"],
  "aliases": ["js闭包是什么", "如何理解闭包"],
  "question": "什么是闭包？",
  "answer": "闭包是函数与其词法作用域的组合...",
  "keyPoints": [
    "函数访问外部作用域变量",
    "变量在函数执行后仍可被引用",
    "本质与词法作用域有关"
  ],
  "followUps": [
    "闭包的应用场景有哪些？",
    "闭包可能带来什么问题？"
  ],
  "pitfalls": [
    "把闭包等同于匿名函数",
    "只会背定义，不会举例"
  ],
  "source": "manual_seed",
  "version": "v1"
}
```

### 5.1 必填字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `string` | 全局唯一标识 |
| `title` | `string` | 条目标题 |
| `knowledgeKind` | `string` | 知识类型枚举 |
| `positionCode` | `string` | 岗位编码 |
| `domainCode` | `string` | 知识域编码 |
| `questionType` | `string` | 题型枚举 |
| `difficulty` | `string` | 难度等级 |
| `question` | `string` | 主问题 |
| `answer` | `string` | 标准答案或知识说明 |
| `keyPoints` | `string[]` | 核心要点数组 |
| `source` | `string` | 来源类型 |
| `version` | `string` | 版本号 |

### 5.2 强烈建议字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `tags` | `string[]` | 辅助过滤标签 |
| `aliases` | `string[]` | 同义问法 |
| `followUps` | `string[]` | 追问问题 |
| `pitfalls` | `string[]` | 常见误区 |

### 5.3 当前程序对应情况

下表用于区分“当前程序已支持的取值约束”和“本文档为后续设计提出的约束”。

| 字段 | 当前程序状态 | 当前程序对应值 | 说明 |
|---|---|---|---|
| `knowledgeKind` | 部分支持 | `job_knowledge`、`interview_question` | 当前程序字段名为 `knowledgeType` |
| `positionCode` | 已支持 | `JAVA_BACKEND`、`GO_BACKEND`、`DATA_ENGINEER`、`FRONTEND`、`QA`、`DEVOPS` | 来自 `TargetRole` 枚举 |
| `domainCode` | 已支持 | 由 `position_skill_domains` 表维护 | 不是 Java 枚举，是配置型约束 |
| `questionType` | 已支持 | `INTRO`、`PROJECT_DEEP_DIVE`、`SCENARIO`、`PRINCIPLE`、`BEHAVIORAL` | 来自 `QuestionType` 枚举 |
| `difficulty` | 已支持 | `L1`、`L2`、`L3`、`L4`、`L5` | 与 `DepthLevel` 枚举对齐 |
| `source` | 未枚举化 | 当前仅为字符串 | 后续建议改为受控词表 |
| `tags` | 未支持 | 无 | 当前程序无字段承接 |
| `aliases` | 未支持 | 无 | 当前程序无字段承接 |
| `followUps` | 未支持 | 无 | 当前程序无字段承接 |
| `pitfalls` | 未支持 | 无 | 当前程序无字段承接 |

## 6. 字段约束

### 6.1 `id`

要求：

- 类型：字符串
- 全局唯一
- 使用小写字母、数字、短横线
- 建议格式：`岗位-知识域-主题-序号`

示例：

- `fe-js-closure-001`
- `be-jvm-gc-002`

### 6.2 `title`

要求：

- 类型：字符串
- 建议长度不超过 50 字
- 必须能直接表达该条目的主题
- 不要在标题里塞完整答案

### 6.3 `knowledgeKind`

要求：

- 类型：枚举
- 当前程序仅允许以下值：
  - `interview_question`
  - `job_knowledge`

说明：

- `interview_question`：具体面试题
- `job_knowledge`：岗位知识点说明

后续设计预留：

- `scoring_rubric`

说明：

- `scoring_rubric` 目前程序没有对应字段契约和处理逻辑
- 如后续要支持，需同步修改 DTO、入库逻辑、检索逻辑和 Prompt 使用方式

### 6.4 `positionCode`

要求：

- 类型：枚举或外键型字符串
- 必须来自系统岗位配置
- 不允许写中文岗位名替代编码

示例：

- `FRONTEND`
- `JAVA_BACKEND`
- `GO_BACKEND`
- `DATA_ENGINEER`
- `QA`
- `DEVOPS`

### 6.5 `domainCode`

要求：

- 类型：外键型字符串
- 必须来自岗位知识域配置
- 命名统一使用小写加下划线
- 不允许自由创造新编码直接入库

示例：

- `js_core`
- `browser`
- `jvm`
- `distributed`

### 6.6 `questionType`

要求：

- 类型：枚举
- 必须与系统题型定义一致
- 仅允许以下值：
  - `INTRO`
  - `PROJECT_DEEP_DIVE`
  - `SCENARIO`
  - `PRINCIPLE`
  - `BEHAVIORAL`

说明：

- `INTRO`：自我介绍类
- `PROJECT_DEEP_DIVE`：项目深挖类
- `SCENARIO`：场景分析类
- `PRINCIPLE`：原理知识类
- `BEHAVIORAL`：行为面试类

### 6.7 `difficulty`

要求：

- 类型：枚举
- 仅允许以下值：
  - `L1`
  - `L2`
  - `L3`
  - `L4`
  - `L5`

等级定义：

- `L1`：定义和基础概念
- `L2`：原理和常见用途
- `L3`：结合场景分析或实现
- `L4`：取舍、排障、优化、深度原理
- `L5`：复杂系统设计或架构判断

面试场景解释建议：

- `L1`：候选人能说出定义和基础概念
- `L2`：候选人能解释原理和常见用途
- `L3`：候选人能结合具体场景分析或实现
- `L4`：候选人能进行取舍、排障、优化，并解释更深层原理
- `L5`：候选人能处理复杂系统设计或架构判断

补充口径：

- `difficulty` 只表示题卡/题目本身的深度等级
- 候选人资历统一使用 `experienceLevel`
- 单轮目标深度提示统一使用 `difficultyHint`

禁止使用：

- `easy`
- `medium`
- `hard`
- `初级`
- `高级`

原因：

这些值语义模糊，跨岗位不可比较。

### 6.8 `tags`

要求：

- 类型：字符串数组
- 每条建议 3 到 8 个
- 标签值统一小写
- 英文标签建议使用短词或 `kebab-case`
- 禁止与 `domainCode`、`questionType` 重复表达同一层语义

推荐标签类型：

- 技术点标签：如 `closure`
- 能力标签：如 `troubleshooting`
- 频率标签：如 `high-frequency`

不推荐标签：

- 句子型标签
- 与标题完全重复的标签
- 无法过滤的空泛标签，如 `important`

### 6.9 `aliases`

要求：

- 类型：字符串数组
- 每条为一种自然问法
- 用于提升召回，不用于表达知识点

示例：

- `js闭包是什么`
- `如何理解闭包`
- `closure 原理`

### 6.10 `question`

要求：

- 类型：字符串
- 一条记录只保留一个主问题
- 不要把追问混入主问题
- 不要出现“问题 1 / 问题 2 / 问题 3”这种合集写法

### 6.11 `answer`

要求：

- 类型：字符串
- 必须是可直接提供给模型参考的标准答案或知识说明
- 优先写结构清晰、信息完整、可直接复述的内容
- 不要只写一句过短定义

### 6.12 `keyPoints`

要求：

- 类型：字符串数组
- 每一项只表达一个要点
- 不要把整段答案复制到每个数组项中
- 建议每条 3 到 6 项

### 6.13 `followUps`

要求：

- 类型：字符串数组
- 每项必须是可独立提问的追问
- 不得与 `question` 完全重复
- 用于追问链和多轮面试控制

### 6.14 `pitfalls`

要求：

- 类型：字符串数组
- 每项描述一个常见误区或错误表述
- 用于纠错、评分和解释补充

### 6.15 `source`

要求：

- 当前程序中该字段尚未枚举化，当前只是普通字符串
- 为保证后续可过滤和可统计，建议设计为受控词表
- 后续推荐值：
  - `manual_seed`
  - `official_doc`
  - `curated_notes`
  - `imported_bank`
  - `mock_interview`
  - `team_internal`

说明：

- 当前代码和样本数据中实际出现过的值包括 `official_doc`、`team_internal`
- `manual_seed`、`curated_notes`、`imported_bank`、`mock_interview` 属于本文档建议的后续统一值，当前程序没有强校验

### 6.16 `version`

要求：

- 类型：字符串
- 使用 `v1`、`v2`、`v3` 等格式
- 用于后续升级、回溯与替换

## 7. 跨字段校验规则

1. `positionCode` 必须存在于系统岗位配置中
2. `domainCode` 必须属于对应 `positionCode` 的知识域集合
3. `questionType=INTRO` 时，允许 `domainCode` 为空或固定为 `intro`
4. 后续若支持 `knowledgeKind=scoring_rubric`，则 `answer` 可以是评分说明文本，但仍必须提供 `keyPoints`
5. `tags`、`aliases`、`keyPoints`、`followUps`、`pitfalls` 必须为数组，不允许逗号拼接字符串
6. `followUps` 中不得出现与主问题完全重复的条目
7. `difficulty` 必须使用 `L1-L5`，不得出现其他表示法
8. `source` 当前程序未强校验；若后续落地受控词表，需增加服务端枚举或校验器

## 8. 收集整理要求

### 8.1 收集对象

优先收集以下内容：

1. 高频面试题
2. 岗位核心知识点
3. 项目深挖题模板
4. 场景题排查步骤
5. 标准答案骨架
6. 常见误区与低质量回答样例

### 8.2 收集原则

1. 一题一卡
2. 一卡一核心主题
3. 先规范编码再录入内容
4. 先写标准答案，再补追问和误区
5. 标签少而准，不为凑数打标签

### 8.3 禁止事项

1. 禁止直接整篇导入面经原文
2. 禁止一条记录中同时包含多个无强关联问题
3. 禁止自由发挥编码值
4. 禁止使用模糊难度标签
5. 禁止把数组字段写成单字符串

## 9. 推荐整理格式

推荐采用两层格式：

1. 人工整理源格式：Markdown + YAML Front Matter
2. 程序入库格式：JSON 或 JSONL

Markdown 示例：

```md
---
id: fe-js-closure-001
title: 什么是闭包
knowledgeKind: interview_question
positionCode: FRONTEND
domainCode: js_core
questionType: PRINCIPLE
difficulty: L2
tags: [javascript, closure, scope]
aliases: [js闭包是什么, 如何理解闭包]
source: manual_seed
version: v1
---

# Question
什么是闭包？

# Answer
闭包是函数与其词法作用域的组合...

# Key Points
- 函数访问外部作用域变量
- 变量在函数执行后仍可被引用
- 本质与词法作用域有关

# Follow Ups
- 闭包的应用场景有哪些？
- 闭包可能带来什么问题？

# Pitfalls
- 把闭包等同于匿名函数
- 只会背定义，不会举例
```

## 10. 入库文本生成要求

为了兼顾结构化过滤与向量检索，系统应根据结构化字段生成统一语义文本。

推荐拼装格式：

```text
标题：什么是闭包
题型：PRINCIPLE
难度：L2
标签：javascript, closure, scope
别名：js闭包是什么；如何理解闭包

问题：
什么是闭包？

标准答案：
闭包是函数与其词法作用域的组合...

关键点：
1. 函数访问外部作用域变量
2. 变量在函数执行后仍可被引用
3. 本质与词法作用域有关

可追问：
1. 闭包的应用场景有哪些？
2. 闭包可能带来什么问题？

常见误区：
1. 把闭包等同于匿名函数
2. 只会背定义，不会举例
```

## 11. 交付验收标准

每批知识条目整理完成后，必须满足以下要求：

1. 所有必填字段完整
2. 所有枚举字段值合法
3. 所有数组字段结构正确
4. `positionCode` 与 `domainCode` 能通过系统配置校验
5. 每条记录只对应一个核心问题或一个核心知识点
6. `answer` 与 `keyPoints` 内容一致，不互相冲突
7. `followUps` 与 `pitfalls` 非空时具备明确语义，不是占位文字

## 12. 最小可执行要求

如果时间有限，至少保证以下字段完整且规范：

1. `id`
2. `title`
3. `knowledgeKind`
4. `positionCode`
5. `domainCode`
6. `questionType`
7. `difficulty`
8. `question`
9. `answer`
10. `keyPoints`
11. `source`
12. `version`

在此基础上，优先补充：

1. `aliases`
2. `followUps`
3. `pitfalls`
4. `tags`

## 13. 现状与后续设计边界

以下约束已经和当前程序直接对应：

1. `positionCode` 必须使用现有岗位枚举值
2. `questionType` 必须使用现有题型枚举值
3. `difficulty` 必须使用现有深度等级枚举值
4. `knowledgeKind` 当前只支持 `job_knowledge` 和 `interview_question`
5. `domainCode` 必须来自岗位知识域配置，而不是自由文本

以下约束当前程序还没有直接实现，属于后续设计要求：

1. `tags` 的数组化与标准化
2. `aliases` 的字段承接与召回利用
3. `followUps` 的字段承接与追问链利用
4. `pitfalls` 的字段承接与纠错利用
5. `source` 的枚举化校验
6. `scoring_rubric` 类型的正式支持
