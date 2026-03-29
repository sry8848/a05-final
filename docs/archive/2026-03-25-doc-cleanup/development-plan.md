# AI 模拟面试系统 — 后续开发计划

> 基于 `product-scope.md` 与 `page-list.md` 制定。**登录/注册功能已完成**，本文档从下一阶段起规划。

## 1. 当前进度与目标

### 1.1 已完成

- **账户与访问**：邮箱注册、邮箱验证码登录、登录态保持与退出（对应登录页、注册页及鉴权中间件）。

### 1.2 登录与入口约定

- **登录/注册成功后**：统一跳转到 **成长中心**（`/profile`），不进入其他首页。
- **成长中心页**：进入系统时展示 **欢迎语**（复用现有前端的欢迎语设计，在成长中心顶部或首屏呈现）。

### 1.3 个人设置与成长中心隔离

- **成长中心页**（`/profile`）：展示成长数据、知识域能力、统计与趋势图；含欢迎语；**不**承载账号与安全等个人设置。
- **个人设置页**（`/settings`）：与成长中心隔离，单独页面，承载头像、昵称、邮箱、密码等资料编辑及账号相关设置；可从成长中心或导航进入。

### 1.4 MVP 剩余能力概览

按**用户主流程**顺序，剩余能力为：

1. **开始面试** → 开始面试页（选岗位、JD、简历等）→ 点击「下一步」**创建会话** → 面试测试页（设备检测，带 sessionId）  
2. **面试执行** → 面试加载页 + 面试练习页（考纲生成 + 逐题动态问答）  
3. **简历管理** → 简历管理页（独立入口）  
4. **结果与复盘** → 面试反馈页 + 问答详情页  
5. **历史与成长** → 历史面试页 + 成长问答库页 + 成长中心页 + 个人设置页  

以下按**阶段**拆解，每阶段包含后端、前端、联调与验收标准，便于排期与迭代。

---

## 2. 前端隔离区与复用策略

### 2.1 隔离区位置与用途

- **目录**：仓库根目录下的 **`frontend-isolation/`**，供你将现有前端项目导入此处。
- **用途**：作为**视觉与 UI 的参考源**，不在此区直接改功能或对接后端；在 `frontend/` 中开发时从此处复用布局、样式、欢迎语、组件结构等。
- **原则**：功能与接口以 `docs/api-design.md`、`docs/page-list.md`、`docs/product-scope.md` 为准；前端已实现的可扩展功能，后端可先做简单实现，后续再扩展。

### 2.2 使用方式

1. 将现有前端项目拷贝或链接到 `frontend-isolation/` 下。  
2. 在 `frontend/` 中新增或修改页面时，对照 `frontend-isolation/` 中对应页面进行复用或改写，保持视觉风格一致。  
3. 功能与接口不一致处，仅在 `frontend/` 中调整，不反向修改 `frontend-isolation/`，避免与上游脱节。

---

## 3. 阶段一：设备检测 + 系统基础设施

**目标**：用户进入面试前可自检设备与网络；本页在**开始面试页点击「下一步」创建会话之后**进入，带 sessionId。

### 3.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 实现 `GET /api/v1/system/ping` | 返回 `serverTime`，前端用往返时间算延迟 | api-design §4.1 |
| 鉴权 | 该接口可不要求登录，或允许未登录访问（按产品约定） | - |

### 3.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 路由与布局 | 新增 `/interview-test`，支持查询参数 `sessionId`（从开始面试页「下一步」跳转时带入） | page-list §2.3 |
| 面试测试页 | 摄像头预览、麦克风波形、扬声器测试、网络 Ping 展示、检测结果汇总、进入面试按钮（通过后跳转 `/interviews/:sessionId/loading`） | page-list §2.3 |
| 工具封装 | 封装 `getUserMedia`、AudioContext 波形、Ping 轮询/单次请求 | project-structure §4 |

### 3.3 验收标准

