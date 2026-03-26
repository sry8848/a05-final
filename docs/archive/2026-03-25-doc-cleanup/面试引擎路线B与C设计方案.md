# 弱控制版 AI 面试引擎设计文档 v1

## 1. 文档目的

本文用于替换此前偏“强编排”的路线 B/C 方案，给出一版更符合 LLM 能力边界、也更贴合当前项目落地条件的设计稿。

本文核心立场只有一句话：

**程序负责约束、记忆、评估沉淀；AI 负责理解回答、决定追问、生成下一问。**

本文主要回答六个问题：

1. 为什么不继续走强编排路线
2. 弱控制方案的核心原则是什么
3. 在当前代码结构下，最小可落地的状态、输入、输出应该长什么样
4. 如何在保证真实性的同时，保留难度稳定、覆盖约束、跨场去重和弱点复现能力
5. RAG 应该在这套方案里如何接入
6. 为什么当前阶段仍保留“决策 + 出题”两步链路

---

## 2. 背景与问题定义

当前系统已经有完整的逐题主链路：

- `planner` 生成考纲
- `AnswerSubmitService` 负责编排逐题提交流程
- `StateLedgerPatchService` 负责账本落库
- `QuestionStreamService` 负责下一题生成与流式输出

当前问题不在“链路缺失”，而在“链路控制方式不对”。

### 2.1 当前体验问题

1. 项目主线感偏弱
2. 不够顺着候选人回答走
3. 转场有时像切题，不像追问
4. 失败语义过粗，容易误收敛
5. 多次练习时，题干可能不同，但主线、追问链和考点组合仍偏重复

### 2.2 强编排方案的问题

此前方案的方向并非完全错误，但存在明显的程序控制过强问题：

1. 试图把“面试官行为”拆成大量显式枚举和状态字段
2. 把很多后验标签提前变成运行时强控制变量
3. 让程序代替模型决定“怎么表演”，而不是让模型基于上下文自然判断
4. 控制性 Prompt 过长时，会压缩真正高价值的候选人上下文和项目上下文，导致模型虽然“被约束更多”，但反而更容易丢失关键信息

典型风险包括：

- 设计复杂度高
- 字段语义容易漂移
- 多模块联动成本高
- 程序味明显，真实感反而下降

因此，这一版方案的重点不是“再造一个更聪明的状态机”，而是把系统改成：

**弱编排、强上下文、最小状态、可观测优先。**

---

## 3. 设计目标

这版方案主要解决四件事：

### 3.1 单场更像真人

AI 能顺着候选人回答继续问，而不是只按预设路径切题。

### 3.2 可控但不僵硬

系统不会完全放飞，但也不再依赖大量程序状态驱动“面试官动作”。

### 3.3 易维护

主状态更少，链路更短，Prompt 和 DTO 职责更清晰。

### 3.4 多场可复练

支持轻量跨场去重、弱点有限复现和主线轮换，但不做过重的预编剧。

---

## 4. 核心设计原则

## 4.1 原则一：不显式编排面试官每一步动作

程序不要提前规定：

- 现在一定是 `MAINLINE` 还是 `COVERAGE`
- 这题一定是 `FOLLOWUP` 还是 `PIVOT`
- 连续追问几轮后必须转场
- 这一轮一定是 `boundary` 还是 `tradeoff`

这些更适合作为：

- AI 的判断结果
- 日志标签
- 统计标签

而不是运行时必须精确维护的控制器。

## 4.2 原则二：只保留真正有产品价值的状态

一个字段是否应该进入主状态，判断标准只有一个：

删掉它之后，是否会明显影响：

- 难度稳定性
- 覆盖完整性
- 跨场去重
- 报告生成
- 面试连续性

如果不会，就不应放入运行时主状态。

## 4.3 原则三：上下文比枚举更重要

真实感主要来自：

- 当前岗位和年限
- 当前主线项目
- 上一问与上一答摘要
- 已覆盖与未覆盖重点
- 当前弱信号
- 当前这一轮的提问目标

而不是来自越来越多的流程控制枚举。

## 4.4 原则四：程序做硬约束，AI 做软决策

### 程序负责

- 轮次预算
- 难度包络
- 覆盖域下限
- 近期重复惩罚
- 跨场记忆
- 评估结果持久化

