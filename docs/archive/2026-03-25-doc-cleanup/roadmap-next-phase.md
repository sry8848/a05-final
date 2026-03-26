# AI 模拟面试系统 — 后续开发路线图（综合版）

> 参考依据：`当前实现与面试流程策略差异与修改指南.md`、`AI增强能力技术选型方案与开发建议.md`、`development-plan.md`
>
> **制定原则**：先稳核心链路、再接 AI 增强能力、最后做高体验功能；每个里程碑可独立验收、可独立回滚。

---

## 一、当前状态速览

### 已完成能力

| 模块 | 状态 |
|------|------|
| 登录 / 注册 / 鉴权 | ✅ 完成 |
| 创建面试会话 + 考纲异步生成（Planner） | ✅ 完成 |
| 状态账本初始化（StateLedgerInitService） | ✅ 完成 |
| 首题生成（FirstQuestionGenerationService） | ✅ 完成 |
| 逐题主链路（AnswerSubmitService，含幂等、评估、账本 patch、下一题） | ✅ 完成 |
| 面试结束 + 异步报告生成（ReportGenerationService） | ✅ 完成 |
| Spring AI 迁移（ChatClient + BeanOutputConverter） | ✅ 完成 |
| SSE 端点（伪流式：已落库题目打字机推送） | ✅ 完成（待升级） |
| AI 调用审计日志（AiInvocationLog） | ✅ 完成 |

### 仍需推进的重点（按优先级）

| 优先级 | 问题 | 影响 |
|--------|------|------|
| **P0** | RAG 检索未真正接入（stub 空实现） | 出题无知识库增强，深挖质量受限 |
| **P0** | SSE 仍为"伪流式"而非模型实时 token 流 | 无法感知模型实时生成，体验有上限 |
| **P0** | MVP 前端页面（练习页等）与后端未联调 | 用户无法完整跑通全链路 |
| **P1** | 账本状态与关系表状态双轨表达 | 维护成本高，存在数据不一致风险 |
| **P1** | Prompt/DTO 契约缺少自动化测试 | Prompt 变更后容易静默回归 |
| **P2** | 语音 ASR / TTS / AI 面试官形象 | 体验提升，不影响核心链路 |
| **P2** | 学习资源推送 | 面试结果的后续增值服务 |

---

## 二、整体分层架构（目标状态）

```
┌──────────────────────────────────────────────────┐
│              前端（Vue / React）                    │
│  练习页 SSE文本流  语音输入(ASR)  AI形象 TTS播报    │
└───────────────────┬──────────────────────────────┘
                    │ SSE / HTTP / WebSocket
┌───────────────────▼──────────────────────────────┐
│           Java 后端（核心编排层）                   │
│  面试主链路  RAG检索编排  多模型路由  审计日志        │
│  报告生成   学习资源推荐  语音最终稿入库             │
└──────┬──────────┬─────────────┬──────────────────┘
       │          │             │
  ┌────▼───┐ ┌────▼────┐ ┌─────▼─────┐
  │ MySQL  │ │ Qdrant  │ │ Redis(缓存)│
  │ 业务数据│ │ 向量检索 │ │ 分布式锁   │
  └────────┘ └─────────┘ └───────────┘
                    │
       ┌────────────┴────────────┐
  ┌────▼────┐              ┌─────▼────┐
  │ LLM API │              │ ASR/TTS  │
  │ 多模型路由│              │ 云厂商   │
  └─────────┘              └──────────┘
```

---

## 三、路线图总览

```
Phase 1（核心 AI 链路强化，约 1 周）
  ├── M1：RAG 真接入（Qdrant）
  ├── M2：SSE 真实模型流式出题
  └── M3：状态统一 + 契约测试

Phase 2（MVP 全链路联调，约 2 周）
  ├── Sprint A：设备检测 + 简历管理
  ├── Sprint B：开始面试页 + 创建会话 + 加载页
  ├── Sprint C：面试练习页（完整主流程）
  └── Sprint D：报告页 + 问答详情 + 历史 + 成长中心

Phase 3（AI 增强体验，约 3 周）
  ├── M4：语音 ASR 接入 + 停顿打标
  ├── M5：TTS 语音播报 + AI 面试官形象
  └── M6：学习资源推送闭环
```

---

