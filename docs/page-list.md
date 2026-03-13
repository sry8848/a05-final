# AI 模拟面试系统页面清单

## 1. 页面分组

### 1.1 MVP 页面

| 页面 | 建议路由 | 优先级 | 页面目标 |
| --- | --- | --- | --- |
| 登录页 | `/login` | P0 | 完成身份认证（成功后进入成长中心） |
| 注册页 | `/register` | P0 | 完成邮箱注册 |
| 成长中心页 | `/profile` | P0 | 登录后默认进入；含欢迎语；查看成长数据 |
| 个人设置页 | `/settings` | P0 | 与成长中心隔离；头像、昵称、邮箱等资料编辑 |
| 面试测试页 | `/interview-test` | P0 | 验证设备和网络（从开始面试页「下一步」创建会话后进入，带 sessionId） |
| 简历管理页 | `/resumes` | P0 | 管理多份简历资产 |
| 开始面试页 | `/interviews/new` | P0 | 填写面试上下文，点击「下一步」创建会话并跳转面试测试页 |
| 面试加载页 | `/interviews/:sessionId/loading` | P0 | 展示考纲生成进度（从面试测试页「进入面试」后进入） |
| 面试练习页 | `/interviews/:sessionId` | P0 | 进行逐题动态问答 |
| 面试反馈页 | `/interviews/:sessionId/report` | P0 | 查看整场面试总结报告 |
| 问答详情页 | `/interviews/:sessionId/questions/:questionId` | P0 | 单题复盘与 AI 追问 |
| 历史面试页 | `/history` | P0 | 浏览历次记录 |
| 成长问答库页 | `/question-bank` | P0 | 管理收藏题目并二次练习 |

### 1.2 非 MVP 页面

| 页面 | 建议路由 | 优先级 | 说明 |
| --- | --- | --- | --- |
| 欢迎页 | `/` | P1 | 营销展示和引导注册 |
| 管理端仪表盘 | `/admin/dashboard` | P2 | 运营后台 |
| Prompt 实验室 | `/admin/prompts` | P2 | Prompt 配置和调试 |
| RAG 语料管理 | `/admin/rag` | P2 | 语料上传与向量化管理 |

## 2. MVP 页面详情

### 2.1 登录页

- 核心模块：
  - 登录方式切换 Tab（邮箱 + 密码 / 邮箱 + 验证码）。
  - 邮箱输入框。
  - 密码输入框（密码登录）。
  - 验证码输入框（验证码登录）。
  - 发送验证码按钮。
  - 登录按钮。
  - 跳转注册链接。
- 页面状态：默认态 / 提交中 / 失败提示。
- 关键字段：`email`、`password`、`verificationCode`、`loginType`。
- 主要动作：发送邮箱验证码、提交登录、跳转注册页。**登录成功后跳转成长中心（`/profile`）。**

### 2.2 注册页

- 核心模块：邮箱输入框、验证码输入框、发送验证码按钮、昵称输入框、注册按钮、返回登录链接。
- 关键字段：`email`、`code`、`nickname`、`password`、`confirmPassword`。
- 主要动作：提交注册（成功后跳转登录页）。

### 2.3 面试测试页

- 页面目标：在进入面试前验证设备状态，避免面试中断。
- 核心模块：
  - 摄像头预览区（实时画面）。
  - 麦克风音量动态波形（实时采集，可视化声音强度）。
  - 扬声器测试（播放测试音频，用户确认能否听到）。
  - 网络延迟显示（实时 Ping，毫秒级显示）。
  - 检测结果汇总（各项通过/警告/失败状态）。
  - **进入面试按钮**（通过后跳转 `/interviews/:sessionId/loading`）。
- **流程说明**：本页在**开始面试页**用户点击「下一步」并成功创建会话后进入，URL 带 `sessionId`（如 `/interview-test?sessionId=xxx`）。
- MVP 说明：
  - 摄像头/麦克风访问使用浏览器 API，无需后端。
  - 网络延迟通过 Ping `GET /api/v1/system/ping` 接口测量。
  - 用户即使部分检测未通过，也可强行继续（给出警告提示）。