### AI 负责

- 理解候选人刚才的回答
- 判断这一轮是否值得继续追
- 决定追什么、怎么问更自然
- 是否降阶补救
- 如何转场不突兀

---

## 5. 总体架构

建议收敛为 5 个核心模块。

## 5.1 Interview Constraints Builder

职责：

- 构建本场硬约束
- 不负责产出面试剧本

实现建议：

- 第一阶段优先用规则实现，不新增额外 AI 调用
- 后续若需要更强动态性，可在保持输出结构稳定的前提下引入 AI 作为约束建议器

原因：

- 这一层主要处理产品规则和系统边界，更适合程序稳定控制
- 过早把它交给 AI，会增加调用次数、引入不必要波动

输入：

- 岗位
- 年限
- round type
- 模式
- 时长或题量预算
- 历史去重摘要
- 弱点复现偏置

输出示例：

```json
{
  "round_type": "technical_first",
  "difficultyHint": "L3",
  "required_domains": ["mysql", "java_basic"],
  "remaining_turn_budget": 8,
  "avoid_recent_question_families": [
    "mq.delay-message.boundary"
  ],
  "weakness_replay_candidates": [
    "mysql.transaction.boundary"
  ]
}
```

说明：

- `difficultyHint` 不是写死本场所有题目的固定难度，而是当前轮或当前阶段优先探测的目标深度提示；本场通常应问到的区间仍由 `experienceLevel` 决定
- 对于实习生/应届生，允许在定标后上探到高于初始预估的深度；对承压明显的候选人，也允许在包络内适度回落
- `required_domains` 不是自由挑选，而是来自现有知识域体系、评分体系和岗位要求的交集约束
- `remaining_turn_budget` 建议理解为软预算而不是硬预算；它主要用于防止无限追问，不应阻止高价值补问
- `avoid_recent_question_families` 第一阶段不要求题目家族体系极度精确，可先由 `project + focus_point + angle` 近似表达

注意：

- 它只生成约束和偏置
- 不生成“这场会怎么走”的固定路线

## 5.2 Interview Context Builder

这是整套系统最关键的模块之一。

职责：

- 为每一轮 AI 决策组装最有信息量的上下文

实现建议：

- 第一阶段优先用程序规则组装上下文，不新增独立 AI 调用
- 如后续发现摘要质量不足，再考虑引入轻量 AI 摘要器，但不作为主链路阻塞步骤

建议输入源：

- 简历摘要
- 项目摘要
- 当前活跃项目
- 当前活跃线程相关的最近若干轮 Q/A 摘要
- 已覆盖点
- 未覆盖重点
- 当前弱信号
- 剩余轮数
- 当前硬约束

它的输出不是状态机，而是“足够让 AI 像面试官一样继续问”的语境。

补充说明：

- 这里不建议把窗口固定死为“2~4 轮”
- 正确做法是：至少覆盖当前追问链所需的上下文，再按 token 预算截断
- 对短支线，2~4 轮通常够用；对较长项目主线，可能需要 4~6 轮摘要
- 上下文组织原则不是“越短越好”，而是“单位 token 价值最大”

## 5.3 Turn Decision Engine（AI）

职责：

读当前语境后，只做一件事：

**判断这轮下一步的面试目标是什么。**

它不输出一大堆中间控制状态，只输出：

- 回答评价
- 下一步决策
- 目标验证点
- 难度调整建议
- 最小状态补丁
- RAG 检索意图

## 5.4 Question Generator（AI）

职责：

- 根据“下一步目标”生成自然问题

它不是根据一堆 `phase/moveType` 拼题，而是根据：

- 当前项目语境
- 上一轮回答摘要
- 下一问要验证什么
- 当前难度带
- 当前风格约束
- 已检索出的 RAG 上下文或检索约束

直接生成“像真人会问的话”。

## 5.5 Memory & Diversity Manager

职责：

- 维护跨场多样性相关记忆

包括：

- 最近主线项目去重
- `question family` 去重
- 弱点有限复现
- 覆盖缺口补齐

它只提供偏置和约束，不决定整场面试剧本。

---

## 6. 最小状态模型

## 6.1 运行时核心状态

建议把运行时主状态收缩为：