## 四、Phase 1：核心 AI 链路强化（约 1 周）

### M1：RAG 真接入（3～4 天）

> **目标**：出题和追问真正引用知识库，彻底消除 stub 空实现。
>
> **向量库选型**：推荐 `Qdrant`（Docker 单容器），保留 MySQL 不动。
> 不推荐 RedisVectorStore（附加功能，技术印象分低）；不推荐 pgvector（需迁移数据库）。

#### T1.1 基础设施接入（0.5 天）

**任务**：
- 启动 Qdrant：`docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant`
- `pom.xml` 新增 `spring-ai-qdrant-store-spring-boot-starter`
- `application.yml` 新增 Qdrant 配置：

```yaml
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        collection-name: interview_knowledge
        initialize-schema: true
```

- 增加配置开关：`rag.enabled`、`rag.top-k`、`rag.min-score`
- 约定文档 metadata 字段：`knowledge_type`、`domain_code`、`question_type`、`difficulty`、`source`、`position_code`、`version`

**DoD**：Qdrant Dashboard 可访问（`http://localhost:6333/dashboard`），Spring 启动不报错。

---

#### T1.2 知识入库服务（0.5 天）

**任务**：
- 新增 `KnowledgeIngestionService`：负责文档切片、向量化、metadata 标注、入库
- 优先建设两类知识库：
  1. `岗位知识库`：知识域说明、考察点、评分锚点
  2. `面试题知识库`：高质量题目、追问模板、常见误区
- 为每份知识文档打 metadata 标签（`domain_code`、`difficulty`、`source`）
- 提供 `POST /api/v1/admin/knowledge/ingest`（内部接口，用于批量入库）

**DoD**：至少 2 个知识域有样本数据入库，Qdrant Dashboard 可查看。

---

#### T1.3 RAG 检索服务实现（1 天）

**任务**：
- 新增 `RagRetrievalService`（接口 + 实现）：

```java
/**
 * 输入：nextDomainCode + focusPoint + difficultyHint + questionType
 * 输出：ragContext（正向证据 + 反证提示，供注入出题 Prompt）
 */
RagContext retrieve(RagRetrievalRequest request);
```

- 实现多路召回：按 `domainCode`、`questionType`、`difficultyHint`、`focusPoint` 多维度编译查询和排序
- 检索结果按 score 降序，聚合成可注入 Prompt 的字符串
- 检索异常必须降级（`log.error(..., e)` 记录完整堆栈，返回空 context，不阻断主链路）
- `rag.enabled=false` 时直接跳过，返回空 context

**DoD**：单元测试覆盖"命中"、"无命中"、"检索异常降级"三个路径。

---

#### T1.4 主链路集成与审计（1 天）

**任务**：
- `AnswerSubmitService` Step6 正式调用 `RagRetrievalService`（替换现有空实现）
- 将检索命中结果写入 `ai_invocation_logs.retrieval_context_json`
- `QuestionGenerationInput.ragContext` 传入检索结果（已有字段，现在真正赋值）
- 验证 `buildQuestionGenUserPrompt` 中 `isBlank()` 降级分支正常工作

**DoD**：
- `ai_invocation_logs.retrieval_context_json` 有非空记录
- 检索异常时出题链路不受影响
- `rag.enabled=false` 时行为与之前完全一致（向后兼容）

---

#### T1.5 端到端验收（0.5 天）

**验收用例**：
- [ ] 创建会话 → 多轮答题 → 日志中有 RAG 命中记录
- [ ] 手动清空 Qdrant 集合 → 检索无命中 → 出题正常（降级路径）
- [ ] 暂停 Qdrant 进程 → 检索异常 → 出题正常 + error 日志有完整堆栈

---

### M2：SSE 真实模型流式出题（2～3 天）

> **目标**：从"伪流式打字机"升级为"模型实时生成 token 流"，前端感知到 AI 正在实时思考出题。

#### T2.1 服务层改造（1 天）

**任务**：
- `InterviewService` 新增 `streamNextQuestion(Long sessionId, Long questionId)`：
  1. 读取会话、账本、历史题，组装 `QuestionGenerationInput`
  2. 调用 `RagRetrievalService` 填充 `ragContext`（复用 M1 成果）
  3. 调用 `aiClient.callQuestionGenerationStream(input)` 返回 `Flux<String>`
  4. 流式传输完成后，将题目最终版本落库（`InterviewQuestionMapper.insert`）
  5. 分配 `generationId` 用于重连幂等