- 关键字段：`cameraStatus`、`micStatus`、`speakerStatus`、`networkLatencyMs`。
- 主要动作：开始检测、重新检测、**进入面试**（跳转面试加载页）。

### 2.4 简历管理页

- 页面目标：管理用于面试的简历资产，支持多份简历长期保存。
- 核心模块：
  - 简历列表（名称、创建时间、默认标记）。
  - 上传简历区（拖拽或点击，支持 PDF/DOCX）。
  - 解析状态展示（解析中 / 解析完成 / 解析失败）。
  - 识别文本编辑区（用户可修改 AI 识别结果后保存）。
  - 设置/取消默认简历 。
  - 删除简历。
- MVP 说明：
  - 文件上传后后端异步解析，前端轮询解析状态。
  - 用户编辑并保存识别文本后，该文本作为面试上下文使用。
  - 不提供 AI 给出修改建议的交互界面，只提供文本编辑。
- 关键字段：`id`、`name`、`sourceType`、`parseStatus`、`parsedText`、`isDefault`。
- 主要动作：上传简历、保存编辑、设为默认、删除。

### 2.5 面试准备页

- 页面目标：收集本次面试上下文并创建面试会话。
- 核心模块：
  - 简历选择（从已保存简历中选择）。
  - 岗位选择。
  - JD 文本输入区。
  - 工作年限选择（决定面试难度）。
  - 面试模式选择（练习模式 / 专业模式，附各自说明）。
  - 侧重知识点输入区（仅练习模式可选）。
  - 压迫感选择
  - 音色选择
  - **下一步按钮**（点击后创建会话并跳转面试测试页，带 sessionId）。
- **流程说明**：用户填写完成后点击「下一步」调用 `POST /api/v1/interviews` 创建会话，成功后跳转 `/interview-test?sessionId=xxx`，在面试测试页完成设备检测后再进入面试加载页。
- 路由参数预留（碎片化练习跳转）：
  - `auto_focus`：自动预填侧重知识点。
  - `auto_mode`：自动选择练习模式。
  - `auto_target_role`：自动选择岗位枚举。
- 关键字段：`targetRole`、`experienceLevel`、`mode`、`jobDescription`、`resumeId`、`focusTopics`、`thinkTimeLimitSeconds`（专业模式，系统自己配置）、`answerTimeLimitSeconds`（专业模式，系统自己配置）。
- 主要动作：保存表单、**点击下一步创建会话并跳转面试测试页**。

### 2.6 面试加载页

- 页面目标：承接创建面试后的等待过程，展示考纲生成进度。
- 核心模块：加载动画、展示 Tips。
- 关键展示：面试官正在准备中...
- 主要动作：自动跳转至面试练习页。

### 2.7 面试练习页

- 页面目标：承载完整的动态问答流程（练习模式和专业模式共用此页面）。
- 页面布局建议：
  - 顶部：面试标题、当前题号（显示"第 N 题"，不显示总数）、累计耗时、模式标识。
  - 左侧：控制面板。
  - 中间：对话区和输入区（练习模式=文本/语音输入；专业模式=语音录入），类似微信聊天框形式
  - 右侧：AI 面试官静态形象卡片 + 用户摄像头画面。
- 核心模块：
  - 当前问题。
  - 文本输入框（仅练习模式）。
  - 语音输入区（专业模式必选，显示实时识别文字）。
  - 专业模式倒计时（思考时间 / 回答时间，颜色变化提示剩余时间）。
  - 提交并继续按钮（主操作）。
  - 跳过本题按钮。
  - 获取提示按钮。
  - 切换输入方式按钮（仅练习模式可用）。
  - 结束面试按钮。
- 关键字段：`sessionId`、`mode`、`currentQuestion`、`messages`、`currentAnswer`、`elapsedSeconds`、`thinkTimeLimitSeconds`、`answerTimeLimitSeconds`。
- 主要动作：
  - 提交并继续（触发：保存答案 + 按固定窗口组装上下文 + AI 评估并决策 + 更新状态账本 + 生成下一题）。
  - 跳过本题（触发：标记跳过 + 生成下一题）。
  - 请求提示。
  - 切换输入方式。
  - 结束面试。