```json
{
  "active_project_id": "p_ai_interview",
  "current_focus": "状态账本设计",
  "turn_index": 4,
  "remaining_turn_budget": 7,
  "difficultyHint": "L3",
  "covered_domains": ["java_basic", "mysql"],
  "covered_points": [
    "project:ai_interview:state_ledger",
    "mysql:transaction_boundary"
  ],
  "weak_signals": [
    "失败语义不清晰",
    "异常处理偏空"
  ],
  "recent_question_families": [
    "project.state-ledger.boundary",
    "mysql.transaction.boundary"
  ]
}
```

字段解释：

- `active_project_id`：当前主线项目
- `current_focus`：当前主要讨论点
- `turn_index`：当前轮次
- `remaining_turn_budget`：剩余轮次预算
- `difficultyHint`：当前轮正在优先探测的目标深度提示
- `covered_domains`：已覆盖的域
- `covered_points`：已覆盖的更细粒度点
- `weak_signals`：最近暴露出的薄弱点
- `recent_question_families`：近期已用过的题目家族，用于去重

## 6.2 不建议进入主状态的字段

以下字段可以做日志标签或离线分析标签，但不建议作为主状态强驱动：

- `phase`
- `moveType`
- `followup_streak`
- `pressure_level`
- `transitionIntent`
- `branch_anchor`
- `calibration_turns`
- 过细的 `current_domain_focus_points` 进度表

原因：

- 它们大多是派生语义，不是基础事实
- 很容易造成语义漂移和控制过度
- 对真实感帮助小于对复杂度的提升

---

## 7. 每轮 AI 决策的最小输出

## 7.1 推荐结构

建议替代当前偏强编排的 `EvaluationDecisionOutput`，改为更小、更直接的结构：

```json
{
  "answer_assessment": "候选人说明了账本记录状态推进的基本思路，但没有讲清失败语义如何避免误收敛。",
  "decision": "followup",
  "target_focus": "失败语义与状态收敛",
  "target_angle": "boundary",
  "difficulty_adjustment": "same",
  "next_question_goal": "继续在当前项目内验证失败处理和状态建模能力",
  "question_type": "PROJECT_DEEP_DIVE",
  "focus_point": "失败语义与状态收敛",
  "retrieval_intent": {
    "domain_hint": "project_design",
    "focus_query": "失败语义 状态收敛 状态账本",
    "question_type_hint": "PROJECT_DEEP_DIVE",
    "avoid_recent_families": [
      "project.state-ledger.definition"
    ]
  },
  "state_patch": {
    "active_project_id": "p_ai_interview",
    "current_focus": "状态账本设计",
    "weak_signals_add": [
      "失败语义建模不足"
    ]
  },
  "tags": {
    "question_family_hint": "project.state-ledger.boundary",
    "interviewer_intent": "probe"
  }
}
```

## 7.2 字段说明

### 必要字段

- `answer_assessment`
  - 本轮回答的简短评价
  - 给日志、报告和下一问参考

- `decision`
  - 下一步动作意图

- `target_focus`
  - 下一问主要验证什么

- `target_angle`
  - 本轮主要验证角度

- `difficulty_adjustment`
  - `up / same / down`

- `next_question_goal`
  - 下一问的目标摘要

- `question_type`
  - 兼容当前系统所需的题型标签
  - 保留，但降级为弱控制输出

- `focus_point`
  - 为沉淀、统计、回放服务

- `retrieval_intent`
  - 为 RAG 检索服务
  - 用于告诉系统下一问应检索什么，以及应避开什么

- `state_patch`
  - 只更新少量必要状态

### 可选标签

- `question_family_hint`
- `interviewer_intent`

这些更适合做观测、统计和去重辅助，不建议作为强控制主字段。

## 7.3 `decision` 建议枚举

保留 5 个就够：

- `followup`
- `probe`
- `rescue`
- `broaden`
- `wrapup`

说明：

- `followup`：顺着当前回答继续追
- `probe`：仍在当前主线，但切到相邻重点继续验证
- `rescue`：降阶补问
- `broaden`：切去未覆盖领域补齐画像
- `wrapup`：开始收束

注意：

- `decision` 不应直接等同于题型
- 例如 `followup` 既可能生成 `PROJECT_DEEP_DIVE`，也可能生成 `SCENARIO` 或 `PRINCIPLE`