- **注意**：先流式传输，完成后再落库；不能"先落库后流式"（否则退化回旧行为）

**DoD**：本地 curl 测 SSE 端点，可看到 token 逐字返回，延迟低于 500ms 首字。

---

#### T2.2 升级结构化流事件（0.5 天）

**任务**：
- 将当前单纯 `Flux<String>` 升级为结构化事件：

```
event: start
data: {"generationId":"xxx","sessionId":1}

event: delta
data: {"text":"请描述你"}

event: delta
data: {"text":"在项目中"}

event: done
data: {"generationId":"xxx","questionId":42,"totalTokens":85}

event: error
data: {"code":"AI_TIMEOUT","message":"生成超时"}
```

- `InterviewController` SSE 端点参数明确：`sessionId` + `attemptId`（避免歧义）
- 约定端点：`GET /api/v1/interviews/{sessionId}/questions/stream?attemptId=xxx`

**DoD**：前端 EventSource 可正确解析 `start/delta/done/error` 四类事件。

---

#### T2.3 断线重连与幂等（0.5～1 天）

**任务**：
- 同一 `attemptId` 只生成一题，防止重复生成（基于 `generationId` 缓存）
- 支持前端断线重连：客户端携带 `Last-Event-ID` 时，从 Redis 缓存返回已生成片段
- 明确生命周期：流式生成中 → 生成完成落库 → SSE 连接关闭

**DoD**：
- [ ] 同一 `attemptId` 重复连接 SSE 端点，第二次返回已生成内容而非重新生成
- [ ] 手动断开客户端连接后重连，可从断点位置续传

---

### M3：状态与契约治理（2 天）

> **目标**：消除双轨状态维护成本，建立 Prompt 变更的自动化防护网。

#### T3.1 状态语义统一（1 天）

**现状问题**：
- 账本 JSON：`UNASKED / IN_PROGRESS / COVERED`
- `session_skill_states` 表：`uncovered / in_progress / covered / circuit_broken`

**任务**：
- 定义统一枚举 `DomainStatus`（四值：`UNASKED/IN_PROGRESS/COVERED/CIRCUIT_BROKEN`）
- 统一 `StateLedgerPatchService` 与 `SessionSkillStateMapper` 的写入口径
- 提供映射函数确保两处状态永远同步
- 清理冗余的 String 常量，全局替换为枚举引用

**DoD**：
- [ ] 一次账本 patch 后，`state_ledger_json` 与 `session_skill_states` 表状态值一致
- [ ] 代码中不再存在硬编码字符串状态值

---

#### T3.2 Prompt/DTO 契约测试（1 天）

**任务**：
为以下四个核心 AI 调用添加最小契约测试（JUnit + Mockito）：

1. **Planner 契约**：验证 `domains` 非空、`questionMixPlan` 有效、`projectAnchors` 关键字段完整
2. **QuestionGeneration 契约**：验证 `stem` 非空、`domainCode` 匹配、`questionType` 合法
3. **EvaluationDecision 契约**：验证 `nextStrategy` 非空、`ledgerPatch` 可应用
4. **Report 契约**：验证 `overallScore` 在范围内、`domainScores` 覆盖所有知识域

每个契约测试覆盖：
- ✅ 正常输出 → 解析成功
- ❌ 缺少必填字段 → 兜底默认值 + warning 日志
- ❌ 字段类型漂移 → 异常捕获 + 降级逻辑触发

**DoD**：4 类契约测试全部通过，覆盖率 100% 对应分支。

---

## 五、Phase 2：MVP 全链路联调（约 2 周）

> 对应 `development-plan.md` 中的阶段一至阶段十一，以下按 Sprint 整合。

### Sprint A：设备检测 + 简历管理（3～4 天）

#### 后端

