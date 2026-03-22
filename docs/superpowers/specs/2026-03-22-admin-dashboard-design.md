# 管理端 Dashboard 首版设计

## 背景

当前仓库里已经存在管理端前端骨架，包括：

- `frontend/src/components/AdminLayout.vue`
- `frontend/src/components/AdminDashboard.vue`
- `frontend/src/components/PromptLab.vue`
- `frontend/src/components/SystemMonitor.vue`

但这些页面目前主要是静态演示数据，不是基于真实后端数据的可用后台。

本项目现阶段最稳定、最清晰的数据基础主要来自以下几类：

- `users`：用户注册和增长数据
- `interview_sessions`：面试会话和状态数据
- `ai_invocation_logs`：模型调用、Prompt 版本、Token、成功率、延迟数据
- `PromptProperties` + `PromptTemplateService`：当前生效 Prompt 版本配置

因此，管理端第一阶段不应直接照搬“全量监控后台”的想象图，而应先落一版真实可用的混合型首页：既能看业务概览，也能看模型运行状态。

## 目标

本设计文档定义管理端首页 `dashboard` 的第一版范围、信息架构、数据口径和接口边界，用于后续拆计划和实现。

管理员登录后台后，首页应能在一个页面内快速回答以下问题：

- 当前系统有没有人在使用
- 最近 7 天用户和面试规模是上涨还是下降
- AI 调用是否稳定
- 当前实际生效的 Prompt 版本是什么

## 范围

本文档只定义首版管理端首页 `dashboard`，不覆盖以下模块的完整实现：

- Prompt 实验室
- 模型路由与成本中心
- RAG 语料管理
- 队列/Worker/死信队列管理
- WebSocket 音频流质量监控
- 管理员权限体系和完整 RBAC

## 关键纠偏

### 1. “活跃面试房间数”首版应改为“活跃面试数”

当前仓库没有真正的视频房间实体，也没有专门的房间监控表。首版如果继续叫“活跃面试房间数”，会让人误以为系统已有独立的 RTC 房间模型。

首版建议统一改为：

- `活跃面试数`

计算口径来自 `interview_sessions.status`，更准确也更可实现。

### 2. “Token 成本”首版应改为“Token 消耗”

当前 `ai_invocation_logs` 表中有：

- `request_tokens`
- `response_tokens`
- `model_provider`
- `model_name`

但没有统一的价格快照表或价格配置表。因此首版可以稳定展示：

- `今日 Token 消耗`
- `最近 7 天 Token 消耗趋势`

不能把货币值当成真实成本直接展示。因为不同模型、不同供应商、不同时间的单价都可能不同，单靠日志表无法保证价格换算准确。

如果后续补一张模型价格配置表，或者提供固定价格配置，再把它扩展为：

- `预估 Token 成本`

在此之前，首页不要写美元或人民币成本数值。

## 设计原则

- 第一版只做真实数据驱动的模块，不做大面积占位演示块
- 首页聚焦“总览”，不承载深度运维能力
- 每个模块都必须能说明清楚数据来源和计算口径
- 单个模块失败时不能拖垮整个首页
- 前后端都应拆成边界清晰的小单元，避免继续把所有逻辑堆进现有超大组件

## 首版信息架构

首页采用“混合型”结构，但以真实可落地的数据能力优先，页面分为四个区块。

### 1. 顶部核心卡片区

建议放 6 张卡片：

1. 总用户数
2. 今日新增用户
3. 面试总场次
4. 今日面试场次
5. 活跃面试数
6. 今日 Token 消耗

这 6 张卡片覆盖了业务规模、当天活跃度和系统资源消耗，符合“业务 + 运行状态混合展示”的目标。

### 2. 中部趋势图区

建议放 3 张最近 7 天趋势图：

1. 最近 7 天新增用户
2. 最近 7 天面试场次
3. 最近 7 天 Token 消耗

每张图都采用按天聚合，不做小时级趋势。首页第一版的重点是让管理员快速看趋势，不是做分析平台。

### 3. 右侧运行状态区

