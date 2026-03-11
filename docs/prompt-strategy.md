# AI 模拟面试系统 Prompt 策略

## 1. 设计原则

Prompt 设计必须从一开始就分层分职责，不能把“出题、评分、报告、追问”混成一个大 Prompt。

后端统一按以下能力拆分：

- `planner`：生成本场面试考纲。
- `answer_evaluation`：评估当前题回答。
- `next_question_decision`：根据状态账本决定下一题。
- `question_generation`：生成单道题目。
- `question_hint`：生成答题提示。
- `final_report`：生成整场报告。
- `question_consult`：单题复盘页中的 AI 追问。

## 2. 输入上下文分层

### 2.1 固定系统信息

- AI 面试官角色设定。
- 岗位知识域树与评分规则。
- 两种面试模式差异。
- 输出格式约束与安全约束。

### 2.2 会话级上下文

- 岗位和知识域树。
- 工作年限、难度。
- 面试模式：`practice` 或 `professional`。
- JD 文本。
- 简历最终确认文本。
- 用户侧重知识点。
- 专业模式时间限制。
- Planner 生成的考纲。

### 2.3 题目级上下文

- 当前题目。
- 当前状态账本。
- 已问过的问题和回答摘要。
- 当前目标知识域和题型。
- RAG 检索结果。

## 3. 编排方式

### 3.1 创建面试阶段

后端执行：

1. 读取岗位知识域树。
2. 整理 JD、简历、模式等输入。
3. 调用 `planner` 生成考纲。
4. 初始化状态账本。
5. 第一题生成时机在创建面试阶段生成

### 3.2 提交并继续阶段

后端执行：

1. 保存当前题回答。
2. 调用 `answer_evaluation`。
3. 更新状态账本。
4. 调用 `next_question_decision` 判断是否继续。
5. 若继续，则调用 `question_generation` 返回下一题。

说明：

- Prompt 层不直接感知前端按钮，只感知“当前题已回答，需继续面试”。
- `submit-and-next` 是后端编排动作，不是一个单独 Prompt。

## 4. Prompt 模块详解

### 4.1 Planner

目标：

- 根据岗位、简历、JD、模式和侧重点生成结构化考纲。

输入：

- 岗位名称和知识域树。
- 工作年限和难度。
- JD 文本。
- 简历最终确认文本。
- 用户侧重知识点。
- 系统内置题量上限策略。
- 面试模式。

输出格式：

```json
{
  "title": "Java 后端开发模拟面试",
  "targetDomains": [
    {
      "domainId": 1,
      "domainName": "Java 语言基础",
      "priority": "high",
      "suggestedDepth": "medium"
    }
  ],
  "questionTypeQuotas": {
    "project": 3,
    "knowledge": 4,
    "scenario": 2,
    "behavioral": 1
  },
  "projectHighlights": [
    "订单系统缓存改造"
  ],
  "focusAreas": [
    "Redis 持久化"
  ]
}
```

约束：

- 不生成具体题目。
- 题型配额由 AI 控制，不由用户手动配置。
- 练习模式允许更强地参考用户自定义侧重点。
- 专业模式优先保证考纲覆盖完整性和节奏稳定。

### 4.2 Answer Evaluation

目标：

- 对单题回答做结构化评分、知识域映射和点评。

输入：

- 当前题目内容和预期要点。
- 用户回答文本。
- 语音模式下的转写文本和时间信息。
- 会话模式和本题上下文。

输出格式：

```json
{
  "score": 72,
  "commentary": "回答结构完整，但对缓存击穿的解决方案展开不足。",
  "strengthPoints": [
    "项目背景交代清楚"
  ],
  "weakPoints": [
    "缺少性能指标"
  ],
  "evaluatedDomains": [
    {
      "domainId": 4,
      "domainName": "缓存与中间件",
      "score": 72,
      "note": "缓存击穿方案描述偏泛"
    }
  ],
  "comprehensiveDimensionScores": {
    "communication": 73,
    "logic": 79,
    "expression": 76
  },
  "idealAnswerOutline": "背景 -> 问题 -> 分析 -> 方案 -> 结果 -> 复盘",
  "rewrittenAnswer": "在订单系统改造中，核心挑战是高并发下的缓存击穿问题..."
}
```

约束：

- 练习模式必须返回技术分与知识域映射。
- 专业模式除技术分外，还要返回综合维度分数。
- 跳过题不调用该模块。
- 颜色批注所需的高亮片段应由结构化字段返回，而不是只给纯文本评论。

### 4.3 Next Question Decision

目标：

- 根据状态账本决定下一题知识域和题型，并判断是否结束。

MVP 策略：

- MVP 优先使用规则引擎。
- AI 仅辅助复杂决策，不直接替代全部状态机。

规则引擎建议逻辑：