## 7.4 `target_angle` 建议枚举

保留较小集合：

- `definition`
- `implementation`
- `tradeoff`
- `boundary`
- `troubleshooting`
- `role`

它主要服务：

- 提问目标表达
- 统计聚合
- 多次复练时的弱点复现换角度

不建议围绕它搭建重型在线状态机。

## 7.5 `question_type` 的定义

这里保留 `question_type`，不是为了回到强编排模型，而是为了兼容当前系统的下游能力。

建议定义如下：

- `PROJECT_DEEP_DIVE`
  - 问题明确落在候选人的真实项目、职责、项目方案、项目边界或项目复盘上
- `SCENARIO`
  - 问题以假设场景、故障场景、极端情况、排障或设计取舍为主要载体
- `PRINCIPLE`
  - 问题以原理、机制、概念辨析、实现逻辑为主要载体
- `BEHAVIORAL`
  - 问题以角色、协作、推进、复盘为主要载体
- `INTRO`
  - 开场介绍与主项目定标

要点：

- `question_type` 是下游交付标签，不是运行时强控制器
- 同一个 `decision=followup`，仍可能落成不同 `question_type`
- `PROJECT_DEEP_DIVE` 的关键不是“是否深”，而是“是否仍然 anchored 在候选人的真实项目语境里”

---

## 8. Question Generator 的输入方式

## 8.1 推荐输入结构

问题生成器应该以“提问目标”为中心，而不是以大量语义标签为中心。

建议输入：

```json
{
  "role_context": {
    "round_type": "technical_first",
    "experienceLevel": "FRESH_GRAD",
    "difficultyHint": "L3",
    "style": "natural_followup"
  },
  "project_context": {
    "active_project_id": "p_ai_interview",
    "project_name": "AI模拟面试系统",
    "current_focus": "状态账本设计"
  },
  "recent_context": {
    "last_question": "你们状态账本里，失败回答是怎么处理的？",
    "last_answer_summary": "候选人提到会记录状态和分数，但没有讲清失败后是暂时跳过还是永久结束。",
    "recent_turns_summary": "前两轮围绕项目主线进行了设计追问。"
  },
  "next_question_goal": {
    "decision": "followup",
    "target_focus": "失败语义与状态收敛",
    "target_angle": "boundary",
    "difficulty_adjustment": "same"
  },
  "retrieval_context": {
    "query": "失败语义 状态收敛 状态账本",
    "rag_context": "..."
  },
  "constraints": {
    "avoid_repetition_families": [
      "project.state-ledger.definition"
    ],
    "must_sound_natural": true,
    "max_sentences": 2
  }
}
```

## 8.2 为什么仍然保留“决策 + 出题”两步链路

当前阶段仍建议保留两步链路，不是因为它更优雅，而是因为它在现有工程下更现实：

1. 第一步负责理解回答并产出“下一问目标”
2. 第二步基于目标执行 RAG 检索并生成题目

保留两步的主要原因：

- 当前项目已经存在独立的 `evaluation_decision` 和 `question_generation_stream`
- RAG 检索当前依赖 `domainCode / questionType / focusPoint / difficultyHint` 一类中间信号
- 两步拆开后，日志、回放、调试和失败降级更容易做
- 对于后续 prompt 调优，更容易定位问题是“判断错了”还是“问法不自然”

这不是永远不变的架构结论。

如果未来满足以下条件，可以再讨论是否合并为单步：

- 模型上下文窗口与成本足够稳定
- RAG 检索与题目生成可以在同一次调用内可靠完成
- 观察证明两步链路的延迟与信息损耗高于收益

换句话说：

- 当前保留两步，是为了兼容现有链路、RAG 接入和可观测性
- 不是原则上反对单步

## 8.3 Prompt 重点

Question Generator Prompt 应重点约束：

1. 你是技术面试官，不是题库系统
2. 基于刚才候选人的回答继续问
3. 当前仍处在当前项目语境内
4. 当前主要验证某个明确目标点
5. 难度不要超出给定难度带
6. 问题要自然、简洁、像真人
7. 不要模板化承接

这比告诉模型“你现在在 MAINLINE/FOLLOWUP/phase X”更有效。

补充说明：