右侧区域包含两个子块：

- 模型状态面板
- 基础系统状态卡

其中模型状态面板展示最近一个观测窗口内的模型运行摘要，建议默认窗口为最近 15 分钟。

每个模型项展示：

- 模型供应商
- 模型名称
- 调用次数
- 成功率
- 平均延迟
- 错误次数
- 状态标签（正常 / 警告 / 异常）

基础系统状态卡只放轻量信息：

- 后端接口是否可达
- 最近一次刷新时间
- 服务端时间
- 当前统计时区

这个卡片不做复杂主机监控，只承担“后台服务当前是否可访问”的基础提示职责。

### 4. 底部 Prompt 摘要区

展示当前生效 Prompt 版本摘要，而不是完整 Prompt 历史。

每个 Prompt 项展示：

- `promptCode`
- 当前配置版本
- 模板元数据版本
- 来源路径
- 最近 24 小时调用次数
- 最近调用时间

这个区块的目标是让管理员快速知道“线上到底跑的是哪个版本”，不是在首页做 Prompt 管理中心。

## 明确不进入首版的内容

以下内容可以保留到后续后台能力建设阶段，但不进入本次 dashboard 设计范围：

- 任务队列状态
- Worker 状态
- 死信队列管理
- WebSocket 连接状态
- 音频流延迟、丢包、抖动
- A/B 测试结果面板
- Prompt 提交历史时间线
- 主动模型探活中心

原因不是这些页面永远不做，而是当前仓库没有与之匹配的稳定后端实体和管理接口。第一版如果强上，只会得到“看起来很复杂，实际上没有真实数据闭环”的假后台。

## 数据口径

### 时区口径

所有“今日”和“最近 7 天”统计统一按 `Asia/Shanghai` 自然日计算。

定义：

- `今日`：本地时区当日 `00:00:00` 到次日 `00:00:00`
- `最近 7 天`：包含今天在内的连续 7 个自然日

前后端必须统一口径，不能前端本地算日期、后端按 UTC 聚合，否则图表会错位。

### 顶部卡片口径

#### 总用户数

- 数据源：`users`
- 口径：首版按 `users` 表总记录数统计

说明：

- 当前 spec 不引入复杂用户状态过滤规则
- 如果后续出现“封禁用户不计入总用户”的业务要求，再单独调整口径

#### 今日新增用户

- 数据源：`users.created_at`
- 口径：今日创建用户数

#### 面试总场次

- 数据源：`interview_sessions`
- 口径：`interview_sessions` 全表总记录数

#### 今日面试场次

- 数据源：`interview_sessions.created_at`
- 口径：今日创建的面试会话数

#### 活跃面试数

- 数据源：`interview_sessions.status`
- 口径：状态为以下集合的会话数：
  - `planning`
  - `in_progress`
  - `report_generating`

不包含：

- `created`
- `completed`
- `aborted`

原因：

- `created` 仅表示会话已建，但未必进入实际面试流程
- `completed` 和 `aborted` 已经不是活跃流程

#### 今日 Token 消耗

- 数据源：`ai_invocation_logs.request_tokens` + `ai_invocation_logs.response_tokens`
- 口径：今日所有 AI 调用输入输出 Token 总和

计算公式：

`sum(coalesce(request_tokens, 0) + coalesce(response_tokens, 0))`

### 趋势图口径

#### 最近 7 天新增用户

- 数据源：`users.created_at`
- 聚合粒度：按天
- 空桶补零：需要

#### 最近 7 天面试场次

- 数据源：`interview_sessions.created_at`
- 聚合粒度：按天
- 空桶补零：需要

#### 最近 7 天 Token 消耗

- 数据源：`ai_invocation_logs.created_at`
- 聚合粒度：按天
- 空桶补零：需要

### 模型状态区口径

观测窗口默认 `15` 分钟，可由后端参数扩展，但首页先固定默认值。

每个模型以 `(model_provider, model_name)` 为一组进行聚合，展示：