| 任务 | 接口 | 说明 |
|------|------|------|
| 系统 Ping | `GET /api/v1/system/ping` | 返回 serverTime，用于前端测网络延迟 |
| 简历表迁移确认 | - | 确保 `resumes` 表字段与 db-schema §4.3 一致 |
| 简历列表 | `GET /api/v1/resumes` | 含 id/name/sourceType/parseStatus/isDefault/createdAt |
| 简历上传 | `POST /api/v1/resumes/upload` | 接收文件，落库，触发异步解析（PDF/DOCX） |
| 解析状态轮询 | `GET /api/v1/resumes/{id}/parse-status` | 返回 parseStatus + parsedText |
| 简历 CRUD | `GET/PUT/DELETE /api/v1/resumes/{id}` | 详情、编辑、删除 |
| 设为默认 | `POST /api/v1/resumes/{id}/set-default` | 同用户其他简历自动取消默认 |

#### 前端

| 任务 | 路由 | 说明 |
|------|------|------|
| 面试测试页 | `/interview-test?sessionId=xxx` | 摄像头、麦克风波形、扬声器试听、Ping 展示、检测结果汇总 |
| 简历管理页 | `/resumes` | 上传、解析状态展示、编辑、设默认、删除 |

**验收标准**：
- [ ] 面试测试页显示摄像头、麦克风波形、Ping 数值
- [ ] 可上传 PDF/DOCX，解析后文本可编辑并保存
- [ ] 可管理多份简历，设置默认，删除生效

---

### Sprint B：开始面试页 + 创建会话 + 面试加载页（3 天）

#### 后端

| 任务 | 接口 | 说明 |
|------|------|------|
| 岗位与知识域种子数据 | - | 至少 2 个岗位、6～10 个知识域 |
| 岗位列表 | `GET /api/v1/positions` | 岗位枚举 |
| 知识域列表 | `GET /api/v1/positions/{code}/skill-domains` | 某岗位知识域 |
| 创建会话 | `POST /api/v1/interviews` | 校验参数，写入 planning 状态，异步触发 Planner |
| 会话详情 | `GET /api/v1/interviews/{id}` | 轮询获取 status + 首题 questionId |

#### 前端

| 任务 | 路由 | 说明 |
|------|------|------|
| 开始面试页 | `/interviews/new` | 选岗位/年限/模式/JD/简历/侧重，「下一步」创建会话 |
| 面试加载页 | `/interviews/:sessionId/loading` | 轮询 status，planning→in_progress 后跳转练习页 |

**验收标准**：
- [ ] 填完表单点「下一步」→ 跳转面试测试页（URL 带 sessionId）
- [ ] 设备检测通过 → 进入加载页，轮询到 in_progress 后自动跳转

---

### Sprint C：面试练习页（完整主流程，4～5 天）

#### 后端

| 任务 | 接口 | 说明 |
|------|------|------|
| 题目与答案表确认 | - | `interview_questions`、`interview_attempts` 就绪 |
| 提交回答 + 生成下一题 | `POST /api/v1/interviews/{id}/attempts` | 已实现，联调验证 |
| 跳过题目 | `POST /api/v1/interviews/{id}/questions/{qid}/skip` | 标记 skipped，生成下一题 |
| 获取提示 | `POST /api/v1/interviews/{id}/hint` | 返回面试官提示文本 |
| 结束面试 | `POST /api/v1/interviews/{id}/finish` | 已实现，联调验证 |
| SSE 真实流式出题 | `GET /api/v1/interviews/{id}/questions/stream` | Phase 1 M2 成果接入前端 |

#### 前端

| 任务 | 路由 | 说明 |
|------|------|------|
| 面试练习页 | `/interviews/:sessionId` | 当前题 + 历史问答 + 输入区 + 操作按钮 |
| 题目 SSE 接收 | - | 接入真实 SSE 流，展示 delta 事件逐字显示效果 |
| 练习模式 | - | 文本输入 + 提交并继续/跳过/获取提示/结束面试 |
| 断点恢复 | - | 刷新或重进时调 `GET /api/v1/interviews/{id}` 恢复当前状态 |
| 面试 Store | - | 管理 currentQuestion/messageList/loadingState |

**验收标准**：
- [ ] 同一 `attempt_id` 重复提交，不生成重复下一题（幂等验证）
- [ ] 题型配额随每题提交递增
- [ ] SSE 展示 token 逐字出现效果（真实模型流）
- [ ] 支持跳过、提示、结束面试
- [ ] 刷新页面后能恢复当前面试状态

---