- [ ] 从开始面试页「下一步」创建会话后，跳转到面试测试页且 URL 带 sessionId  
- [ ] 面试测试页可看到摄像头画面、麦克风音量波形、扬声器试听、实时 Ping 显示  
- [ ] 用户点击「进入面试」后跳转到面试加载页（`/interviews/:sessionId/loading`）

---

## 4. 阶段二：简历管理

**目标**：用户可上传、解析、编辑、保存多份简历，并设置默认简历。

### 4.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 简历表与迁移 | 确保 `resumes` 表存在且字段符合 db-schema §4.3 | db-schema |
| `GET /api/v1/resumes` | 列表，含 `id,name,sourceType,parseStatus,isDefault,createdAt` | api-design §5.1 |
| `POST /api/v1/resumes/upload` | 接收文件，落库，触发异步解析（或同步解析 MVP） | api-design §5.2 |
| `GET /api/v1/resumes/{id}/parse-status` | 轮询解析状态 | api-design |
| `GET /api/v1/resumes/{id}` | 详情，含 `parsedText` 等 | api-design |
| `PUT /api/v1/resumes/{id}` | 更新名称、`parsed_text`（用户编辑后保存） | api-design |
| `POST /api/v1/resumes/{id}/set-default` | 设为默认，同用户其余简历取消默认 | api-design |
| `DELETE /api/v1/resumes/{id}` | 删除简历 | api-design |
| 文件解析 | PDF/DOCX 解析（本地库或第三方服务），解析结果写入 `raw_text`/`parsed_text`，状态更新 | product-scope §2.2 |

### 4.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 路由 | `/resumes` 简历管理页 | page-list §2.4 |
| 简历列表 | 卡片/列表展示名称、创建时间、默认标记 | page-list §2.4 |
| 上传区 | 拖拽或点击上传 PDF/DOCX，上传后展示解析中/解析完成/失败 | page-list §2.4 |
| 解析状态 | 轮询 `parse-status`，完成后展示可编辑文本 | page-list §2.4 |
| 编辑与保存 | 识别结果可编辑，保存调用 `PUT /resumes/{id}` | page-list §2.4 |
| 默认/删除 | 设置默认、删除简历 | page-list §2.4 |
| API 与类型 | `apis/resume.ts`、简历相关类型定义 | page-list §5 |

### 4.3 验收标准

- [ ] 可上传简历文件并看到解析状态  
- [ ] 解析完成后可编辑文本并保存  
- [ ] 可管理多份简历、设置默认、删除  

---

## 5. 阶段三：岗位与面试准备（创建会话）

**目标**：用户选择岗位、年限、模式、JD、简历等，点击「下一步」时**创建会话**，并跳转面试测试页（带 sessionId）；设备检测通过后再进入加载页。

### 5.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 岗位与知识域数据 | `position_skill_domains` 表及种子数据（2～3 个岗位，6～10 个知识域） | db-schema §4.4, product-scope §8.1 |
| `GET /api/v1/positions` | 岗位列表 | api-design §6 |
| `GET /api/v1/positions/{code}/skill-domains` | 某岗位知识域列表（准备页可选侧重时用） | api-design |
| 会话与偏好表 | `interview_sessions`、`interview_preferences` 表就绪 | db-schema §4.5, §4.6 |
| `POST /api/v1/interviews` | 创建会话：校验 `positionCode`、`experienceLevel`、`mode`、JD、简历等，写入会话并初始化状态为 `planning`；异步触发考纲生成 | api-design §7 |
| 考纲生成（Planner） | 在面试创建或加载页轮询时：调用 AI Planner 生成主考纲，写入 `syllabus_json`，初始化 `state_ledger_json`；主考纲需包含题型配额、知识域目标深度、项目锚点 | product-scope §4, §7.3 |

