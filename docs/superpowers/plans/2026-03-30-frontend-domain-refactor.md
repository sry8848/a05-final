# 前端知识域重构实施计划（当前版）

## 目标

在开发阶段一次性完成前端知识域收口，让 `FRONTEND` 的知识域定义达到与 Java 后端相近的可评估性、可追问性和可统计性。

## 当前原则

- 不按 `HTML / CSS / JS` 学习顺序直接拆一级知识域
- 优先按面试评估边界拆分，而不是按文件后缀拆分
- 不保留 `vue_react` 这种混合域
- 知识域定义要同时服务于：
  - AI 规划与追问
  - RAG 题卡标注
  - 面试报告统计
  - 薄弱项分析

## 当前问题

当前 `FRONTEND` 只有 6 个域，定义见 `schema-position.sql`：

- `js_core`
- `browser`
- `vue_react`
- `css_layout`
- `performance`
- `network`

这套定义的主要问题是：

1. 缺少高级前端关键域：`TypeScript`、工程化、应用架构、安全
2. `vue_react` 把两个框架混成一类，不利于深问
3. `performance` 是横切能力，不适合作为粗糙一级域单独存在
4. `js_core`、`browser`、`network` 边界冲突严重
5. `HTML` 语义化、表单规范、无障碍没有明确归属

## 目标 9 域

建议将 `FRONTEND` 重构为以下 9 个知识域：

- `js_ts_core`
- `browser_runtime`
- `ui_foundation`
- `react`
- `vue`
- `frontend_engineering`
- `app_architecture`
- `web_network_security`
- `web_performance`

## 目标域语义

### 1. `js_ts_core`

- JavaScript / TypeScript 语言基础
- 闭包、原型链、this、模块化
- Promise、async/await、异步模型
- TypeScript 类型系统、泛型、条件类型、类型推断

### 2. `browser_runtime`

- 浏览器解析与运行时机制
- DOM API、事件系统、事件委托
- DOM Tree / CSSOM / Render Tree
- 回流、重绘、合成
- 页面生命周期、缓存与浏览器调度

### 3. `ui_foundation`

- HTML 语义化
- 页面结构与表单规范
- 无障碍基础
- CSS 布局、响应式设计
- 组件样式、设计系统落地

说明：

- “页面结构设计”归 `ui_foundation`
- “DOM 运行时模型”归 `browser_runtime`

### 4. `react`

- React 组件模型
- Hooks
- 状态与副作用
- 渲染机制
- React 生态常见工程实践

### 5. `vue`

- Vue 组件模型
- 响应式系统
- `watch` / `watchEffect`
- 组合式 API
- Vue 生态常见工程实践

### 6. `frontend_engineering`

- Vite / Webpack
- Babel / SWC / 编译转换
- Tree Shaking / 分包 / 构建优化
- 包管理、Monorepo、发布流程

### 7. `app_architecture`

- 状态管理
- 组件边界设计
- 模块拆分
- 前端数据流
- 页面级与应用级架构取舍

### 8. `web_network_security`

- HTTP / HTTPS / WebSocket
- 缓存协商、跨域、鉴权
- XSS / CSRF / CSP / Cookie / SameSite
- 前端常见安全与网络交互问题

### 9. `web_performance`

- Core Web Vitals
- 首屏优化
- 长任务与卡顿定位
- 渲染性能与资源加载优化
- 性能监控与排障

## 为什么不直接用 `HTML / CSS / JS`

如果目标是面试评估，而不是课程编排，`HTML / CSS / JS` 三分法不够稳定：

- `HTML` 太薄，难以支撑连续追问
- `CSS` 会和无障碍、结构设计、组件规范混在一起
- `JS` 太宽，会把语言、运行时、DOM、TypeScript、框架机制全部吞掉

因此更适合把它们吸收到：

- `ui_foundation`
- `browser_runtime`
- `js_ts_core`

## 旧域到新域的映射

建议使用如下迁移规则：

- `js_core` -> `js_ts_core`
- `browser` -> `browser_runtime`
- `css_layout` -> `ui_foundation`
- `network` -> `web_network_security`
- `performance` -> `web_performance`
- `vue_react` -> 需要拆分：
  - React 题迁到 `react`
  - Vue 题迁到 `vue`
  - 纯组件架构题视内容迁到 `app_architecture`

补充约束：

- HTML 语义化、结构规范、表单与无障碍题，统一归 `ui_foundation`
- DOM 事件、渲染流程、回流重绘题，统一归 `browser_runtime`
- 纯语言 / TypeScript 类型系统题，统一归 `js_ts_core`

## 需要人工复标的题型

以下旧题不能机械迁移，需要人工或规则辅助复标：

- `vue_react` 下同时包含 React 与 Vue 关键词的题
- 既讲浏览器渲染又讲首屏优化的题
- 既讲网络缓存又讲安全策略的题
- 既讲 TypeScript 类型系统又强依赖框架上下文的题

## 执行顺序

### 1. 先改真源

- 修改 `schema-position.sql`
- 直接覆写 `FRONTEND` 种子知识域定义
- 删除旧 6 域，不做并存版本

### 2. 再补边界文档

- 增加一份前端知识域边界说明
- 明确每个域的“包含 / 不包含”
- 特别写清：
  - `ui_foundation` vs `browser_runtime`
  - `js_ts_core` vs `react/vue`
  - `web_network_security` vs `web_performance`

当前边界说明文档路径：

- `docs/superpowers/specs/2026-03-30-frontend-domain-boundaries.md`

### 3. 再清样例与题卡

- RAG 样例
- Prompt 示例
- 测试样例
- 手工题卡样本

都要切到新前端域，不再出现旧 6 域编码。

### 4. 再做题库重标

- 先按映射规则自动迁移简单题
- 再人工复标 `vue_react` 等高风险旧题

当前题卡重标执行清单路径：

- `docs/superpowers/specs/2026-03-30-frontend-domain-relabel-playbook.md`

### 5. 最后收口主链路

- Planner
- Evaluation
- 报告统计
- 成长中心

统一以新前端知识域为准。

## 验收方式

1. `FRONTEND` 不再是旧 6 域集合
2. 主链路不再出现 `vue_react`
3. `HTML` 语义化与无障碍题能稳定归到 `ui_foundation`
4. React / Vue 题不再混在同一主域
5. TypeScript / 工程化 / 架构 / 安全题有明确一级域归属
6. 报告与统计中的前端知识域分布不再明显失真

## 当前结论

前端知识域不应沿用课程导向的 `HTML / CSS / JS` 一级拆分，而应按面试评估边界重构。

当前推荐方向已经明确：

- 用 `ui_foundation` 吸收 HTML 语义化、结构规范、无障碍与 CSS 布局
- 用 `browser_runtime` 承载浏览器机制与 DOM 运行时模型
- 用 `js_ts_core` 承载语言与类型系统
- 用 `react` / `vue` / `frontend_engineering` / `app_architecture` / `web_network_security` / `web_performance` 补齐高级前端能力