- 非 MVP 扩展位：
  - 实时状态仪表盘（语速、流畅度）。
  - 算法题代码编辑器。
  - AI 动态形象驱动。
  - 智能监考事件上报。

### 2.8 面试反馈页

- 页面目标：展示整场面试的综合分析结果。
- 核心模块：
  - 总分卡片（综合得分）。
  - 知识域条形图（所有模式展示，用于技术成长分析）。
  - 综合能力雷达图（专业模式展示，反映沟通、逻辑、表达等综合维度）。
  - 面试官总结评语（先肯定后建议）。
  - 优势总结列表。
  - 薄弱点与提升建议列表。
  - 知识盲区靶向推送学习资源（推荐重点复习的知识域）。
  - 题目列表（仅展示问题，点击进入问答详情页）。
- 非 MVP 扩展位：
  - 历史平均残影雷达图（需 P1 实现）。
  - 综合得分进度条增长动效（需 P1 实现）。
- 关键字段：`overallScore`、`skillDomainScores`、`comprehensiveRadarScores`、`summary`、`strengths`、`weaknesses`、`improvementSuggestions`、`recommendedTopics`。
- 主要动作：查看单题详情、返回历史记录、再来一场。

### 2.9 问答详情页

- 页面目标：承载单题深度复盘，支持 AI 追问。
- 核心模块：
  - 题目原文。
  - 考察知识域标签和题型标签。
  - 用户回答原文（含颜色批注：绿色=亮点，红色=薄弱点）。
  - 单题得分和知识域评分明细。
  - 薄弱点说明列表。
  - 黄金答题骨架（推荐的回答结构）。
  - 参考满分重构（AI 重写的优质回答示例）。
  - 询问 AI 区域（用户可就本题向 AI 发起对话）。
  - 收藏到成长问答库按钮。
  - 回到当时那场面试入口。
- 关键字段：`questionStem`、`domainName`、`questionType`、`targetDepth`、`userAnswer`、`highlightedSegments`、`score`、`commentary`、`strengthPoints`、`weakPoints`、`evaluatedDomains`、`idealAnswerOutline`、`rewrittenAnswer`。
- 主要动作：向 AI 追问、收藏到问答库、返回整场报告。

### 2.10 历史面试页

- 页面目标：支持用户查找和进入既往面试记录。
- 核心模块：
  - 历史记录列表（卡片式，含岗位、日期、得分、题数、状态）。
  - 筛选栏（按岗位、日期范围、状态）。
  - 排序栏（按时间、得分）。
  - 报告查看入口。
  - 相似面试快速发起按钮。
- 列表字段：`sessionId`、`title`、`targetRole`、`mode`、`overallScore`、`questionCount`、`status`、`createdAt`。
- 主要动作：查看报告、再做一场相似面试。

### 2.11 成长问答库页

- 页面目标：管理收藏的面试题目，支持检索和二次练习。
- 核心模块：
  - 题目列表卡片（题目内容摘要、所属知识域、得分）。
  - 筛选栏（按时间、知识域标签、得分区间）。
  - 排序栏（按时间、分数）。
  - 删除按钮（从收藏中移除）。
  - 重做按钮（跳转至面试准备页，预填该题的知识域和侧重点）。
- 关键字段：`id`、`questionStem`、`domainName`、`score`、`sessionId`、`tag`、`createdAt`。
- 主要动作：删除题目、重做题目（触发碎片化练习跳转）、筛选排序。

### 2.12 成长中心页

- 页面目标：**登录后默认进入本页**；展示欢迎语与个人成长数据、技术档案；与个人设置页隔离。
- **欢迎语**：进入系统时在成长中心顶部/首屏展示欢迎语（可复用现有前端的欢迎语设计与样式，参考 `frontend-isolation/`）。
- 核心模块：
  - **欢迎语区**（首屏）。
  - 基础信息区（头像、昵称、邮箱展示；**编辑入口跳转个人设置页** `/settings`）。
  - 能力多维雷达图。
  - 知识域条形图（按岗位筛选，展示各知识域历史累积得分，支持选择知识域去练习）。
  - 统计数据区（累计模拟次数、总时长、平均分）。
  - 平均分趋势折线图（按时间轴展示历史分数变化，按综合能力和多维雷达图各维度筛选）。
