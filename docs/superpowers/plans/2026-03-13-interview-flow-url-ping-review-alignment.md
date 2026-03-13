# Interview Flow Ping-Review Alignment Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不重构单页主流程、保留 `attempts + SSE` 主机制的前提下，优先跑通前后端联调：完成设备检测真实 ping、复盘接口最小打通，并确保 `sessionId` 在现有单页流转里稳定透传。

**Architecture:** 本期暂缓 `vue-router` 与 URL 深链改造，保留 `App.vue + InterviewPage.vue` 的现有单页状态流转，避免为了路由同步扩大改动面。前端以 `backendSessionId` 和结果页/详情页的现有状态传递为联调主链路；后端补齐报告轻量题目列表和单题复盘核心字段。设备检测采用真实 `system/ping`，但测速策略改为“预热 + 并发 + 超时控制”，避免第一次连接建立成本和串行等待污染结果。

**Tech Stack:** Vue 3, Spring Boot 3, MyBatis-Plus, MySQL, Redis, SSE.

**Implementation Order:** 先完成 Chunk 1（现有单页流转下的 sessionId 透传与 ping），再做 Chunk 2（后端 report / question detail），然后做 Chunk 3（前端复盘接入）；路由与深链恢复降为后续工作，Chunk 4 文档与 checklist 后置，不阻塞主功能联调。

---

## Chunk 1: 暂缓路由改造，优先打通前端联调

### Task 1: 在现有单页流转中补齐 `sessionId` 透传

**Files:**
- Modify: `frontend/src/components/InterviewPage.vue`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/components/InterviewResultPage.vue`

- [ ] **Step 1: 明确本期状态流转边界**

保持当前单页流转：
- 不新增 `vue-router`
- 不改 `main.js`
- 不把 `sessionId` 设计为 URL 参数

本期联调以现有本地状态链路为准，而不是深链/刷新恢复。

- [ ] **Step 2: 固定 `backendSessionId` 为前端联调真值**

`createInterviewSession` 成功后，继续以 `InterviewPage.vue` 内的 `backendSessionId` 作为本场面试唯一会话标识，并确保从 loading -> interview -> finish 的整个流程不会丢失该值。

补充约束：
- 禁止分别维护多个独立 `sessionId` 状态源
- `resultData.sessionId`、单题详情请求参数等都必须从当前 `backendSessionId` 派生或复制
- 不允许详情页或结果页反向写回 `backendSessionId`

- [ ] **Step 3: 报告与单题详情入口始终带出 `sessionId`**

要求：
- `finishInterview` 生成的 `resultData` 必须始终包含 `sessionId`
- 从结果页进入单题详情时，优先从当前 `resultData.answers` / `resultData.report` 中读取 `questionId + sessionId`
- 若参数不足，则显式走本地降级，不尝试通过 URL 恢复上下文

- [ ] **Step 4: 手工验证现有单页主链路**

验证路径：
1. 创建会话后能拿到 `sessionId`
2. 完成面试后报告页能继续拉学习建议
3. 从报告页打开单题详情时具备调用后端详情接口所需参数

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/InterviewPage.vue frontend/src/App.vue frontend/src/components/InterviewResultPage.vue
git commit -m "feat(frontend): preserve session id through existing single-page interview flow"
```

### Task 2: 设备检测接入真实 ping，并采用预热 + 并发测速

**Files:**
- Modify: `frontend/src/api/resume.js` (or split to `api/system.js`)
- Modify: `frontend/src/components/InterviewPage.vue`

- [ ] **Step 1: 新增 ping API 调用**

添加 `getSystemPing()` -> `GET /api/v1/system/ping`（`cache: 'no-store'`）。

- [ ] **Step 2: 实现预热 + 并发测速策略**

在设备检测流程中用 `performance.now()` 计算 RTT，测速策略改为：
- 先发 1 次预热请求（Warm-up），不计入统计，用于摊平首次连接建立成本
- 然后使用 `Promise.allSettled` 并行发出 3 个 ping 请求
- 每个请求独立使用 `AbortController`，单次超时 `<= 1s`
- 过滤失败/超时样本后，对成功样本取中位数作为 `latencyMs`

补充说明：
- 当前前端 dev 环境通过 Vite `proxy` 转发 `/api`，不一定发生浏览器层面的真实跨域预检
- 但首次请求仍可能包含到后端的连接建立成本，所以预热仍然必要
- 总体网络检测预算控制在约 `<= 2s`

允许降级：
- 优先实现“warm-up + 3 次并发测速”
- 若实现复杂度或稳定性不理想，可退化为“warm-up + 2 次串行测速”，取稳定规则值（较小值或中位值）
- 不要求同时实现两套方案；选择一条可稳定落地的路径即可
- 无论采用哪种方案，都必须避免把明显随机抖动值直接展示给用户

