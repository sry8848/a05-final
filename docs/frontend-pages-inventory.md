# 前端页面清单与修正建议顺序

> 基于对 `frontend/src` 的扫描，对照 `docs/page-list.md`、`docs/development-plan.md`、`docs/auth-pages-plan.md` 整理。  
> **当前前端无 Vue Router**，所有页面通过 `App.vue` 的状态（`currentPage`、`showXXX` 等）切换组件。

---

## 1. 当前前端结构概览

### 1.1 导航与入口

| 状态条件 | 显示的组件 | 说明 |
|----------|------------|------|
| 未登录且未点注册/管理端 | `LoginPage` | 登录页 |
| 未登录且点击「管理端登录」 | `AdminLoginPage` | 管理端登录 |
| 未登录且点击「去注册」 | `RegisterPage` | 注册页 |
| 已登录且为管理员 | `AdminLayout` | 管理后台整体布局 |
| 已登录且非管理员 | 主布局（Sidebar + 主内容区） | 见下表 |

### 1.2 主布局侧栏（Sidebar）与主内容

| `currentPage` | 组件 | 侧栏名称 |
|---------------|------|----------|
| `home` | `HomePage` | 首页概览 |
| `interview` | `InterviewPage` | 开始面试 |
| `history` | `HistoryPage` | 面试记录 |
| `growth` | `GrowthCenterPage` | 成长中心 |
| `analysis` | `AnalysisPage` | 数据分析 |
| `settings` | `SettingsPage` | 个人设置 |

### 1.3 由状态触发的 overlay/全屏页

| 状态 | 组件 | 触发方式 |
|------|------|----------|
| `showResultPage` | `InterviewResultPage` | 面试结束后 |
| `showQuestionBank` | `QuestionBankPage` | 侧栏或某处「问答库」 |
| `showRadarPage` | `RadarChartPage` | 成长中心内「雷达图」 |
| `showScoreTrendPage` | `ScoreTrendPage` | 成长中心内「分数趋势」 |
| `showInterviewDetail` | `InterviewDetailPage` | 历史/报告内「查看详情」 |
| `isInterviewRunning` | `InterviewPage`（fullscreen） | 点击开始面试并进入面试中 |

### 1.4 管理端（AdminLayout 内）

| `currentPage` | 组件 |
|---------------|------|
| `dashboard` | `AdminDashboard` |
| `prompt` | `PromptLab` |
| `model` | `ModelRouting` |
| `rag` | `RagManagement` |
| `monitor` | `SystemMonitor` |
| `analysis` | `DataAnalysis` |

### 1.5 涉及文件一览

```
frontend/src/
├── App.vue                    # 根：状态与组件切换
├── main.js
├── utils/
│   └── interview.js
└── components/
    ├── LoginPage.vue
    ├── RegisterPage.vue
    ├── AdminLoginPage.vue
    ├── AdminLayout.vue
    ├── AdminDashboard.vue
    ├── PromptLab.vue
    ├── ModelRouting.vue
    ├── RagManagement.vue
    ├── SystemMonitor.vue
    ├── DataAnalysis.vue
    ├── Sidebar.vue
    ├── HomePage.vue
    ├── InterviewPage.vue
    ├── HistoryPage.vue
    ├── GrowthCenterPage.vue
    ├── AnalysisPage.vue
    ├── SettingsPage.vue
    ├── InterviewResultPage.vue
    ├── QuestionBankPage.vue
    ├── RadarChartPage.vue
    ├── ScoreTrendPage.vue
    ├── InterviewDetailPage.vue
    ├── CustomSelect.vue
    └── ...
```

---

## 2. 与文档的页面映射及差异

| 文档页面（page-list.md） | 建议路由 | 当前对应组件/入口 | 差异说明 |
|--------------------------|----------|-------------------|----------|
| 登录页 | `/login` | `LoginPage` | 需对照 auth-pages-plan：登录方式（邮箱+密码 / 邮箱+验证码）、成功后跳转**成长中心** |
| 注册页 | `/register` | `RegisterPage` | 需对照：字段（email、code、nickname、password、confirmPassword）+ 注册验证码发送，成功后跳转**登录页** |
| 成长中心页 | `/profile` | `GrowthCenterPage` + 部分 `HomePage` | 文档规定**登录后默认进入成长中心**；当前默认是 `home`（HomePage）。需统一：要么默认进 growth，要么把 home 合并为成长中心 |
| 个人设置页 | `/settings` | `SettingsPage` | 与成长中心隔离，可从成长中心或导航进入。功能对齐：头像、昵称、邮箱等 |
| 面试测试页 | `/interview-test?sessionId=xxx` | **缺失** | 文档：在「开始面试页」点击下一步**创建会话**后进入，带 sessionId；含摄像头/麦克风/网络检测，再「进入面试」→ 加载页。当前流程可能在 InterviewPage 内一步到位，需拆出或新增 |
| 简历管理页 | `/resumes` | **无独立页** | 当前仅在 InterviewPage 的「面试准备」里有简历选择/上传。文档要求独立「简历管理页」：列表、上传、解析状态、编辑识别文本、设默认、删除 |
| 开始面试页 | `/interviews/new` | `InterviewPage` 未运行时的配置区 | 文档：岗位、JD、工作年限、模式、侧重知识点、简历选择；**下一步** = 创建会话并跳转面试测试页（带 sessionId）。当前字段/流程需对齐（如去掉公司名、薪资、面试轮次等与文档不符项） |
| 面试加载页 | `/interviews/:sessionId/loading` | **缺失** | 文档：考纲生成中、加载动画，轮询到 in_progress 后跳练习页；第一题返回策略待 D4 决策后定稿。当前可能无此步 |
| 面试练习页 | `/interviews/:sessionId` | `InterviewPage`（fullscreen） | 文档：当前题、提交并继续/跳过/提示/结束等。逻辑与接口需对齐 |
| 面试反馈页 | `/interviews/:sessionId/report` | `InterviewResultPage` | 文档：总分、知识域雷达图、总结、优势/薄弱点/建议、题目列表。功能与字段对齐 |
| 问答详情页 | `/interviews/:sessionId/questions/:questionId` | `InterviewDetailPage` | 文档：题目原文、批注、得分、黄金骨架、参考重构、AI 追问、收藏。功能对齐 |
| 历史面试页 | `/history` | `HistoryPage` | 文档：列表、筛选、排序、查看报告、再做一场。功能对齐 |
| 成长问答库页 | `/question-bank` | `QuestionBankPage` | 文档：题目列表、筛选排序、删除、重做（跳转准备页带参数）。功能对齐 |
| 欢迎页 | `/`（P1 非 MVP） | 无 | 可选保留或后续再做 |
| 管理端仪表盘 | `/admin/dashboard` | `AdminDashboard` | P2，可保留样式，功能后做 |
| Prompt 实验室 | `/admin/prompts` | `PromptLab` | P2，可保留 |
| RAG 语料管理 | `/admin/rag` | `RagManagement` | P2，可保留 |
| 模型路由与成本 / 系统监控 / 管理端数据分析 | — | `ModelRouting`、`SystemMonitor`、`DataAnalysis` | 文档未单独列，可保留为管理端扩展 |