### 5.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 路由 | `/interviews/new` 开始面试页 | page-list §2.5 |
| 表单 | 岗位枚举、工作年限分层枚举、面试模式（练习/专业）、JD 输入、简历选择、侧重知识点（练习模式可选） | page-list §2.5 |
| URL 预填 | 支持 `auto_focus`、`auto_mode`、`auto_position_code` 等查询参数预填 | page-list §2.5, §5 |
| 下一步 | 点击「下一步」调用 `POST /api/v1/interviews` 创建会话，成功后跳转 `/interview-test?sessionId=xxx`（面试测试页） | page-list §3 |
| API 与类型 | `apis/position.ts`、`apis/interview.ts`，会话/岗位类型定义 | page-list §5 |

### 5.3 验收标准

- [ ] 开始面试页可完整填写，点击「下一步」后创建会话成功并跳转面试测试页（URL 带 sessionId）  
- [ ] 面试测试页通过后，用户可进入面试加载页（见阶段四）  

---

## 6. 阶段四：面试加载页与考纲生成

**目标**：加载页展示「面试官准备中」，后端完成考纲与状态初始化，前端轮询到可开始状态后进入练习页。

### 6.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 考纲与状态初始化 | 在会话状态为 `planning` 时执行：Planner 生成主考纲 + 状态账本；状态账本作为唯一过程状态表达；第一题生成时机待 D4 决策后定稿 | product-scope §7.3 |
| `GET /api/v1/interviews/{id}` | 会话详情；当 `status=in_progress` 时返回可开始状态，首题返回方式待 D4 决策后定稿 | api-design §7, product-scope §7.3 |
| 轮询友好 | 若考纲生成较慢，建议异步任务 + 轮询，避免长时间阻塞 | - |

### 6.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 面试加载页 | 路由 `/interviews/:sessionId/loading`，加载动画与 Tips | page-list §2.6 |
| 轮询 | 轮询 `GET /api/v1/interviews/{id}`，当状态为 `in_progress` 时跳转 `/interviews/:sessionId`；首题获取方式待 D4 决策后定稿 | page-list §2.6 |
| 错误与超时 | 超时或失败提示，可重试或返回准备页 | - |

### 6.3 验收标准

- [ ] 从面试测试页「进入面试」后进入加载页，等待至考纲生成完成  
- [ ] 自动跳转到面试练习页；首题获取方式按 D4 最终决策实现  

---

## 7. 阶段五：面试练习页（核心执行链路）

**目标**：完整跑通「看题 → 作答 → 提交并继续 / 跳过 / 提示 → 结束面试」的闭环。

### 7.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 题目与答案表 | `interview_questions`、`interview_answers`、`session_skill_states` 已就绪 | db-schema §4.7–4.9 |
| `POST /api/v1/interviews/{id}/submit-and-next` | 保存答案 → 组装固定上下文窗口（最近 x 题完整 `Q/A`，历史题仅问题）→ AI 评估并决策 → 更新状态账本 → 生成下一题；返回上题评估 + 下一题；必要时返回 `interviewShouldEnd: true` | api-design §7, product-scope §7.3 |
| `POST /api/v1/interviews/{id}/questions/{qid}/skip-and-next` | 跳过本题，标记 skipped，生成下一题 | product-scope §7.3 |
| `POST /api/v1/interviews/{id}/hint` | 获取面试官提示，可写回题目 `hint_text` 或仅返回 | api-design |
| `POST /api/v1/interviews/{id}/finish` | 结束面试，触发报告生成（状态 `report_generating` → `completed`） | api-design |
| AI 模块 | 评估 Prompt、出题 Prompt、Planner Prompt 等；统一走 `ai` 模块，落库 `ai_invocation_logs` | product-scope §7.6, project-structure |
| 状态账本 | 根据主考纲与当前题目/评估结果更新 session 状态账本，记录知识域覆盖、题型进度、项目锚点与当前深度，供下一题生成使用 | product-scope §4 |
| 上下文窗口组装 | 实现固定读取规则：最近 `x` 题读取完整 `Q/A`，更早历史题仅读取问题文本 | 面试流程策略, api-design §7 |