### Sprint D：报告与后续页面（4～5 天）

#### 后端

| 任务 | 接口 | 说明 |
|------|------|------|
| 报告生成 | - | 已实现，联调验证结构化字段 |
| 面试报告 | `GET /api/v1/interviews/{id}/report` | 总分、知识域得分、总结、优劣势、建议、题目列表 |
| 单题详情 | `GET /api/v1/interviews/{id}/questions/{qid}` | 题干、评估、批注、黄金骨架、参考重构 |
| AI 追问 | `POST /api/v1/interviews/{id}/questions/{qid}/ai-consult` | 多轮对话，流式或非流式 |
| 历史面试列表 | `GET /api/v1/interviews` | 分页 + 筛选（岗位、日期、状态） |
| 问答库 CRUD | `GET/POST/DELETE /api/v1/question-bank` | 收藏、列表、删除 |
| 成长中心 | `GET /api/v1/profile`、`GET /api/v1/profile/statistics`、`GET /api/v1/profile/skill-overview` | 个人资料 + 统计 + 知识域能力 |

#### 前端

| 页面 | 路由 | 主要功能 |
|------|------|----------|
| 面试反馈页 | `/interviews/:sessionId/report` | 总分、雷达图、总结、优劣势、题目列表 |
| 问答详情页 | `/interviews/:sessionId/questions/:questionId` | 批注展示、黄金骨架、AI 追问、收藏 |
| 历史面试页 | `/history` | 列表 + 筛选排序 + 查看报告入口 |
| 成长问答库 | `/question-bank` | 收藏题目列表 + 重做跳转 |
| 成长中心 | `/profile` | 欢迎语 + 能力图 + 统计趋势 |
| 个人设置 | `/settings` | 头像、昵称、密码 |

**验收标准**：
- [ ] 结束面试后进入报告页，数据与后端一致
- [ ] 可向 AI 追问并看到回复
- [ ] 收藏题目后在问答库可见
- [ ] 历史面试筛选排序生效
- [ ] 成长中心展示知识域条形图和统计数据

---

## 六、Phase 3：AI 增强体验（约 3 周，可与 Phase 2 并行推进）

### M4：语音 ASR 接入（4～5 天）

> **目标**：用户可以说话答题，系统能实时识别并提交最终稿；停顿信息辅助 AI 评估表达流畅度。

#### T4.1 前端 ASR 接入（2 天）

**技术路线**：前端直连云厂商实时 ASR（WebSocket），不走后端中转。

**任务**：
- 封装 `AsrService`（前端）：初始化、开始/停止录音、处理中间帧/最终帧
- 实现实时字幕展示（中间帧结果）
- `is_final=true` 时提交最终转写文本
- 实现停顿打标（按 `questionType` 分档阈值）：

| questionType | 停顿阈值 |
|---|---|
| INTRO | 2000ms |
| PRINCIPLE | 2500ms |
| SCENARIO | 3000ms |
| PROJECT_DEEP_DIVE | 2000ms |
| BEHAVIORAL | 2500ms |

- 打标格式：在最终文本中插入 `[停顿 Xs]` 标签
- 计算并上报 `pauseStats`（`wpm`、`longPauseCount`、`longestPauseMs`）

#### T4.2 阈值配置接口（0.5 天）

**任务**：
- 新增 `GET /api/v1/config/asr-pause-thresholds`：按 `questionType` 返回停顿阈值 JSON
- 前端启动时拉取并缓存，不硬编码
- 新增配置项 `speech.asr.pause-threshold-config`

#### T4.3 后端接收语音数据（1 天）

**任务**：
- `SubmitAttemptRequest` 新增字段：`asrSegments`、`pauseStats`（含 wpm/longPauseCount/longestPauseMs）、`audioUrl`
- `answerText` 改用停顿打标后的富文本
- 评估 Prompt 中新增表达流畅度维度（基于停顿标签）
- 报告中新增基础语速指标展示

**DoD**：
- [ ] 专业模式下用户说话，实时字幕出现
- [ ] 答题提交后 `answerText` 包含 `[停顿 Xs]` 标签
- [ ] 报告中有"表达节奏观察"字段

---

### M5：TTS 语音播报 + AI 面试官形象（4～5 天）

> **目标**：题目不仅显示，还能"说出来"；面试官形象能响应声音动起来。