- Prompt 不宜过短，否则模型拿不到足够语境，很难做自然追问
- Prompt 也不宜过长，否则高价值上下文会被稀释，尤其对项目主线和上一轮回答不利
- 实施时应采用“价值优先”的上下文策略：
  - 优先保留当前活跃项目与当前 focus
  - 优先保留上一问与上一答摘要
  - 优先保留未覆盖重点与重复回避约束
  - 低价值历史信息在 token 紧张时优先裁剪

## 8.4 兼容当前系统的保留字段

考虑到当前后端、前端和报告系统依赖题型，建议 `QuestionGenerationInput` 仍保留：

- `questionType`
- `difficultyHint`
- `targetSkill`
- `expectedPoints`

但它们的来源从“强编排状态机决定”改为：

- 决策结果给出目标
- 程序做硬约束校正
- Question Generator 基于语境自然表达

---

## 9. 跨场多样性设计

## 9.1 不做 Blueprint 驱动剧本

不建议在开场前把整场剧本固定为：

- 主线项目一定是什么
- 覆盖域套餐一定是什么
- 追问链一定怎么走

这会让系统变成考务编排，而不是面试对话。

## 9.2 改成 Session Bias

本场只生成轻量偏置：

```json
{
  "preferred_projects": [
    "p_ai_interview",
    "p_order_system"
  ],
  "preferred_domains": [
    "mysql",
    "concurrency",
    "behavioral"
  ],
  "avoid_question_families": [
    "mq.delay-message.boundary",
    "redis.cache-consistency.tradeoff"
  ],
  "weakness_replay_candidates": [
    "mysql.transaction.boundary"
  ],
  "difficultyHint": "L3"
}
```

它回答的是：

- 优先从哪些项目里选
- 优先补哪些域
- 哪些题目家族近期别再问
- 哪些弱点可以有限复现

它不是剧本，只是偏置。

## 9.3 保留 Question Family，但做轻

建议保留 `question family`，因为它对跨场去重确实有价值。

最小字段建议：

```json
{
  "question_family_id": "mysql.transaction.boundary",
  "focus_point": "事务边界",
  "angle": "boundary",
  "project_mapping": "p_order_system"
}
```

第一阶段不建议一口气引入过多层次，如：

- `templateId`
- `followupPathId`
- 多级蓝图路径 ID

这些可以后续按数据需要再加。

## 9.4 轻量去重策略

建议只做三层就够：

1. 最近 2~3 场相同主线项目降权
2. 最近 2~3 场相同 `question_family_id` 降权
3. 同一弱点连续两场高频复现时临时降权

这已经足够大幅降低重复感。

## 9.5 弱点复现策略

目标不是重复同一道题，而是重复验证同一能力缺口。

建议规则：

- 弱点提高出现概率
- 但有上限，不允许无限复读
- 每次复现优先变化：
  - 项目映射
  - 提问角度
  - 抽象层级

例如“事务边界弱”：

- 第一次：项目追问
- 第二次：原理题
- 第三次：场景题

这是有效复现，而不是机械重复。

---

## 10. 与当前代码的映射关系

## 10.1 `PlannerService`

职责收缩：

- 输出项目摘要
- 输出域能力图
- 输出推荐主线项目候选
- 输出项目 `entryPoints`
- 输出基础难度建议

不负责节奏编排。

## 10.2 `AnswerSubmitService`

改为核心上下文编排器，但职责只有两件事：

### A. 组装上下文

从：

- 当前 `SessionState`
- 最近问答
- planner 输出
- 历史去重摘要

组装 `TurnDecisionInput`。

### B. 调 AI 决策并做硬约束校验

拿到：

- 决策
- `target_focus`
- `state_patch`

然后：

- 校验难度带
- 校验重复约束
- 必要时调整 `questionType`
- 生成或组装 `retrieval_intent`
- 更新状态
- 调 Question Generator

它不是状态机执行器，而是上下文调度器。

## 10.3 `StateLedgerPatchService`

建议保留，但职责收缩。

它只负责：

- 合并 `state_patch`
- 更新 `covered_domains / covered_points`
- 更新 `weak_signals`
- 更新 `recent_question_families`

不再负责复杂流程语义推导。

## 10.4 `Reducer`

Reducer 只保留事实合并逻辑：