- [ ] **Step 3: 应用阈值与 UI 状态**

阈值固定：
- `<120ms`: pass
- `120~300ms`: warning
- `>300ms`: fail（允许继续）

在检测汇总区新增网络项，显示 `latencyMs/status`。

- [ ] **Step 4: 异常降级**

规则：
- 3 个并行请求全部失败/超时 -> `warning`
- 仅有 1 个成功样本 -> 展示该样本值并标记 `warning`
- 至少 2 个成功样本 -> 使用聚合结果正常判级

提示文案：“网络检测失败或结果不稳定，可继续但建议检查网络”。

- [ ] **Step 5: 构建验证**

Run: `cd frontend && npm run build`
Expected: Build success，设备检测页可显示真实 ping 数值，且 warm-up + 并发测速不会造成明显等待。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/api/resume.js frontend/src/components/InterviewPage.vue
feat(frontend): use real system ping with warmed-up latency measurement
```

## Chunk 2: 报告 DTO 与单题详情接口（后端）

### Task 3: 扩展 `GET /interviews/{id}/report` 返回轻量题目列表

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewReportDto.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/InterviewReportService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/mapper/InterviewAttemptMapper.java`
- Modify: `backend/src/main/resources/mapper/interview/InterviewAttemptMapper.xml`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/service/InterviewReportServiceTest.java` (new or extend)

- [ ] **Step 1: 先写失败测试（报告包含题目摘要）**

覆盖断言：`report.questions` 非空时，每项至少包含：
- `questionId`
- `questionNo`
- `questionStem`
- `status`
- `score`（可为 `null`，仅在真实分存在时返回）

测试夹具至少覆盖：
- 1 题存在最终回答
- 1 题被跳过
- 1 题无回答记录

- [ ] **Step 2: 扩展 DTO 字段**

在 `InterviewReportDto` 增加 `List<QuestionSummaryDto> questions`，并把 `score` 定义为可空字段。

- [ ] **Step 3: 在 `InterviewReportService#getReport` 组装题目摘要**

数据来源：
- 题目基础信息：`interview_questions`
- 用户最终回答：`interview_attempts`（每题最终一次）

状态规则：
- answerText=`[skip]` -> `skipped`
- 有最终回答 -> `answered`
- 其他 -> `pending`

分数规则：
- 仅返回当前 attempt 评估对象中的显式分数字段，不做字符串数值的隐式解析和容错转换，避免把脏数据默认为真实分。
- 当前计划只认可 `attempt.evaluationJson` 内显式存在的 `score` 键
- 不得从 `signal`、状态码、文案或整场报告分反推单题分
- 若当前链路拿不到显式 `score`，则返回 `null`
- 不再用 `signal` 做临时分数映射，避免“业务造值”

- [ ] **Step 4: 完善 Mapper 查询能力**

新增按 `sessionId` 批量查询 attempts（供服务层聚合，不引入不必要复杂 SQL）。

- [ ] **Step 5: 运行后端测试**

