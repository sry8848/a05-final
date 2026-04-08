# 面试Agent协作流程技术文档

## 目录

1. [概述](#概述)
2. [RAG检索机制](#rag检索机制)
3. [状态账本](#状态账本)
4. [策略池筛选与联动](#策略池筛选与联动)
5. [完整协作流程](#完整协作流程)

---

## 概述

本面试系统采用多Agent协作架构，通过以下核心组件实现智能化面试流程：

- **RAG（检索增强生成）**：提供知识库支持，为面试题目和评估提供参考材料
- **状态账本**：维护面试会话的全局状态，包括配额、知识域覆盖等
- **策略池**：定义并筛选合法的面试策略，确保AI决策在可控范围内
- **决策引擎**：协调各组件，执行AI决策并更新状态

### 技术栈

| 组件 | 技术选型 |
|------|----------|
| 向量数据库 | Qdrant (gRPC) |
| 向量化模型 | OpenAI Embedding (Spring AI) |
| 重排服务 | 阿里百炼 Text Rerank |
| 框架 | Spring Boot 3 + Spring AI 1.0.0 |

---

## RAG检索机制

### 1. 知识库结构

知识库以`KnowledgeDocument`为核心数据模型，包含以下关键字段：

| 字段 | 说明 |
|------|------|
| `id` | 题目唯一标识 |
| `questionText` | 题目文本 |
| `intentConcept` | 考点概念 |
| `referenceContext` | 参考语境 |
| `scoringKeyPoints` | 评分关键点 |
| `scoringPitfalls` | 评分误区 |
| `domainCode` | 知识域编码 |
| `questionType` | 题目类型 |
| `difficulty` | 难度等级 |
| `keywords` | 关键词列表 |
| `followUpIds` | 跟进题目ID列表 |

### 2. 检索流程

RAG检索采用**多阶段混合检索**策略：

```
检索请求 → Lexical预过滤 → Dense向量召回 → 业务重排 → 硬约束过滤 → 结果构建
```

#### 阶段1：Lexical预过滤

- **目的**：快速收缩候选集，提高检索效率
- **实现**：使用Qdrant的`scroll` API结合全文索引
- **过滤条件**：
  - 激活状态 (`active=true`)
  - 题目类型匹配
  - 知识域匹配
  - 关键词匹配（`question_text`、`intent_concept`、`keywords`）

#### 阶段2：Dense向量召回

- **目的**：基于语义相似度召回相关文档
- **实现**：使用Spring AI VectorStore的`similaritySearch`
- **参数**：
  - 检索文本：焦点 + 关键词 + 必须包含线索
  - TopK：可配置（默认5）
  - 相似度阈值：可配置（默认0.65）

#### 阶段3：业务重排

- **目的**：优化排序结果，提升相关性
- **实现**：调用阿里百炼Text Rerank API
- **回退策略**：API失败时回退到基于dense排名的本地排序

#### 阶段4：硬约束过滤

- **目的**：确保结果符合业务规则
- **约束**：
  - 行为题(`BEHAVIORAL`)只能匹配行为题
  - 项目题(`PROJECT`)只能匹配项目题
  - 知识域必须匹配（如果指定）

### 3. 核心配置

```yaml
rag:
  enabled: true
  top-k: 5
  min-score: 0.65
  host: localhost
  port: 6334
  collection-name: interview_knowledge
  rerank:
    model: gte-rerank-v2
    timeout-ms: 5000
    top-n: 10
```

### 4. 容错机制

- **服务降级**：RAG服务不可用时返回空结果
- **API失败回退**：重排API失败时使用本地排序
- **异常捕获**：统一捕获并记录检索过程中的异常

---

## 状态账本

### 1. 状态账本设计

状态账本(`state_ledger_json`)是面试会话的核心状态管理机制，用于维护：

- 配额使用情况
- 知识域覆盖情况
- 已问知识点
- 活动项目/行为项
- 兜底机制状态

### 2. 配额系统

配额系统防止某类题型或追问过度使用，确保面试的均衡性。

#### 配额类型

| 配额常量 | 说明 |
|----------|------|
| `PRINCIPLE_TOTAL` | 理论题总数 |
| `PROJECT_TOTAL` | 项目题总数 |
| `SCENARIO_TOTAL` | 场景题总数 |
| `BEHAVIORAL_TOTAL` | 行为题总数 |
| `SAME_POINT_CONTINUE` | 同知识点连续追问次数 |
| `SAME_DOMAIN_CONTINUE` | 同知识域连续追问次数 |
| `SAME_PROJECT_CONTINUE` | 同项目连续追问次数 |
| `SAME_PROJECT_POINT_CONTINUE` | 同项目要点连续追问次数 |

#### 配额动态调整

配额上限根据候选人经验级别动态调整：

| 经验级别 | 配额系数 |
|----------|----------|
| JUNIOR | 较低 |
| MID | 中等 |
| SENIOR | 较高 |

### 3. 状态账本初始化

状态账本在面试开始时通过`StateLedgerInitService`初始化：

```java
// 初始化流程
1. 初始化所有配额为0
2. 设置最大题数（根据经验级别）
3. 初始化知识域覆盖列表为空
4. 初始化兜底旋转索引为0
```

### 4. 状态账本更新

状态账本通过`DefaultStateLedgerReducer`进行更新，更新规则由策略的`StrategyQuotaPolicy`定义：

| 配额策略类型 | 说明 |
|--------------|------|
| `incrementOnly` | 只递增指定配额 |
| `incrementAndReset` | 递增部分配额，重置另一部分 |
| `enter` | 进入新题型时的配额处理 |
| `none` | 不更新任何配额 |

### 5. 兜底机制状态

状态账本维护兜底机制的旋转索引，当AI决策连续失败时，系统会按顺序尝试不同的兜底策略：

```java
// 兜底策略旋转
rotationIndex = (rotationIndex + 1) % FALLBACK_STRATEGY_COUNT
```

---

## 策略池筛选与联动

### 1. 策略目录

`StrategyCatalog`是策略的单一真实来源(SSOT)，定义了所有可用的面试策略。

#### 策略分类

| 策略类型 | 说明 | 示例 |
|----------|------|------|
| **内部策略(ADVANCE)** | 在当前题型内深入追问 | S_P_VERIFY（引导和验证） |
| **切换策略(SWITCH)** | 在当前题型内切换焦点或项目 | S_SWITCH_DOMAIN（切换知识域） |
| **进入策略(ENTER)** | 切换到其他题型 | S_ENTER_PROJECT（进入项目题） |
| **结束策略(END/WRAPUP)** | 结束面试 | S_WRAPUP（结束面试） |

#### 题型定义

| 题型代码 | 说明 |
|----------|------|
| INTRO | 自我介绍 |
| PRINCIPLE | 理论题 |
| PROJECT_DEEP_DIVE | 项目深挖题 |
| SCENARIO | 场景题 |
| BEHAVIORAL | 行为题 |

### 2. 策略定义

每个策略包含以下关键属性：

```java
StrategyDefinition {
    code: 策略编码
    label: 策略标签
    description: 策略描述
    applicableWhen: 适用条件
    moveType: 策略类型(ADVANCE/SWITCH/ENTER/END)
    allowedCurrentQuestionTypes: 允许的当前题型
    targetQuestionType: 目标题型
    requiresTargetDomain: 是否需要目标知识域
    requiredContext: 需要的上下文
    blockingLimits: 阻塞配额（超限则策略不可用）
    quotaUpdatePolicy: 配额更新策略
    wrapup: 是否为结束策略
}
```

### 3. 策略池筛选

`AvailableStrategyAssembler`根据当前面试上下文动态筛选合法策略：

#### 筛选规则

```
1. 结束策略总是可用
2. 如果策略需要目标知识域但剩余知识域为空 → 不可用
3. 如果策略需要项目上下文但没有项目信息 → 不可用
4. 如果同点连续追问已达配额上限 → 不可用
5. 检查策略定义的所有blockingLimits，任一超限 → 不可用
6. 所有检查通过 → 可用
```

#### 筛选输入

| 参数 | 说明 |
|------|------|
| `currentQuestionType` | 当前题目类型 |
| `hasProjectContext` | 是否有项目上下文 |
| `remainingTargetDomains` | 剩余待考察的知识域列表 |
| `quotaState` | 配额使用状态 |
| `experienceLevel` | 候选人经验级别 |

### 4. 策略联动

策略之间通过状态账本和配额系统联动：

```
AI选择策略 → 执行策略动作 → 更新状态账本 → 影响后续策略筛选
```

#### 示例：理论题策略联动

1. **S_P_VERIFY**（引导和验证）→ 递增`SAME_POINT_CONTINUE`
2. **S_P_DEEP_LINK**（深入强关联点）→ 递增`SAME_DOMAIN_CONTINUE`，重置`SAME_POINT_CONTINUE`
3. **S_P_SAME_DOMAIN_SHIFT**（同域平移）→ 递增`SAME_DOMAIN_CONTINUE`，重置`SAME_POINT_CONTINUE`
4. **S_SWITCH_DOMAIN**（切换知识域）→ 递增`PRINCIPLE_TOTAL`，重置`SAME_POINT_CONTINUE`和`SAME_DOMAIN_CONTINUE`

---

## 完整协作流程

### 1. 面试创建流程（Planner编排）

```
用户创建面试
    ↓
读取岗位、简历、JD、历史信息
    ↓
调用planner生成考纲(syllabus_json)
    ↓
初始化状态账本(state_ledger_json)
    ↓
生成第一道题目
    ↓
更新会话状态为in_progress
```

**核心实现**：`PlannerOrchestrationService.runAsync()`

### 2. 回答提交流程

```
用户提交回答
    ↓
幂等性检查
    ↓
参数校验（会话、权限、状态）
    ↓
强制结束检查（最大题数）
    ↓
构建评估决策输入
    ├─ 历史上下文
    ├─ 配额状态
    ├─ 知识域覆盖
    ├─ 剩余目标域
    └─ 可用策略池
    ↓
调用evaluation-decision模型
    ↓
三级容错处理
    ├─ 第一级：决策验证
    ├─ 第二级：决策修复
    └─ 第三级：系统兜底
    ↓
持久化回答记录
    ↓
更新状态账本
    ↓
返回决策结果(continue/wrapup)
    ↓
(如continue) 前端通过SSE触发生成下一题
```

**核心实现**：`AnswerSubmitService.submitAnswer()`

### 3. 决策容错三级机制

#### 第一级：决策验证

- **组件**：`DecisionExecutionPlanBuilder`
- **验证内容**：
  - 策略编码是否合法
  - 策略是否在可用策略池中
  - 目标知识域是否有效（如需要）
  - 配额是否超限

#### 第二级：决策修复

- **组件**：`DecisionRepairOrchestrator`
- **修复策略**：
  - 尝试理解AI意图
  - 选择最接近的合法策略
  - 补全缺失字段

#### 第三级：系统兜底

- **组件**：`SystemFallbackPlanBuilder`
- **兜底策略**：
  - 按旋转索引顺序尝试
  - 优先选择continue策略
  - 必要时选择wrapup策略

### 4. 下一题生成流程

```
前端通过SSE连接
    ↓
根据决策结果构建题目生成输入
    ├─ 决策策略
    ├─ 目标知识域
    ├─ 焦点点
    ├─ RAG检索计划
    └─ 历史上下文
    ↓
执行RAG检索（如需要）
    ↓
调用question-generation-stream模型
    ↓
流式返回题目文本
    ↓
持久化题目
    ↓
更新状态账本
    ↓
(可选) 触发TTS语音合成
```

### 5. 面试结束流程

```
AI决策wrapup或达到最大题数
    ↓
持久化最终回答
    ↓
更新状态账本
    ↓
更新会话状态为report_generating
    ↓
异步启动报告生成
    ↓
调用report-generation模型
    ↓
生成整场面试报告
    ↓
更新会话状态为completed
```

**核心实现**：`ReportGenerationService.generateAsync()`

---

## 总结

本面试系统通过多Agent协作架构实现了智能化、可控化的面试流程：

1. **RAG检索**提供知识库支持，确保题目和评估的专业性
2. **状态账本**维护全局状态，确保面试流程的连贯性和均衡性
3. **策略池**定义并筛选合法策略，确保AI决策在可控范围内
4. **三级容错机制**保证系统稳定性，即使AI决策失败也能继续面试

这套架构既充分发挥了AI的智能性，又通过严格的状态管理和策略约束保证了面试的质量和可控性。

---

*文档版本: v1.0 | 更新日期: 2026-04-01*