- 覆盖域加入集合
- 弱信号更新
- 当前 focus 更新
- 最近 question family 滚动窗口更新

不再根据 `FAIL` 自动推导“该域结束”。

## 10.5 `QuestionStreamService`

输入改成“提问目标上下文”。

每题落库时，建议至少写入：

```json
{
  "question_family_id": "project.state-ledger.boundary",
  "focus_point": "失败语义与状态收敛",
  "angle": "boundary",
  "active_project_id": "p_ai_interview",
  "decision_source": "followup"
}
```

其中：

- `question_family_id`
- `focus_point`
- `angle`
- `active_project_id`

是后续跨场记忆、多样性统计和报告沉淀的最小基础。

---

## 11. 测试策略

相比强编排版“状态迁移是否正确”，弱控制版更应该关注以下四类测试。

## 11.1 上下文组装正确

验证能否正确组装：

- 当前项目
- 最近回答摘要
- 当前 focus
- 未覆盖重点
- 去重约束

## 11.2 AI 决策输出结构稳定

验证：

- JSON 可解析
- `decision` 合法
- `target_focus` 非空
- `difficulty_adjustment` 合法
- `state_patch` 可合并
- `retrieval_intent` 可用于现有 RAG 检索链路

## 11.3 约束生效

例如：

- 剩余轮数很少时更容易 `broaden` 或 `wrapup`
- 最近重复的 `question family` 会降权
- 弱点不会连续三场以同一问法复现

## 11.4 生成自然性回归

建议保留人工评测维度：

- 是否明显像追问
- 是否有机械承接语
- 是否无故切题
- 是否难度失真

这比测试 `FOLLOWUP -> MAINLINE` 一类状态迁移更有产品意义。

---

## 12. 与旧强编排方案的取舍

## 12.1 建议保留

- `entryPoints`
- `activeProjectId`
- `questionFamilyId`
- 弱点复现机制
- 最近历史去重
- `generationContextJson` 承载元数据
- 项目主线优先思路

## 12.2 建议弱化

- `phase`
- `moveType`
- `angle` 的强控制作用
- `blueprint` 的剧本属性
- reducer 对失败语义的过度推导

## 12.3 建议删除或降级为日志标签

- `followup_streak`
- `pressure_level`
- `calibration_turns`
- `transitionIntent`
- `branch_anchor`
- `followupCandidates`

---

## 13. 最小可落地实施方案

如果以 MVP 改造为目标，建议先只做 6 件事。

## 13.1 收缩状态

运行时核心状态只保留：

- `active_project_id`
- `current_focus`
- `covered_domains`
- `covered_points`
- `weak_signals`
- `remaining_turn_budget`
- `recent_question_families`
- `difficultyHint`

## 13.2 重写 Decision Prompt

让 AI 输出：

- `answer_assessment`
- `decision`
- `target_focus`
- `target_angle`
- `difficulty_adjustment`
- `next_question_goal`
- `retrieval_intent`
- `state_patch`

不再输出一大堆强编排字段。

## 13.3 重写 Question Generation Prompt

基于：

- `last_answer_summary`
- `next_question_goal`
- `active_project`
- `retrieval_context`
- `style_constraints`

生成自然追问。

## 13.4 落 Question Family

每题落：

- `question_family_id`
- `focus_point`
- `angle`
- `active_project_id`

## 13.5 加跨场轻去重

最近 N 场做：

- 相同项目降权
- 相同 family 降权
- 连续弱点复现降权

## 13.6 复杂字段降级为日志

若仍希望观察 AI 行为，可记录：

- `inferred_phase`
- `inferred_intent`
- `inferred_angle`

但不要让它们控制主流程。

---

## 14. 最终建议

这一版方案的核心不是“把程序控制做得更漂亮”，而是重新划清程序和 AI 的边界：

- 程序不再决定“面试官下一步该怎么表演”
- 程序只告诉 AI：
  - 边界在哪
  - 刚才聊到哪
  - 哪些不能重复
  - 这一轮大概要验证什么

然后由 AI 自己完成：

- 理解
- 追问
- 转场
- 出题

一句话总结：

**弱控制版 AI 面试引擎，不是让程序扮演面试官，而是让程序提供约束和记忆，让 AI 真正去做面试官。**
