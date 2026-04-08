# 前端知识域边界说明（当前版）

## 目的

本说明用于固定 `FRONTEND` 岗位知识域的面试评估边界，避免：

- 题卡标注时一题多归
- AI 规划时知识域漂移
- 报告统计时同类能力被打散

当前前端知识域不采用课程导向的 `HTML / CSS / JS` 一级拆分，而采用面试评估导向的 9 域体系。

## 当前 9 域

- `js_ts_core`
- `browser_runtime`
- `ui_foundation`
- `react`
- `vue`
- `frontend_engineering`
- `app_architecture`
- `web_network_security`
- `web_performance`

## 核心边界

### `js_ts_core`

包含：

- JavaScript 语言基础
- 闭包、原型链、this、作用域
- Promise、async/await、异步模型
- ES Module / CommonJS
- TypeScript 类型系统、泛型、条件类型、类型推断

不包含：

- 浏览器 DOM API
- 浏览器渲染流程
- React / Vue 专属机制
- 构建工具链

典型题：

- `Promise.allSettled` 和 `Promise.all` 的差异
- `infer` 在条件类型中的作用
- 闭包导致的 stale value 问题

### `browser_runtime`

包含：

- 浏览器解析流程
- DOM API
- 事件传播、事件委托
- DOM Tree / CSSOM / Render Tree
- 回流、重绘、合成
- 页面生命周期
- 浏览器缓存机制与调度行为

不包含：

- HTML 语义化设计
- 表单结构规范
- 组件样式设计
- HTTP 鉴权与安全策略

典型题：

- 事件委托为什么成立
- 为什么修改样式会触发回流或重绘
- 浏览器从 HTML 到可视渲染经历了哪些阶段

### `ui_foundation`

包含：

- HTML 语义化
- 页面结构规范
- 表单语义
- 无障碍基础
- CSS 布局
- 响应式设计
- UI 组件样式系统

不包含：

- DOM 事件系统底层
- 浏览器解析和渲染原理
- 框架内部渲染机制
- 工程化构建链路

典型题：

- 为什么按钮不应该随意用 `div` 模拟
- `label` 和 `input` 的正确绑定方式
- Flex、Grid 与响应式布局取舍

### `react`

包含：

- React 组件模型
- Hooks
- 状态与副作用
- React 渲染机制
- React 常见生态实践

不包含：

- Vue 响应式实现
- 通用 JS 语言基础
- 构建工具底层原理

典型题：

- `useEffect` 与 `useLayoutEffect`
- stale closure
- React 状态更新与渲染行为

### `vue`

包含：

- Vue 组件模型
- 响应式系统
- Composition API
- `watch` / `watchEffect`
- Vue 常见生态实践

不包含：

- React Hooks 机制
- 通用 JS 语言基础
- 构建工具底层原理

典型题：

- `watch` 与 `watchEffect` 的区别
- 响应式依赖收集的基本思路
- 组合式 API 的适用场景

### `frontend_engineering`

包含：

- Vite / Webpack
- Babel / SWC
- Tree Shaking
- 分包与构建优化
- 包管理与发布流程

不包含：

- 运行时渲染机制
- 业务状态管理
- 页面级性能分析结果本身

典型题：

- Vite 为什么冷启动更快
- Tree Shaking 为什么会失效
- 动态导入与代码分割

### `app_architecture`

包含：

- 状态管理
- 组件边界设计
- 模块拆分
- 数据流设计
- 页面级和应用级架构取舍

不包含：

- 单纯的框架 API 语法题
- 构建工具链实现细节
- 单纯的 HTTP / 安全协议问题

典型题：

- 全局状态和局部状态如何拆
- 页面组件如何分层
- 前端模块边界如何设计

### `web_network_security`

包含：

- HTTP / HTTPS / WebSocket
- 缓存协商
- 跨域
- 鉴权
- XSS / CSRF / CSP
- Cookie / SameSite

不包含：

- 浏览器渲染性能问题
- 资源加载优化指标分析
- 构建分包实现细节

典型题：

- XSS 和 CSRF 的区别
- Cookie、LocalStorage、Token 的取舍
- CORS 预检请求为什么会发生

### `web_performance`

包含：

- Core Web Vitals
- 首屏优化
- 长任务与卡顿定位
- 渲染性能优化
- 资源加载优化
- 性能监控与排障

不包含：

- 安全协议本身
- 构建工具链底层实现
- 浏览器原理的纯机制题

典型题：

- LCP 慢怎么定位
- 长任务如何发现
- 首屏白屏优化思路

## 三组容易混淆的边界

### 1. `ui_foundation` vs `browser_runtime`

- `ui_foundation`：
  页面结构设计、语义化、无障碍、CSS 布局
- `browser_runtime`：
  DOM API、事件传播、渲染流程、回流重绘

固定规则：

- `DOM structure design` -> `ui_foundation`
- `DOM runtime model` -> `browser_runtime`

### 2. `js_ts_core` vs `react/vue`

- `js_ts_core`：
  与框架无关的语言、类型系统、异步模型
- `react/vue`：
  框架自身组件模型、响应式或渲染机制

固定规则：

- 脱离框架仍成立的题，优先归 `js_ts_core`
- 明显依赖框架心智模型的题，归对应框架域

### 3. `web_network_security` vs `web_performance`

- `web_network_security`：
  协议、安全、跨域、鉴权
- `web_performance`：
  指标、加载速度、卡顿、首屏体验

固定规则：

- 讨论“安全正确性”的题归 `web_network_security`
- 讨论“速度与体验”的题归 `web_performance`

## 旧域到新域映射规则

- `js_core` -> `js_ts_core`
- `browser` -> `browser_runtime`
- `css_layout` -> `ui_foundation`
- `network` -> `web_network_security`
- `performance` -> `web_performance`

`vue_react` 需要拆分：

- React 题 -> `react`
- Vue 题 -> `vue`
- 纯组件设计 / 状态管理 / 页面拆分题 -> `app_architecture`

## 人工复标优先级

以下旧题不应自动迁移，必须进入人工复标：

1. 同时出现 React 与 Vue 关键词的题
2. 同时涉及浏览器渲染与首屏优化的题
3. 同时涉及缓存策略与安全策略的题
4. 同时涉及 TypeScript 类型系统与框架上下文的题

## 结论

前端知识域的主目标是服务面试评估，而不是学习路径编排。

因此：

- 不采用 `HTML / CSS / JS` 一级拆分
- 保留 `ui_foundation`、`browser_runtime`、`js_ts_core` 这组三角底座
- 用框架、工程化、架构、安全、性能域补齐高级前端能力