- `requestCount`：窗口内调用次数
- `successCount`：窗口内成功次数
- `errorCount`：窗口内失败次数
- `successRate`：`successCount / requestCount`
- `avgLatencyMs`：平均延迟
- `p95LatencyMs`：P95 延迟，可选

状态标签建议按如下规则生成：

- `正常`：`successRate >= 0.98` 且 `avgLatencyMs < 1500`
- `警告`：`successRate >= 0.95` 且 `avgLatencyMs < 3000`
- `异常`：不满足以上条件

如果窗口内没有调用数据，不显示“正常”，而应显示：

- `暂无数据`

### Prompt 摘要区口径

当前生效 Prompt 版本不应依赖 `ai_invocation_logs` 反推，因为“最近没流量”并不代表“当前没配置”。

推荐数据来源组合：

- `PromptProperties.asVersionMap()`：当前配置版本
- `PromptTemplateService.loadMetadata(promptCode)`：模板元信息
- `ai_invocation_logs`：补充最近调用次数和最近使用时间

## 前端页面结构

建议将现有 [AdminDashboard.vue](D:/a05-cursor/frontend/src/components/AdminDashboard.vue) 拆分为更小的可维护组件，避免继续向单文件堆积。

推荐拆分：

- `AdminDashboardPage`
  - 负责页面装配和各模块请求调度
- `DashboardOverviewCards`
  - 负责顶部卡片区
- `DashboardTrendSection`
  - 负责 3 张趋势图
- `DashboardModelStatusPanel`
  - 负责模型状态区
- `DashboardPromptSummaryPanel`
  - 负责 Prompt 摘要区
- `DashboardErrorState`
  - 负责局部异常和重试状态

### 页面加载策略

- 页面进入后并发请求各区块数据
- 卡片区和趋势图区可以先加载
- 模型状态区允许独立轮询刷新
- Prompt 摘要区以首屏加载为主，不需要高频刷新

推荐刷新策略：

- 卡片区：30 到 60 秒刷新一次
- 模型状态区：30 秒刷新一次
- 趋势图区：手动刷新或 5 分钟刷新
- Prompt 摘要区：首屏加载 + 手动刷新

### 局部失败策略

如果某个接口失败：

- 只让对应卡片/面板进入错误态
- 其他面板继续显示
- 页面顶部不做全屏崩溃态

每个模块至少支持：

- 加载中
- 加载失败
- 空数据
- 正常展示

## 后端接口设计

首版建议不要做一个巨型 dashboard 全量接口，而是按页面区块拆 4 个 dashboard 聚合接口，并复用现有 `system/ping` 作为基础状态探针，降低耦合。

### 1. 概览接口

`GET /api/v1/admin/dashboard/overview`

返回示例：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "totalUsers": 2847,
    "newUsersToday": 156,
    "totalInterviews": 15632,
    "interviewsToday": 423,
    "activeInterviews": 23,
    "totalTokensToday": 1289432
  }
}
```

### 2. 趋势接口

`GET /api/v1/admin/dashboard/trends?days=7`

返回示例：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "dates": ["03-16", "03-17", "03-18", "03-19", "03-20", "03-21", "03-22"],
    "newUsers": [12, 18, 14, 21, 19, 25, 16],
    "interviews": [34, 40, 31, 45, 39, 48, 42],
    "totalTokens": [120003, 150430, 133220, 181240, 165400, 195340, 176200]
  }
}
```

### 3. 模型状态接口

`GET /api/v1/admin/dashboard/models?windowMinutes=15`

返回示例：

```json
{
  "code": 0,
  "message": "OK",
  "data": [
    {
      "modelProvider": "openai",
      "modelName": "gpt-4o-mini",
      "requestCount": 124,
      "successCount": 122,
      "errorCount": 2,
      "successRate": 0.9839,
      "avgLatencyMs": 842,
      "p95LatencyMs": 1560,
      "status": "healthy"
    }
  ]
}
```

### 4. Prompt 摘要接口

`GET /api/v1/admin/dashboard/prompts`

返回示例：