Run: `cd backend && mvn -DskipTests=false test`
Expected: 新增测试通过，历史测试不回归。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewReportDto.java backend/src/main/java/com/a05/aiinterview/interview/service/InterviewReportService.java backend/src/main/java/com/a05/aiinterview/interview/mapper/InterviewAttemptMapper.java backend/src/main/resources/mapper/interview/InterviewAttemptMapper.xml backend/src/test/java/com/a05/aiinterview/interview/service/InterviewReportServiceTest.java
git commit -m "feat(backend): include lightweight question summaries in interview report dto"
```

### Task 4: 新增单题复盘核心接口 `GET /interviews/{sessionId}/questions/{questionId}`

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewQuestionReviewDto.java`
- Create: `backend/src/main/java/com/a05/aiinterview/interview/service/InterviewQuestionReviewService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/InterviewController.java`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/InterviewControllerQuestionDetailTest.java`

- [ ] **Step 1: 先写失败测试，并补齐多次作答夹具**

断言接口返回核心真实字段即可：
- `questionId`
- `questionNo`
- `questionStem`
- `questionType`
- `domainName`
- `targetDepth`
- `userAnswer`
- `answerStatus`
- `score`（可为 `null`）
- `commentary`（若现有链路可提供，否则允许为空）

测试数据初始化必须覆盖“一题多答”场景：
- 同一个 `questionId` 至少 3 条 attempt
- 至少 1 条 `isFinal=false` 的中间回答
- 至少 2 条 `isFinal=true` 的最终回答，且 `createdAt` 不同

断言：
- 接口忽略中间回答
- 在多条 final 中，选取 `createdAt` 最新的一条作为 `userAnswer` 与评估来源
- 若多条 final 的 `createdAt` 相同，则按主键 `id` 倒序取最后一条，保证结果稳定

- [ ] **Step 2: 实现 DTO 与服务聚合逻辑**

服务层优先使用已有真实数据源：
- `interview_questions`：题目主信息
- `interview_attempts`：用户最终回答、可用的评估结果
- 仅在确有必要时读取 `syllabus_json` 补充展示字段

attempt 选择规则与现有报告链路保持一致：
- 优先 `isFinal=true`
- 若存在多条 final，取 `createdAt` 最新的一条
- 若 `createdAt` 相同，按持久化主键 `id` 倒序取最后一条
- 是否在“完全没有 final”时回退到最新 attempt，可作为容错实现，但不作为本期核心契约

score 读取规则：
- 只读取最终被选中那条 attempt 的 `evaluationJson.score`
- 若该键不存在、为空或不是数值，则按 `null` 处理
- 不得从 `signal`、`answerStatus`、`commentary` 或其他派生字段推断分值

不在本任务强行组装以下前端可派生/可占位字段：
- `hasPrev/hasNext`
- `highlightedSegments`
- `idealAnswerOutline`
- `rewrittenAnswer`
- `strengthPoints/weakPoints`

- [ ] **Step 3: 在 `InterviewController` 暴露新接口**

新增：`GET /interviews/{sessionId}/questions/{questionId}`，复用现有用户归属校验风格。

- [ ] **Step 4: 运行后端测试**

Run: `cd backend && mvn -DskipTests=false test`
Expected: 新接口测试通过且无回归。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewQuestionReviewDto.java backend/src/main/java/com/a05/aiinterview/interview/service/InterviewQuestionReviewService.java backend/src/main/java/com/a05/aiinterview/interview/InterviewController.java backend/src/test/java/com/a05/aiinterview/interview/InterviewControllerQuestionDetailTest.java
git commit -m "feat(backend): add minimal interview question review endpoint"
```

## Chunk 3: 复盘前端对齐（优先后端数据，降级仅作容错）

### Task 5: 报告页消费 `report.questions` 轻量列表

**Files:**
- Modify: `frontend/src/components/InterviewPage.vue`
- Modify: `frontend/src/components/InterviewResultPage.vue`

- [ ] **Step 1: 接入报告题目列表**

在 `finishInterview` 拿到 `report` 后，把 `report.questions` 作为结果页题目来源优先级 1。

- [ ] **Step 2: 明确降级边界**

当 `report.questions` 缺失或接口失败时，才使用现有本地 `answers` 列表兜底；本地拼装不再作为长期并行主路径维护。

执行约束：
- 当后端题目列表成功返回时，前端不得再用本地伪 `score/status/commentary` 覆盖后端同名字段
- 仅允许对后端缺失字段做派生补全

- [ ] **Step 3: 真实分缺失时不显示分数**

结果页题目项仅在 `item.score != null` 时显示分数徽标；若后端暂无真实单题分，则显示状态/文案但不展示伪分数。

- [ ] **Step 4: 构建验证**

Run: `cd frontend && npm run build`
Expected: 结果页问题列表优先展示后端轻量题目；无真实分数时 UI 不显示分数字样。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/InterviewPage.vue frontend/src/components/InterviewResultPage.vue
git commit -m "feat(frontend): consume report question summaries with fallback only for error recovery"
```

### Task 6: 单题复盘页接入最小后端详情，并在前端派生其余展示字段

**Files:**
- Modify: `frontend/src/api/resume.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/components/QuestionDetailPage.vue`

- [ ] **Step 1: 新增前端 API 方法**

`getInterviewQuestionDetail(sessionId, questionId)` -> `GET /api/v1/interviews/{sessionId}/questions/{questionId}`.

- [ ] **Step 2: 打开详情时优先请求后端核心数据**

有 `sessionId + questionId` 时优先请求后端；成功后以后端返回为主重新构造详情页核心 view-model；仅对后端未提供的增强字段使用前端派生值补齐，失败时才降级到现有本地拼装逻辑。

合并约束：
- 后端成功返回时，不允许本地 mock 分数、点评、标签覆盖后端同名字段
- 仅允许在后端缺失字段时补充前端派生值

- [ ] **Step 3: 把可派生字段留在前端**

前端继续负责：
- `hasPrev/hasNext`：由当前题目列表或当前页面状态推导
- `highlightedSegments`
- `idealAnswerOutline`
- `rewrittenAnswer`
- `strengthPoints/weakPoints`

后端只提供真实主信息，不为满足页面现状去“硬凑”全部字段。

- [ ] **Step 4: `QuestionDetailPage` 字段映射收口**

把后端字段映射到现有页面核心展示区：
- 题目信息区
- 我的回答区
- 评分与点评区（`score` 为空时不显示分数）

其余增强区块在本期继续使用当前本地派生或占位逻辑，确保页面可运行但不扩张后端范围。

- [ ] **Step 5: 构建验证**

Run: `cd frontend && npm run build`
Expected: 单题详情可打开并显示后端返回主字段；接口失败时仍可降级，不发生页面崩溃。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/api/resume.js frontend/src/App.vue frontend/src/components/QuestionDetailPage.vue
git commit -m "feat(frontend): load minimal question review detail from backend with derived frontend enrichments"
```