1. 是否达到系统内置最大题量上限。
2. 是否达到最少题量且核心知识域已覆盖。
3. 练习模式下是否应继续围绕用户自定义侧重点深入。
4. 专业模式下是否应平衡项目题、知识题、场景题和综合表现。

输出格式：

```json
{
  "shouldEnd": false,
  "endReason": null,
  "nextDomainId": 3,
  "nextDomainName": "数据库原理",
  "nextCategory": "knowledge",
  "suggestedDepth": "medium"
}
```

说明：

- `circuit_broken` 作为数据结构保留，但 MVP 不启用熔断。

### 4.4 Question Generation

目标：

- 生成一道适合当前知识域、题型和模式的题目。

输入：

- 目标知识域和题型。
- 会话模式。
- 已问问题列表。
- 会话级上下文。
- RAG 检索结果。

输出格式：

```json
{
  "stem": "请解释一下 MySQL 中 B+ 树索引的结构，以及为什么不用 B 树？",
  "targetSkill": "索引原理",
  "expectedPoints": [
    "B+ 树叶子节点链表",
    "磁盘 IO 优化",
    "范围查询效率"
  ],
  "difficulty": "medium"
}
```

约束：

- 一次只生成一道题。
- 专业模式的题目表述要更口语化，适合语音回答。
- 不能与已问过的问题重复。

### 4.5 Question Hint

目标：

- 给出答题思路，不泄露完整标准答案。

约束：

- 80 字以内。
- 用“从哪些角度回答”来提示。

### 4.6 Final Report

目标：

- 基于全场题目、回答、知识域得分和模式生成最终报告。

输出格式：

```json
{
  "overallScore": 78,
  "skillDomainScores": [
    {
      "domainId": 1,
      "domainName": "Java 语言基础",
      "score": 85,
      "note": "泛型和集合框架掌握较好"
    }
  ],
  "comprehensiveRadarScores": {
    "communication": 73,
    "logic": 79,
    "stability": 75,
    "expression": 76,
    "adaptability": 82
  },
  "summary": "整体基础较扎实，但并发编程原理和缓存边界细节仍有提升空间。",
  "strengths": [
    "项目经验真实"
  ],
  "weaknesses": [
    "并发底层原理薄弱"
  ],
  "improvementSuggestions": [
    "针对 JMM 和锁优化做专题复习"
  ],
  "recommendedTopics": [
    "JMM 内存模型",
    "CAS 原理"
  ]
}
```

约束：

- 练习模式下 `comprehensiveRadarScores` 可为空。
- 专业模式下必须返回综合能力雷达图数据。
- 报告需包含互动式逐字稿所需摘要数据。

### 4.7 Question Consult

目标：

- 在单题详情页中回答用户对本题的追问。

输入：

- 当前题目。
- 用户回答。
- 本题 AI 评分结果。
- 用户追问内容。

输出要求：

- 优先解释“为什么失分/为什么得分”。
- 给出可执行改进建议。
- 避免和最终报告冲突。

## 5. RAG 集成架构

后端 AI 服务层始终使用两阶段结构：

```text
业务上下文 -> 知识库检索 -> Prompt 拼接 -> 模型调用
```

MVP 时：

- `KnowledgeRetriever` 可返回空结果或静态材料。
- 接口层和 Prompt 结构不改。

后续接入向量数据库时：

- 只替换检索实现。
- 不改业务编排和前端接口。

## 6. 模板存储建议

推荐每类 Prompt 单独存文件：

- `planner.md`
- `answer-evaluation.md`
- `question-generation.md`
- `question-hint.md`
- `final-report.md`
- `question-consult.md`

每份模板保留：

- `promptCode`
- `promptVersion`
- `systemPrompt`
- `userPromptTemplate`
- `responseSchema`

## 7. 质量控制

### 7.1 JSON 稳定性

- 模型返回非 JSON 时先做一次自动修复。
- 修复失败则返回降级文案，避免前端空白。

### 7.2 观测指标

- `promptCode`
- `promptVersion`
- `modelProvider`
- `modelName`
- `temperature`
- `requestTokens`
- `responseTokens`
- `latencyMs`
- `success`

### 7.3 上线前验证

- Planner 是否对不同岗位和简历输出差异化考纲。
- 提交并继续链路是否稳定返回“评估 + 下一题”。
- 练习模式是否只产出技术分。
- 专业模式是否稳定产出综合能力评分。
- 单题详情页追问是否与原题评分一致。

## 8. 风险与约束

- 不要让前端直接拼完整 Prompt。
- 不要把评估原文直接塞进出题 Prompt，应只传结构化状态。
- 语音模式下，语音识别和内容评分要分两个阶段处理。
- RAG 检索结果必须控制长度，防止上下文爆炸。
- 熔断逻辑虽然结构已预留，但 MVP 不要提前启用，避免规则不稳定影响体验。