```json
{
  "code": 0,
  "message": "OK",
  "data": [
    {
      "promptCode": "planner",
      "configuredVersion": "v2",
      "templateVersion": "v2",
      "sourcePath": "classpath:prompts/planner.md",
      "lastUsedAt": "2026-03-22T15:42:11+08:00",
      "callsLast24Hours": 183
    }
  ]
}
```

### 5. 基础系统状态接口

首版可以直接复用已有接口：

`GET /api/v1/system/ping`

页面端自行补充：

- 本地最近刷新时间
- 固定展示时区 `Asia/Shanghai`

该接口只用于“系统可达性”和时间基线，不作为复杂健康检查中心。

## 后端模块边界

建议在 `backend` 中新增独立的 dashboard 聚合层，而不是把查询逻辑散落到各业务模块 Controller。

推荐结构：

- `admin/dashboard/AdminDashboardController`
- `admin/dashboard/AdminDashboardService`
- `admin/dashboard/dto/*`
- `admin/dashboard/query/*`

职责划分：

- `Controller`
  - 仅负责参数接收和响应输出
- `Service`
  - 负责聚合多个领域模块的数据
- `query`
  - 负责偏统计类查询，避免把复杂 SQL 直接塞进通用业务 Service

这样后续即使继续扩展 `analysis`、`monitor`、`prompt-lab`，也不会把首版 dashboard 的聚合逻辑污染到别的模块。

## 权限与安全假设

本设计默认 dashboard 属于管理员可见页面。

但本次 spec 不展开完整管理员体系设计，只约定：

- dashboard 聚合接口应为受保护接口
- 未登录用户不得访问
- 普通 C 端用户不得访问

如果当前后端尚未完成管理员角色体系，可以在实现阶段先提供最小可用保护方案，但不能把这些接口做成公开接口。

## 空数据和降级策略

### 系统刚启动、还没有真实业务数据

应允许首页显示 0 值和空图表，不视为异常。

例如：

- 总用户数 = 0
- 面试总场次 = 0
- 趋势图全 0
- 模型状态区显示“最近 15 分钟暂无调用数据”

### `ai_invocation_logs` 不完整

如果历史日志中部分记录缺少 `request_tokens` 或 `response_tokens`：

- 聚合时按 `0` 处理
- 不阻断整张卡片

### Prompt 配置与模板版本不一致

如果读取 Prompt 摘要时发现：

- 配置版本与模板元数据版本不一致

应在 Prompt 摘要区明确显示异常状态，而不是静默返回正常版本信息。

## 验收标准

满足以下条件即可视为首版 dashboard 达到设计目标：

- 管理员登录后默认先看到 dashboard
- 首页只展示真实可计算的指标
- 顶部 6 张卡片数据均来自真实后端接口
- 最近 7 天三张趋势图按 `Asia/Shanghai` 口径正确聚合
- 模型状态区可展示最近窗口内的真实成功率和延迟
- Prompt 摘要区能显示当前配置版本，而不是仅显示历史调用版本
- 任一面板加载失败时，其余面板仍能正常显示

## 后续扩展位

以下内容明确作为后续演进，不纳入本次实现：

- 预估 Token 成本
  - 前提：新增模型价格配置
- 队列与 Worker 监控
  - 前提：补充真实队列和执行器管理接口
- 死信队列操作台
  - 前提：补充消息系统可观测与重试接口
- Prompt 历史时间线
  - 前提：补充 Prompt 发布记录实体
- A/B 测试看板
  - 前提：补充 Prompt 实验分流与结果统计模型

## 实现建议摘要

首版 dashboard 应被视为“真实数据总览页”，不是“全能运维中心”。

最重要的决策有三个：

1. 首页先只做真实数据闭环，不做大面积占位监控块
2. “活跃面试房间数”改为“活跃面试数”
3. “Token 成本”改为“Token 消耗”，成本延后到有价格配置后再做

按这个边界推进，第一版可以很快做出真正能用的管理端首页，也能为后续 PromptLab、系统监控、分析中心留出清晰的扩展边界。