### 7.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 面试练习页 | 路由 `/interviews/:sessionId`，布局：顶部标题/题号/耗时/模式；中间题目与对话区；右侧 AI 形象 + 用户摄像头（可选） | page-list §2.7 |
| 题目展示 | 当前问题、历史问答列表（类似聊天） | page-list §2.7 |
| 输入与操作 | 练习模式：文本输入 + 提交并继续、跳过、获取提示；专业模式：仅语音输入（Web Speech API）+ 思考/回答倒计时 | page-list §2.7, product-scope §7.2 |
| 结束面试 | 结束按钮调用 `finish`，跳转反馈页 | page-list §2.7 |
| 断点恢复 | 刷新或重进时根据 `GET /api/v1/interviews/{id}` 恢复当前题与历史 | product-scope §9 |
| Store | 面试会话 store（当前题、消息列表、loading 状态） | page-list §5 |
| 工具 | Web Speech API 封装（专业模式用） | project-structure §4 |

### 7.3 验收标准

- [ ] 练习模式：能连续多题「提交并继续」，看到评估与下一题  
- [ ] 支持跳过、提示、结束面试  
- [ ] 结束后面试状态变为已完成，可进入报告页  

---

## 8. 阶段六：面试反馈页与报告

**目标**：面试结束后展示总分、知识域雷达图、总结、薄弱点与建议、题目列表入口。

### 8.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 报告表 | `interview_reports` 已就绪 | db-schema §4.10 |
| 报告生成 | `finish` 时或异步：汇总本场题目与评估，生成总分、知识域得分、总结、优势/薄弱点/建议、推荐复习知识点；专业模式写入 `comprehensive_radar_scores` | product-scope §8.2 |
| `GET /api/v1/interviews/{id}/report` | 返回报告 JSON（总分、知识域、综合雷达图、总结、优势/薄弱点/建议、题目列表等） | api-design §8 |
| 用户档案更新 | 本场结束后按 product-scope §8.2 更新 `user_skill_profiles` | product-scope §2.2 |

### 8.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 面试反馈页 | 路由 `/interviews/:sessionId/report` | page-list §2.8 |
| 模块 | 总分卡片、知识域雷达图、综合能力雷达图（专业模式）、总结评语、优势/薄弱点/建议、题目列表（点击进问答详情） | page-list §2.8 |
| 操作 | 查看单题详情、返回历史、再来一场（跳转准备页） | page-list §2.8 |

### 8.3 验收标准

- [ ] 结束面试后进入报告页，数据与后端一致  
- [ ] 可点击题目进入问答详情页  

---

## 9. 阶段七：问答详情页与 AI 追问

**目标**：单题复盘、颜色批注、黄金骨架/参考重构、收藏、向 AI 追问。

### 9.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| `GET /api/v1/interviews/{id}/questions/{qid}` | 单题详情：题干、知识域、用户回答、批注、得分、点评、亮点/薄弱点、黄金骨架、参考重构等 | api-design §9 |
| `POST /api/v1/interviews/{id}/questions/{qid}/ai-consult` | 用户追问，多轮对话落库 `question_consult_messages`，流式或非流式返回 | api-design §9 |
| 收藏到问答库 | 若收藏在详情页完成，需提供 `POST /api/v1/question-bank`（见阶段九） | api-design §11 |

### 9.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 问答详情页 | 路由 `/interviews/:sessionId/questions/:questionId` | page-list §2.9 |
| 展示 | 题目原文、知识域/题型标签、用户回答（含颜色批注）、得分与点评、薄弱点、黄金骨架、参考满分重构 | page-list §2.9 |
| 交互 | 询问 AI 区域（输入 + 发送）、收藏到成长问答库、返回本场报告 | page-list §2.9 |
| 组件 | 颜色批注渲染组件（report 相关） | page-list §5 |

### 9.3 验收标准

- [ ] 从报告页进入详情页，内容完整  
- [ ] 可向 AI 追问并看到回复  
- [ ] 可收藏题目到问答库（依赖阶段九接口）  

---

## 10. 阶段八：历史面试页