#### T5.1 一期整段 TTS（2 天）

**技术路线**：云厂商 TTS，题目生成完成后异步触发整段合成。

**任务**：
- 后端新增 `TtsService`：接收题目文本，调用云厂商 TTS，返回音频 URL 或临时文件
- SSE `done` 事件中携带 `ttsReady: true/false`
- 新增音频获取接口：`GET /api/v1/interviews/{sessionId}/questions/{questionId}/audio`
- 音频缓存：相同题目文本短期缓存（Redis），避免重复合成
- 新增配置项 `speech.tts.enabled`

- 前端新增音频播放器组件：
  - 支持"自动播报 / 手动播放 / 静音模式"三档
  - 提供 skip（跳过播报）和 interrupt（打断）控制

#### T5.2 AI 面试官形象 V1（1～2 天）

**技术路线**：音量驱动波纹/光圈动效（最轻量，零额外依赖，复用麦克风测试组件）。

**任务**：
- 复用设备测试页的 Web Audio API 分析节点
- TTS 播放时，分贝值驱动圆形波纹/光圈扩散动画（`speaking` 状态）
- 无音频时静止（`idle` 状态）
- 定义 `avatarAction` 事件，V1 只用 `speaking/idle` 两个状态
- 即使 TTS 时间轴解析失败，音量动效仍正常工作（天然降级）

**DoD**：
- [ ] 题目生成后 1～2 秒内开始播报
- [ ] 播报时 AI 面试官形象有动效
- [ ] 用户可选择关闭自动播报
- [ ] TTS 服务异常时，面试主链路不受影响

---

### M6：学习资源推送闭环（5～7 天）

> **目标**：面试结果可以自然转化为个性化学习建议，形成"面试→评估→差距→学习"闭环。

#### T6.1 学习资源数据层（1.5 天）

**任务**：
- 新增 `learning_resources` 表（`resource_id`、`title`、`resource_type`、`domain_code`、`focus_point`、`difficulty`、`estimated_minutes`、`source`、`quality_score`、`tags`）
- 新增 `LearningResourceIngestionService`：资源入库、向量化（复用 Qdrant）
- 至少准备 20～30 条种子资源（覆盖 2～3 个知识域）
- 建立 metadata 规范，确保标签可控

#### T6.2 学习资源检索服务（1 天）

**任务**：
- 新增 `LearningResourceRetrievalService`：复用 Qdrant 向量检索底座
- 基于薄弱点标签做精确过滤 + 向量召回
- 按资源质量、难度匹配、与目标岗位相关度排序
- 结果去重，避免重复推荐

#### T6.3 推荐编排服务（1.5 天）

**任务**：
- 新增 `LearningRecommendationService`：
  - 输入：面试报告（薄弱点标签 + domainCode + 用户目标岗位）
  - 输出三层推荐结构：
    - `立即学习`（与本次薄弱点直接相关，10～30 分钟）
    - `重点补齐`（按知识域，1～2 周）
    - `长期进阶`（与目标岗位升级相关）
  - 每条推荐附带可解释理由（"因为你在 X 知识域得分较低..."）
- 报告生成后**异步触发**学习推荐，不阻塞主报告链路
- 推荐结果单独缓存（Redis，TTL 24h）

#### T6.4 接口与前端展示（2 天）

**任务**：
- 新增 `GET /api/v1/interviews/{sessionId}/report/learning-recommendations`
- 报告页每个薄弱知识域后展示 2～3 条高价值资源
- 新增成长页"本周优先学习"区块
- 复盘页展示"本题为什么答得不够好 → 建议补什么"

**DoD**：
- [ ] 面试报告页有学习推荐区块，内容与薄弱点相关
- [ ] 推荐理由可解释
- [ ] 推荐结果已缓存，重复打开不重新计算

---

## 七、共性基础设施（贯穿全程）

以下能力需在各里程碑推进中持续补强：

### 7.1 多模型路由（M1 完成后推进）

**任务**：
- 新增 `ModelRoutingService`：根据 `promptCode` 选择模型、控制温度/超时/重试
- 分层模型策略：
  - **主决策模型**：Planner / EvaluationDecision / ReportGeneration（推理稳定、JSON 输出稳定）
  - **流式出题模型**：QuestionGeneration / 追问（首字延迟低、成本可控）
  - **轻量辅助模型**：query rewrite / 标签提取（便宜快）
