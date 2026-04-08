# 前端题卡重标与人工复标执行清单（当前版）

## 目的

本清单用于在 `FRONTEND` 知识域完成 9 域重构后，指导题卡、收藏题和历史快照的域值迁移与人工复标。

目标不是“保留历史兼容”，而是把旧前端 6 域彻底清掉，统一到当前 9 域：

- `js_ts_core`
- `browser_runtime`
- `ui_foundation`
- `react`
- `vue`
- `frontend_engineering`
- `app_architecture`
- `web_network_security`
- `web_performance`

## 当前仓库结论

基于本地仓库扫描，当前只发现：

- 前端知识域真源定义：[schema-position.sql](/D:/a05-cursor/backend/src/main/resources/db/schema-position.sql)
- 收藏题结构表：[schema-question-bank.sql](/D:/a05-cursor/backend/src/main/resources/db/schema-question-bank.sql)

当前仓库 **没有提供独立的前端题库种子文件或 JSONL 题卡集**，因此这一步不能伪装成“已批量迁移真实题库”。  
本清单的真实用途是：

1. 给后续导入或已有数据库中的前端题卡提供迁移规则
2. 给人工复标提供统一判断标准

## 自动迁移范围

以下旧域可以直接自动迁移：

| 旧域 | 新域 | 说明 |
| --- | --- | --- |
| `js_core` | `js_ts_core` | 纯 JavaScript / TypeScript 语言、异步、模块化、类型系统 |
| `browser` | `browser_runtime` | DOM API、事件系统、渲染流程、回流重绘 |
| `css_layout` | `ui_foundation` | HTML 语义化、结构规范、表单、无障碍、CSS 布局 |
| `network` | `web_network_security` | HTTP、缓存协商、跨域、鉴权、安全策略 |
| `performance` | `web_performance` | 首屏优化、长任务、性能指标、加载优化 |

## 禁止自动迁移的范围

以下旧域 **不能** 直接批量迁移：

| 旧域 | 原因 | 处理方式 |
| --- | --- | --- |
| `vue_react` | 同时混了 React、Vue 和部分组件架构题 | 必须人工复标或规则辅助后人工确认 |

## 人工复标判断规则

### 归 `react`

命中以下任一特征，优先归 `react`：

- `Hooks`
- `useEffect`
- `useLayoutEffect`
- `useMemo`
- `useRef`
- `JSX`
- `Concurrent Rendering`
- `React Router`
- `Redux` 但题目焦点仍在 React 组件/渲染心智

### 归 `vue`

命中以下任一特征，优先归 `vue`：

- `Composition API`
- `watch`
- `watchEffect`
- `ref`
- `reactive`
- `computed`
- `Pinia`
- `Vue Router`
- 响应式依赖收集

### 归 `app_architecture`

即使题中出现 React / Vue，只要焦点是以下内容，优先归 `app_architecture`：

- 组件边界怎么拆
- 状态放哪里
- 页面级和应用级状态分层
- 模块拆分
- BFF / 前端中台 / 微前端的边界取舍
- 数据流设计

### 归 `ui_foundation`

出现以下内容时，不要误归 `browser_runtime`：

- HTML 语义化
- 表单结构规范
- `label` / `input` 绑定
- 可访问性
- Flex / Grid / 响应式布局
- 组件样式系统

### 归 `browser_runtime`

出现以下内容时，不要误归 `ui_foundation`：

- DOM API
- 事件冒泡 / 捕获
- 事件委托
- DOM Tree / CSSOM / Render Tree
- 回流 / 重绘 / 合成
- 页面生命周期

### `web_network_security` 与 `web_performance` 的分界

- 讨论“协议、安全正确性”的题，归 `web_network_security`
- 讨论“速度、卡顿、加载体验”的题，归 `web_performance`

## 数据库排查 SQL 模板

### 1. 找出仍使用旧前端域的收藏题

```sql
SELECT id, user_id, session_id, question_id, domain_code, created_at
FROM question_bank_items
WHERE domain_code IN (
  'js_core',
  'browser',
  'vue_react',
  'css_layout',
  'network',
  'performance'
)
ORDER BY created_at DESC, id DESC;
```

### 2. 自动迁移可直接映射的旧域

```sql
UPDATE question_bank_items
SET domain_code = CASE domain_code
  WHEN 'js_core' THEN 'js_ts_core'
  WHEN 'browser' THEN 'browser_runtime'
  WHEN 'css_layout' THEN 'ui_foundation'
  WHEN 'network' THEN 'web_network_security'
  WHEN 'performance' THEN 'web_performance'
  ELSE domain_code
END
WHERE domain_code IN (
  'js_core',
  'browser',
  'css_layout',
  'network',
  'performance'
);
```

### 3. 单独拉出 `vue_react` 待人工复标队列

```sql
SELECT id, user_id, session_id, question_id, domain_code, source_snapshot_json, created_at
FROM question_bank_items
WHERE domain_code = 'vue_react'
ORDER BY created_at DESC, id DESC;
```

## 历史快照修正建议

如果 `source_snapshot_json` 里保留了旧域字段，建议同步修正，避免后续前端展示或离线分析继续看到旧值。

优先检查：

- `source_snapshot_json.domainCode`
- `source_snapshot_json.domainName`
- 任何嵌套 `evaluatedDomains[*].domainCode`

这部分是否批量改 JSON，取决于你后续是否真的会消费历史快照。  
如果只以 `question_bank_items.domain_code` 为主，快照可以后置处理；如果前端页面直接展示快照域名，就应一并修正。

## 人工复标最小工作流

1. 先批量迁移 5 类安全旧域：
   - `js_core`
   - `browser`
   - `css_layout`
   - `network`
   - `performance`
2. 单独导出 `vue_react` 队列
3. 按题干关键词做第一轮分桶：
   - React
   - Vue
   - 架构
   - 待人工判断
4. 对“同时出现框架名 + 架构词”的题，以题目焦点而不是关键词数量定归属
5. 人工复核后，再统一写回正式域值

## 验收标准

满足以下条件，才算题卡重标完成：

1. 数据库中不再存在旧前端 6 域值
2. `vue_react` 队列已经清空
3. React 与 Vue 题不再混在同一主域
4. HTML 语义化 / 无障碍题稳定归 `ui_foundation`
5. 浏览器机制题稳定归 `browser_runtime`
6. TypeScript、工程化、架构、安全、性能题都有明确一级域归属

## 结论

task5 的关键不是“机械替换字符串”，而是：

- 能自动迁移的先自动迁移
- `vue_react` 这类混域题必须人工复标
- 在仓库当前没有真实前端题卡种子的前提下，先把复标规则和执行清单固定下来，避免后续拿到题库时再次发生边界漂移