**目标**：列表展示历史面试，支持筛选排序，进入报告或「再来一场」。

### 10.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| `GET /api/v1/interviews` | 分页列表，筛选（岗位、日期、状态）、排序（时间、得分）；返回 `sessionId, title, positionCode, mode, overallScore, questionCount, status, createdAt` 等 | api-design §7, page-list §2.10 |

### 10.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 历史面试页 | 路由 `/history` | page-list §2.10 |
| 列表 | 卡片展示岗位、日期、得分、题数、状态 | page-list §2.10 |
| 筛选与排序 | 按岗位、日期范围、状态筛选；按时间、得分排序 | page-list §2.10 |
| 操作 | 查看报告、再做一场相似面试（跳转准备页并可带参数） | page-list §2.10 |

### 10.3 验收标准

- [ ] 历史列表正确展示，筛选排序生效  
- [ ] 可进入某场报告页或准备页  

---

## 11. 阶段九：成长问答库

**目标**：收藏题目列表、删除、重做（跳转准备页并预填侧重）。

### 11.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| `question_bank_items` 表 | 已就绪 | db-schema §4.13 |
| `GET /api/v1/question-bank` | 列表，支持按时间/标签/分数筛选排序 | api-design §11 |
| `POST /api/v1/question-bank` | 收藏题目（传入 sessionId + questionId 等） | api-design §11 |
| `DELETE /api/v1/question-bank/{id}` | 取消收藏 | api-design §11 |

### 11.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 成长问答库页 | 路由 `/question-bank` | page-list §2.11 |
| 列表 | 题目卡片（摘要、知识域、得分）、筛选与排序 | page-list §2.11 |
| 操作 | 删除、重做（跳转 `/interviews/new?auto_focus=...` 等） | page-list §2.11, §3 |

### 11.3 验收标准

- [ ] 从详情页收藏的题目出现在问答库  
- [ ] 可删除、可重做并正确跳转准备页  

---

## 12. 阶段十：成长中心

**目标**：登录后默认进入本页；展示欢迎语、成长数据、知识域能力、统计与趋势；个人资料编辑入口可跳转个人设置页。

### 12.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| `GET /api/v1/profile` | 当前用户资料（昵称、头像、邮箱等） | api-design §12 |
| `PUT /api/v1/profile` | 更新昵称等 | api-design §12 |
| `POST /api/v1/profile/avatar` | 头像上传 | api-design §12 |
| `GET /api/v1/profile/statistics` | 累计场次、总时长、平均分等 | api-design §12 |
| `GET /api/v1/profile/skill-overview` | 按岗位的知识域条形图数据（来自 `user_skill_profiles`） | api-design §12 |

### 12.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 成长中心页 | 路由 `/profile`；**登录成功后默认进入此页** | page-list §2.12 |
| **欢迎语** | 进入系统时在成长中心顶部/首屏展示欢迎语（复用 `frontend-isolation/` 中现有前端的欢迎语设计与样式） | - |
| 基础信息 | 头像、昵称、邮箱展示；编辑入口跳转个人设置页（`/settings`）或弹窗 | page-list §2.12 |
| 能力展示 | 知识域条形图（按岗位筛选）、雷达图（若有） | page-list §2.12 |
| 统计与趋势 | 累计模拟次数、总时长、平均分；平均分趋势折线图 | page-list §2.12 |
| 操作 | 按知识域「去练习」跳转准备页并预填侧重；进入个人设置 | page-list §2.12 |

### 12.3 验收标准

- [ ] 登录后进入成长中心页，可见欢迎语  
- [ ] 知识域条形图与统计数据正确  
- [ ] 可从成长中心发起靶向练习，可进入个人设置页  

---

## 13. 阶段十一：个人设置页

**目标**：与成长中心隔离，单独承载账号与资料设置。

### 13.1 后端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 复用 profile 接口 | `GET /api/v1/profile`、`PUT /api/v1/profile`、`POST /api/v1/profile/avatar` 等，个人设置页与成长中心共用 | api-design §12 |