- 配置项：`ai.routing.question-generation.model`、`ai.routing.evaluation.model`
- `promptCode → model strategy` 支持热更新

### 7.2 必须补齐的共性能力

| 能力 | 说明 |
|------|------|
| 超时控制 | 每个 AI 调用设置合理超时（Planner 30s，出题 15s，评估 20s） |
| 重试与降级 | 瞬时失败重试 1 次，超时降级（mock 结果或跳过本步） |
| 成本统计 | `ai_invocation_logs` 汇总每日 token 消耗和费用估算 |
| Prompt 版本管理 | 每个 Prompt 带 `promptVersion` 字段，支持 A/B 测试 |
| 数据脱敏 | 日志中简历文本、用户答题内容打码，遵守合规要求 |

---

## 八、全量 Definition of Done（DoD）

每个里程碑交付时，须满足以下通用标准：

| 维度 | 标准 |
|------|------|
| **功能** | 接口可调用，主流程稳定，异常可降级，幂等可验证 |
| **日志** | 入口/出口 `log.info` 记录关键参数，异常 `log.error(..., e)` 包含完整堆栈 |
| **数据** | 关键字段入库可回放（attempt/report/invocation_log） |
| **测试** | 覆盖成功路径、幂等路径、异常降级路径 |
| **接口文档** | Controller `@Tag/@Operation`，DTO `@Schema` 同步更新 |
| **端到端** | 每个里程碑结束后做一次完整回归：创建会话→多轮答题→结束→查看报告 |

---

## 九、建议执行节奏（8 周计划）

| 周次 | 主要目标 | 里程碑 |
|------|---------|--------|
| **Week 1** | RAG 真接入，消除 stub | Phase 1 M1 |
| **Week 2** | SSE 真实流式 + 状态/契约治理 | Phase 1 M2 + M3 |
| **Week 3** | 设备检测 + 简历管理 + 开始面试页 | Phase 2 Sprint A + B |
| **Week 4** | 面试练习页完整主流程联调 | Phase 2 Sprint C |
| **Week 5** | 报告 + 历史 + 成长中心 + 全链路验收 | Phase 2 Sprint D |
| **Week 6** | 语音 ASR 接入 + 停顿打标 | Phase 3 M4 |
| **Week 7** | TTS 整段播报 + AI 面试官形象 V1 | Phase 3 M5 |
| **Week 8** | 学习资源推送闭环 | Phase 3 M6 |

---

## 十、可立即开始的第一批子任务

> 下一个工作日即可开始，无需等待其他模块：

1. `feat(rag)` 启动 Qdrant Docker + Spring AI QdrantVectorStore 配置 + 配置开关
2. `feat(rag)` 新增 `KnowledgeIngestionService`，建设岗位知识库和评分锚点知识库
3. `feat(rag)` 新增 `RagRetrievalService`，实现多路召回 + 降级逻辑
4. `feat(rag)` `AnswerSubmitService` Step6 正式接入 RAG，`retrieval_context_json` 入库
5. `feat(sse)` 改造 `InterviewService.streamNextQuestion()`，接 `aiClient.callQuestionGenerationStream()`
6. `feat(sse)` 升级 SSE 事件协议为结构化事件（start/delta/done/error）
7. `refactor(state)` 定义 `DomainStatus` 枚举，统一账本与关系表写入口径
8. `test(ai-contract)` 为 Planner/Question/Eval/Report 增加最小契约测试

---

## 十一、不建议当前阶段做的事情

- ❌ 不建议用 Redis 做向量库（继续只做缓存/锁）
- ❌ 不建议首期直接上 Milvus（过重，得不偿失）
- ❌ 不建议首期自研 ASR/TTS（用云厂商）
- ❌ 不建议首期做 Live2D 或 3D 数字人（先验证音量动效价值）
- ❌ 不建议把音频处理逻辑塞进 Java 主服务（独立 Python 服务后置）
- ❌ 不建议 TTS 音频通过 SSE Base64 内嵌（独立 HTTP 接口按需拉取）
- ❌ 不建议大而全重构，优先小步快跑，每个里程碑独立可验收