- 非 MVP 扩展位：
  - 技术能力红黑榜 Top3（原始构想保留，但本阶段仅保留数据基础与接口扩展位，展示层 P1 实现）。
  - 各维度成长细节折线图（P1）。
- 关键字段：`nickname`、`avatarUrl`、`totalSessions`、`totalMinutes`、`averageScore`、`skillDomainScores`（按岗位）。
- 主要动作：进入个人设置、按岗位切换知识域条形图、切换平均分趋势折线图、点击薄弱知识域发起靶向练习。

### 2.13 个人设置页

- 页面目标：与成长中心隔离的独立页面，承载账号与资料设置。
- 核心模块：
  - 头像上传。
  - 昵称、邮箱编辑。
  - 密码修改（若产品支持）。
  - 可从成长中心或主导航进入。
- 主要接口：复用 `GET /api/v1/profile`、`PUT /api/v1/profile`、`POST /api/v1/profile/avatar` 等。
- 主要动作：保存资料、返回成长中心或上一页。

## 4. 页面与接口映射

| 页面 | 主要接口 |
| --- | --- |
| 登录页 | `POST /api/v1/auth/login/password` `POST /api/v1/auth/login/email-code` `POST /api/v1/auth/email-code/send`（手机验证码接口为非 MVP 预留） |
| 注册页 | `POST /api/v1/auth/email-code/send`（`scene=register`） `POST /api/v1/auth/register` |
| 面试测试页 | `GET /api/v1/system/ping` |
| 简历管理页 | `GET /api/v1/resumes` `POST /api/v1/resumes/upload` `GET /api/v1/resumes/{id}/parse-status` `GET /api/v1/resumes/{id}` `PUT /api/v1/resumes/{id}` `POST /api/v1/resumes/{id}/set-default` `DELETE /api/v1/resumes/{id}` |
| 面试准备页 | `POST /api/v1/interviews` `GET /api/v1/positions` `GET /api/v1/positions/{code}/skill-domains` |
| 面试加载页 | `GET /api/v1/interviews/{id}`（轮询；第一题返回策略待 D4 决策后定稿） |
| 面试练习页 | `POST /api/v1/interviews/{id}/submit-and-next` `POST /api/v1/interviews/{id}/questions/{qid}/skip-and-next` `POST /api/v1/interviews/{id}/hint` `POST /api/v1/interviews/{id}/finish` |
| 面试反馈页 | `GET /api/v1/interviews/{id}/report` |
| 问答详情页 | `GET /api/v1/interviews/{id}/questions/{qid}` `POST /api/v1/interviews/{id}/questions/{qid}/ai-consult` |
| 历史面试页 | `GET /api/v1/interviews` |
| 成长问答库页 | `GET /api/v1/question-bank` `POST /api/v1/question-bank` `DELETE /api/v1/question-bank/{id}` |
| 成长中心页 | `GET /api/v1/profile` `GET /api/v1/profile/statistics` `GET /api/v1/profile/skill-overview`（资料编辑在个人设置页） |
| 个人设置页 | `GET /api/v1/profile` `PUT /api/v1/profile` `POST /api/v1/profile/avatar` |

## 5. 前端模块拆分建议

- `layouts`：登录布局、主应用布局（侧边栏/顶部导航）。
- `views`：页面级组件，每个 MVP 页面一个 view 文件；成长中心（含欢迎语）与个人设置页可放在 `views/profile` 下。
- `components/interview`：题目卡片、答题输入框（文本+语音）、控制面板、倒计时、实时评估反馈条。
- `components/report`：总分卡片、知识域条形图、建议列表、颜色批注渲染。
- `components/resume`：简历卡片、识别文本编辑器。
- `components/device-test`：摄像头预览、麦克风波形、网络延迟显示。
- `stores`：认证、面试会话（含当前题目和动态出题状态）、历史记录、用户档案、问答库。
- `apis`：按模块拆分，每个模块一个文件（auth、interview、report、resume、profile、questionBank、position、system）。
- `types`：用户、会话、题目、报告、知识域、简历等类型定义。
- `utils`：路由参数预填解析（处理 `auto_focus`、`auto_target_role` 等碎片化练习参数）、Web Speech API 封装。