### 13.2 前端

| 任务 | 说明 | 参考 |
|-----|------|------|
| 个人设置页 | 路由 `/settings`，与成长中心隔离的独立页面 | - |
| 模块 | 头像上传、昵称/邮箱编辑、密码修改（若产品支持）等；可从成长中心或主导航进入 | - |

### 13.3 验收标准

- [ ] 可从成长中心或导航进入个人设置页  
- [ ] 资料编辑与头像上传正常  

---

## 14. 建议实施顺序与依赖

```
阶段一（设备检测）     → 无前置，可立即开始
阶段二（简历管理）     → 依赖鉴权
阶段三（准备页+创建）  → 依赖简历、岗位接口
阶段四（加载页）       → 依赖创建会话 + 考纲/首题生成
阶段五（练习页）       → 依赖加载页拿到首题 + 提交/跳过/提示/结束接口
阶段六（反馈页）       → 依赖 finish + 报告生成
阶段七（问答详情）     → 依赖报告与单题接口，可与阶段九并行
阶段八（历史面试）     → 依赖 interviews 列表接口
阶段九（问答库）       → 依赖 question-bank 接口，可与阶段七配合
阶段十（成长中心）   → 依赖 profile 与 skill-overview、statistics
阶段十一（个人设置） → 复用 profile 接口，可与阶段十一起交付
```

**推荐迭代方式**：

- **Sprint 1**：阶段一 + 阶段二（设备检测 + 简历管理），前后端一起交付。  
- **Sprint 2**：阶段三 + 阶段四（开始面试页 + 创建会话后跳面试测试页 + 加载页 + 考纲初始化），打通「下一步 → 设备检测 → 加载 → 进入可开始状态（首题策略待 D4）」。
- **Sprint 3**：阶段五（练习页主流程），实现提交并继续、跳过、提示、结束。  
- **Sprint 4**：阶段六 + 阶段七（报告页 + 问答详情 + AI 追问）。  
- **Sprint 5**：阶段八 + 阶段九 + 阶段十 + 阶段十一（历史、问答库、成长中心含欢迎语、个人设置）。  

专业模式（语音、倒计时、综合维度评分）可在阶段五之后单独排期，与练习模式共用同一套会话与报告结构。

---

## 15. 文档与规范

- **接口**：按 `api-design.md` 实现，Controller 使用 Swagger 3 注解（`@Tag`、`@Operation`、`@Schema`）。  
- **日志**：核心入口/出口 `log.info`，异常 `log.error(..., e)`，禁止 `System.out`。  
- **注释**：Class 与 public 方法 JavaDoc，复杂逻辑加业务意图注释。  
- **数据库**：与 `db-schema.md` 保持一致，AI 调用写入 `ai_invocation_logs`。  

---

## 16. 附录：页面与阶段对照

| 页面 | 路由 | 阶段 |
|-----|------|------|
| 登录页 / 注册页 | `/login`, `/register` | 已完成 |
| 成长中心页 | `/profile`（登录后默认进入，含欢迎语） | 阶段十 |
| 个人设置页 | `/settings` | 阶段十一 |
| 面试测试页 | `/interview-test?sessionId=xxx`（「下一步」后进入） | 阶段一 |
| 简历管理页 | `/resumes` | 阶段二 |
| 开始面试页 | `/interviews/new`（点击「下一步」创建会话并跳转面试测试页） | 阶段三 |
| 面试加载页 | `/interviews/:sessionId/loading` | 阶段四 |
| 面试练习页 | `/interviews/:sessionId` | 阶段五 |
| 面试反馈页 | `/interviews/:sessionId/report` | 阶段六 |
| 问答详情页 | `/interviews/:sessionId/questions/:questionId` | 阶段七 |
| 历史面试页 | `/history` | 阶段八 |
| 成长问答库页 | `/question-bank` | 阶段九 |

以上计划可直接用于任务拆解、排期与进度跟踪。若某阶段内接口或页面有增删，只需在对应小节与附录中同步调整即可。