## Chunk 4: 后置文档与联调清单（主功能稳定后再做）

> 该 Chunk 为收尾项，不与 Chunk 1-3 强绑定；只有在主流程联调稳定后再补。

### Task 7: 补充联调 checklist 与最小冒烟说明

**Files:**
- Modify: `docs/page-list.md` (append implementation notes)

- [ ] **Step 1: 在核心功能稳定后补充 checklist**

覆盖：
- 设备检测真实 ping 与阈值状态
- 面试 `attempts + SSE` 正常
- 报告返回轻量题目列表
- 单题详情接口可用

- [ ] **Step 2: 执行后端验证**

Run: `cd backend && mvn -DskipTests=false test`
Expected: 全绿。

- [ ] **Step 3: 执行前端验证**

Run: `cd frontend && npm run build`
Expected: Build success。

- [ ] **Step 4: 手工冒烟（最小路径）**

1. 在现有面试准备页创建会话
2. 专业模式完成设备检测（看到真实 ping）
3. 进入现有 loading / interview 单页流程
4. 作答到结束，进入报告页
5. 点击题目进入单题详情页

- [ ] **Step 5: 提交**

```bash
git add docs/page-list.md
git commit -m "docs: add post-stabilization checklist for interview flow alignment"
```

## Assumptions and Defaults

- 保持现有 `attempts + SSE` 主链路，不新增 `submit-and-next/skip-and-next/hint` 兼容端点。
- 本期暂缓 `vue-router`、URL 深链、刷新恢复与浏览器返回键语义治理，优先保证当前单页流转下的前后端联调可跑通。
- `sessionId` 通过现有组件状态和结果页数据透传，不作为 URL 参数管理。
- `backendSessionId` 是运行时唯一真值；`resultData.sessionId`、详情页参数等只是派生副本，不得反向成为新的状态源。
- `report.questions[].score` 与单题详情 `score` 仅在真实值存在时返回并展示，不再使用临时映射分数。
- “真实分”仅指显式持久化在被选中 attempt 评估对象中的 `evaluationJson.score`；若无该键，返回 `null` 属于预期行为。
- 单题详情接口本期只保证主信息可对齐；`highlightedSegments/outline/rewrite/AI 追问增强` 继续由前端本地派生或占位，不在本期扩张为完整后端合同。
- 前端降级只用于接口缺失/失败容错，不继续把本地拼装作为长期主路径维护。
- `history/question-bank/resumes/analysis/settings` 保持现有页面结构，本期以“不破坏现状 + 打通面试主流程”为边界。
- 路由化与深链恢复作为后续独立阶段处理，避免与本期联调目标耦合。

## Risks to Watch

- 本期不做路由化，意味着刷新恢复与深链访问仍不是验收目标；如果后续需求恢复这一能力，应单独立项而不是顺手塞回本计划。
- 设备 ping 即使不走真实浏览器跨域，也会受首次代理转发、后端连接建立、TLS 握手等影响；若不做预热，首个样本会显著失真。
- ping 若实现成过重的并发/超时编排，容易引入取消竞态；应优先选择可稳定落地的最简方案，并保留串行降级出口。
- 若 attempt 排序没有补上 `id` 作为 tie-breaker，测试在低精度时间戳或批量插入场景下会出现不稳定结果。
- `QuestionDetailPage.vue` 当前展示字段很多，本期必须坚持“核心数据后端化，其余前端派生”，否则会引入大量脆弱 DTO。
- 若后端成功返回后仍允许本地伪数据覆盖同名字段，页面会形成真假混杂的数据源，后续排错成本很高。
- 文档/checklist 只应在功能稳定后补，避免用文档产出掩盖主链路仍不稳定的问题。

Plan complete and saved to `docs/superpowers/plans/2026-03-13-interview-flow-url-ping-review-alignment.md`. Ready to execute?