**与文档相悖或需收敛：**

- **首页概览（HomePage）**：文档无「首页概览」独立页，登录后默认应为**成长中心**。建议二选一：① 将默认页改为成长中心（`growth`），HomePage 改为欢迎/快捷入口或删除；② 将 HomePage 与 GrowthCenterPage 合并为「成长中心」一页，默认即 home。
- **用户端「数据分析」（AnalysisPage）**：文档无对应用户端数据分析页。建议：删除侧栏该项或改为文档中已有的其他入口（如暂无则隐藏）。
- **面试准备字段**：文档无「公司名称、薪资范围、面试轮次」等，需按 page-list §2.5 调整为岗位、JD、工作年限、模式、侧重知识点、简历选择等。

---

## 3. 建议修正顺序

按「登录与入口 → 个人与成长 → 简历 → 面试流程 → 复盘与历史 → 管理端」顺序，**只改功能、不改样式**，逐页收敛。

| 序号 | 页面 | 对应组件/改动点 | 说明 |
|------|------|------------------|------|
| 1 | 登录页 | `LoginPage` | 登录方式（邮箱+密码/邮箱+验证码）、成功后跳转成长中心；与 auth-pages-plan、api-design 对齐 |
| 2 | 注册页 | `RegisterPage` | 字段与校验、成功后跳转登录页 |
| 3 | 成长中心 + 默认入口 | `GrowthCenterPage`、`App.vue`、`Sidebar` | 登录后默认进入成长中心（currentPage 默认 `growth` 或合并 home/growth）；成长中心内容与 page-list §2.12 对齐 |
| 4 | 个人设置页 | `SettingsPage` | 与成长中心隔离，头像/昵称/邮箱等；入口从成长中心或侧栏 |
| 5 | 简历管理页 | 新增或从 InterviewPage 拆出 | 独立路由/状态对应「简历管理」；列表、上传、解析状态、编辑、默认、删除（后端未开发可先 mock/占位） |
| 6 | 开始面试页 | `InterviewPage` 配置区 或 独立组件 | 表单字段与文档一致；「下一步」= 创建会话并跳转面试测试页（带 sessionId）；侧栏可保留「开始面试」入口 |
| 7 | 面试测试页 | 新增组件 + App 状态/路由 | 带 sessionId 进入；设备检测；「进入面试」→ 加载页 |
| 8 | 面试加载页 | 新增组件 | 轮询会话状态，进入练习页 |
| 9 | 面试练习页 | `InterviewPage`（fullscreen） | 提交并继续、跳过、提示、结束；接口与文档对齐 |
| 10 | 面试反馈页 | `InterviewResultPage` | 总分、雷达图、总结、题目列表等与文档对齐 |
| 11 | 问答详情页 | `InterviewDetailPage` | 批注、黄金骨架、AI 追问、收藏等 |
| 12 | 历史面试页 | `HistoryPage` | 列表、筛选、排序、查看报告、再做一场 |
| 13 | 成长问答库页 | `QuestionBankPage` | 列表、删除、重做（带参数跳转准备页） |
| 14 | 侧栏与入口收敛 | `Sidebar`、`App.vue` | 去掉或重命名「首页概览」、处理「数据分析」；必要时引入 Vue Router 或保持状态 + 约定「逻辑路由」与文档一致 |

**管理端**：仪表盘、Prompt 实验室、RAG、模型路由、监控、数据分析等可保留现状，后端未开发则仅做展示/占位，不纳入本批「与文档对齐」的必改项。

---

## 4. 路由与入口的后续建议

- 当前无 URL 路由，刷新或分享链接无法定位到具体页面。若需与文档中的「建议路由」一致并支持深链接，后续可引入 **Vue Router**，在保持现有组件和样式的前提下，将上述「逻辑页面」映射到对应 path，并在 `App.vue` 中改为 `<router-view />` + 按路由设置 `currentPage`/show 状态。  
- 在未接入 Router 前，仍可按上表顺序**仅改功能与状态**（例如登录成功设 `currentPage = 'growth'`、面试流程用状态切换面试测试/加载/练习组件），待整体流程对齐后再统一加路由。

---

以上为 frontend 页面清单与建议的修正顺序，可从第 1 项「登录页」开始逐项修改。
