# Question Detail Evaluation Service Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改动现有 `evaluation_decision + attempts + SSE` 主链路职责的前提下，新增一个轻量的异步单题详细评估服务，并让问答详情页优先消费真实后端数据。

**Architecture:** 继续保留 `evaluation_decision` 负责流程决策，新增 `question_detail_evaluation` 负责题后复盘。详细评估结果直接挂在 `interview_attempts` 的最终回答记录上，避免引入新表、新队列和额外编排层；详情页通过 `sessionId + questionId` 查询“该题最新 final attempt”的详细评估，未就绪时返回 `generating`。

**Tech Stack:** Spring Boot 3, MyBatis-Plus, MySQL, Vue 3, fetch API.

**Non-Goals:** 本期不实现 `ai-consult` 真后端、不补路由深链、不改整场报告口径、不引入消息队列或定时重试系统。

---

## Chunk 1: 后端最小数据闭环
r
### Task 1: 为 final attempt 增加单题详细评估存储位

**Files:**
- Modify: `backend/src/main/resources/db/schema-interview.sql` or the active interview schema file
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/entity/InterviewAttempt.java`
- Modify: `backend/src/main/resources/mapper/interview/InterviewAttemptMapper.xml`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/entity/InterviewAttemptMappingTest.java` (new if missing)

- [ ] **Step 1: 写一个映射失败测试或最小集成测试**

覆盖断言：`InterviewAttempt` 能正确读写两个新字段：
- `detailEvaluationStatus`
- `detailEvaluationJson`

状态值固定为：
- `pending`
- `generating`
- `ready`
- `failed`

- [ ] **Step 2: 修改表结构，保持最小新增**

给 `interview_attempts` 新增两列：
- `detail_evaluation_status varchar(32) not null default 'pending'`
- `detail_evaluation_json json null`

不要拆新表。原因：
- 单题详细评估天然依附于某次最终回答
- 当前系统已经以 latest final attempt 作为“本题最终答案”来源
- 本期优先降低 join、迁移和维护成本

- [ ] **Step 3: 扩展实体和 mapper**

在 `InterviewAttempt` 中新增：
- `private String detailEvaluationStatus;`
- `private Map<String, Object> detailEvaluationJson;`

沿用现有 `JacksonTypeHandler`，不要自定义复杂 type handler。

- [ ] **Step 4: 跑后端最小测试**

Run: `cd backend && mvn -DskipTests=false -Dtest=InterviewAttemptMappingTest test`
Expected: 新字段可读写，JSON 映射通过。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/resources/db backend/src/main/java/com/a05/aiinterview/interview/entity/InterviewAttempt.java backend/src/main/resources/mapper/interview/InterviewAttemptMapper.xml backend/src/test/java/com/a05/aiinterview/interview/entity/InterviewAttemptMappingTest.java
git commit -m "feat(backend): add storage for question detail evaluation on attempts"
```

### Task 2: 新增独立 AI 输出 DTO 与 prompt，不复用 evaluation_decision

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionDetailEvaluationInput.java`
- Create: `backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionDetailEvaluationOutput.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/AiClient.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/impl/MockAiClient.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
- Create: `backend/src/main/resources/prompts/question-detail-evaluation.md`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/a05/aiinterview/ai/QuestionDetailEvaluationOutputTest.java`

- [ ] **Step 1: 先定输出 Schema，只保留详情页必需字段**

`QuestionDetailEvaluationOutput` 只包含：
- `score`
- `commentary`
- `strengthPoints`
- `weakPoints`
- `evaluatedDomains`
- `highlightedSegments`
- `idealAnswerOutline`
- `rewrittenAnswer`

不要在一期加入：
- 置信度
- 多版本候选答案
- 审批字段
- 人工修订字段

- [ ] **Step 2: 设计输入，复用现有题目和回答上下文**

输入只拿：
- 岗位、年限、模式
- 当前题干、题型、知识域、目标深度
- 用户最终回答
- `expectedPoints`
- 近题上下文（可选，最多复用现有 window）

不要把整场报告生成逻辑塞进这里。

- [ ] **Step 3: 新增 prompt 模板**

新建 `question-detail-evaluation.md`，要求模型专注“题后复盘”，禁止输出下一题策略。核心约束：
- 结论必须以题干和回答为依据
- 分数和批注不能编造候选人未表达的信息
- `highlightedSegments` 必须能直接给前端渲染
- `idealAnswerOutline` 控制在 3~5 点
- `rewrittenAnswer` 保持示范性，不写成长文八股

- [ ] **Step 4: 先写 DTO 解析测试，再接 AI Client**

测试目标：
- `BeanOutputConverter<QuestionDetailEvaluationOutput>` 能稳定解析样例 JSON
- `MockAiClient` 返回完整结构，不是空壳

- [ ] **Step 5: 扩展 AI client 接口**

在 `AiClient` 增加：
- `AiCallResult<QuestionDetailEvaluationOutput> callQuestionDetailEvaluation(QuestionDetailEvaluationInput input)`

要求：
- `MockAiClient` 返回固定但结构完整的数据
- `OpenAiClient` 走独立 prompt code：`question_detail_evaluation`
- `application.yml` 增加 prompt version 配置

- [ ] **Step 6: 跑 AI 相关测试**

Run: `cd backend && mvn -DskipTests=false -Dtest=QuestionDetailEvaluationOutputTest test`
Expected: DTO 解析和 Mock 输出测试通过。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/ai backend/src/main/resources/prompts/question-detail-evaluation.md backend/src/main/resources/application.yml backend/src/test/java/com/a05/aiinterview/ai/QuestionDetailEvaluationOutputTest.java
git commit -m "feat(ai): add question detail evaluation prompt and dto"
```

## Chunk 2: 后端服务接入，但不拖慢主答题链路

### Task 3: 新增异步 `QuestionDetailEvaluationService`，提交成功后触发

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionDetailEvaluationService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/mapper/InterviewAttemptMapper.java`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/service/QuestionDetailEvaluationServiceTest.java`

- [ ] **Step 1: 先写服务测试，卡住职责边界**

覆盖 3 个场景：
- 正常生成：attempt 状态从 `pending/generating` 到 `ready`
- AI 调用失败：attempt 状态变 `failed`
- 幂等重复触发：`ready` 的 attempt 不重复生成

- [ ] **Step 2: 服务实现保持轻量，不做复杂调度**

`QuestionDetailEvaluationService` 做 4 件事：
1. 读取 session、question、attempt
2. 组装 `QuestionDetailEvaluationInput`
3. 调 AI
4. 把结果写回 `attempt.detailEvaluationJson` 和 `detailEvaluationStatus`

明确不做：
- MQ 投递
- 延迟队列
- 分布式锁框架
- 多次重试编排

- [ ] **Step 3: 在 `submitAnswer` 成功后异步触发**

触发点放在：
- `saveAttempt(...)` 成功之后
- `stateLedgerPatchService.applyPatch(...)` 之后也可以，但不要阻塞返回

要求：
- 即使详细评估失败，也不能影响 `submitAttempt` 主成功路径
- 日志记录 `sessionId/questionId/attemptId`
- 只对 `isFinal=true` 的回答触发

- [ ] **Step 4: 状态流转规则固定**

规则：
- insert attempt 时默认 `pending`
- 开始异步评估前置为 `generating`
- 成功写入后置为 `ready`
- 失败置为 `failed`

不要做“自动重新排队”，一期先靠人工重试或后续页面刷新触发补偿。

- [ ] **Step 5: 跑后端测试**

Run: `cd backend && mvn -DskipTests=false -Dtest=QuestionDetailEvaluationServiceTest test`
Expected: 服务状态流转正确，主链路测试无回归。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/service/QuestionDetailEvaluationService.java backend/src/main/java/com/a05/aiinterview/interview/engine/AnswerSubmitService.java backend/src/main/java/com/a05/aiinterview/interview/mapper/InterviewAttemptMapper.java backend/src/test/java/com/a05/aiinterview/interview/service/QuestionDetailEvaluationServiceTest.java
git commit -m "feat(backend): trigger async question detail evaluation after final answer submit"
```

### Task 4: 提供真实单题详情接口 `GET /interviews/{sessionId}/questions/{questionId}`

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewQuestionDetailDto.java`
- Create: `backend/src/main/java/com/a05/aiinterview/interview/service/InterviewQuestionDetailService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/InterviewController.java`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/InterviewQuestionDetailServiceTest.java`

- [ ] **Step 1: 先写失败测试，固定 latest final attempt 规则**

至少覆盖：
- 同题多条 attempt，只选最新 `isFinal=true`
- 没有 final attempt 时返回基础信息 + `answerStatus=pending`
- `detailEvaluationStatus=generating` 时接口可正常返回占位结构

- [ ] **Step 2: DTO 只返回页面真实所需字段**

返回字段：
- `questionId`
- `questionNo`
- `questionStem`
- `domainName`
- `questionType`
- `difficulty`
- `userAnswer`
- `answerStatus`
- `evaluationStatus`
- `score`
- `commentary`
- `strengthPoints`
- `weakPoints`
- `evaluatedDomains`
- `highlightedSegments`
- `idealAnswerOutline`
- `rewrittenAnswer`

额外允许返回：
- `backfillFromLocalAllowed` = `true/false`

不要在一期顺手加上一堆导航字段。

- [ ] **Step 3: 服务层聚合规则写死，避免前端猜**

数据来源：
- 题目信息：`interview_questions`
- 回答与详细评估：最新 final `interview_attempts`
- `domainName`：优先从 `syllabusJson` 映射，找不到时退化为 domain code

规则：
- `evaluationStatus=ready`：完整返回详细评估
- `evaluationStatus=generating/failed/pending`：返回基础题目信息和回答，详细评估字段置空

- [ ] **Step 4: 在控制器新增接口**

新增：
- `GET /interviews/{sessionId}/questions/{questionId}`

保持与现有 controller 风格一致，不单独新开 report controller。

- [ ] **Step 5: 跑后端测试**

Run: `cd backend && mvn -DskipTests=false -Dtest=InterviewQuestionDetailServiceTest test`
Expected: 接口聚合结果与 latest final attempt 规则一致。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/dto/InterviewQuestionDetailDto.java backend/src/main/java/com/a05/aiinterview/interview/service/InterviewQuestionDetailService.java backend/src/main/java/com/a05/aiinterview/interview/InterviewController.java backend/src/test/java/com/a05/aiinterview/interview/InterviewQuestionDetailServiceTest.java
git commit -m "feat(backend): add interview question detail endpoint backed by async evaluation"
```

## Chunk 3: 前端详情页切到真实接口

### Task 5: 新增前端 question detail API，并改造详情页数据源

**Files:**
- Modify: `frontend/src/api/resume.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/components/QuestionDetailPage.vue`
- Test: `frontend` existing test setup if present; otherwise manual verification checklist only

- [ ] **Step 1: 新增前端 API 方法**

在 `frontend/src/api/resume.js` 增加：
- `getInterviewQuestionDetail(sessionId, questionId)`

不要新建一堆 API 文件，沿用当前项目风格。

- [ ] **Step 2: 调整 `App.vue` 的详情页加载策略**

规则：
- 有真实 `sessionId + questionId` 时，先调后端详情接口
- 请求成功后，用后端字段覆盖当前本地 view model
- 只有缺少参数或接口失败时，才退回本地拼装逻辑

这一步的核心是把本地拼装从“主逻辑”降级为“兜底逻辑”。

- [ ] **Step 3: 在 `QuestionDetailPage.vue` 明确展示评估状态**

新增 3 种 UI 状态：
- `generating`: 显示“单题评估生成中”
- `failed`: 显示“评估暂时失败，可稍后重试”
- `ready`: 正常展示真实评分与复盘内容

不要在前端再生成假 AI 评分文案去冒充真实结果。

- [ ] **Step 4: 保留本地收藏和本地 AI 追问模拟，但标注其性质**

本期允许：
- 收藏仍走本地逻辑
- `ai-consult` 仍保留前端模拟

但需要避免误导：
- 真实评分区和模拟追问区在代码职责上分开
- 不要再用本地 `buildCommentary/buildRewrittenAnswer` 覆盖后端返回值

- [ ] **Step 5: 进行手工联调验证**

验证路径：
1. 完成一题作答后立即进入详情页，先看到 `generating`
2. 刷新或重新打开后能看到 `ready`
3. 接口失败时会回退到本地兜底，但不会把兜底值误写成后端真实值
4. 没有 `questionId/sessionId` 的旧数据仍能打开详情页

- [ ] **Step 6: 构建验证**

Run: `cd frontend && npm run build`
Expected: Build success，问答详情页在真实接口和本地兜底之间能稳定切换。

- [ ] **Step 7: 提交**

```bash
git add frontend/src/api/resume.js frontend/src/App.vue frontend/src/components/QuestionDetailPage.vue
git commit -m "feat(frontend): load question detail from backend evaluation endpoint"
```

## Chunk 4: 最终回归与文档

### Task 6: 更新最小文档并完成回归验证

**Files:**
- Modify: `docs/api-design.md`
- Modify: `docs/db-schema.md`
- Modify: `docs/development-plan.md` (only if still needed)

- [ ] **Step 1: 更新接口文档**

在 `api-design.md` 中补齐 `GET /interviews/{sessionId}/questions/{questionId}` 的真实返回示例，明确 `evaluationStatus` 语义。

- [ ] **Step 2: 更新数据结构文档**

在 `db-schema.md` 中补齐 `interview_attempts.detail_evaluation_status/detail_evaluation_json`。

- [ ] **Step 3: 完整回归**

Run:
```bash
cd backend && mvn -DskipTests=false test
cd ../frontend && npm run build
```
Expected:
- 后端测试通过
- 前端构建通过
- 手工链路“答题 -> 异步生成单题评估 -> 打开详情页”可走通

- [ ] **Step 4: 提交**

```bash
git add docs/api-design.md docs/db-schema.md docs/development-plan.md
git commit -m "docs: document async question detail evaluation flow"
```

## Implementation Notes

- 不要把单题详细评估塞回 `EvaluationDecisionOutput`。这是职责倒退。
- 不要因为“以后也许会有人工审核”就先建 reviewer 表。现在没有这个需求。
- 不要为了异步两个字就上 MQ。当前 `@Async` 足够。
- 如果后续发现 `interview_attempts` 上的 JSON 负担过重，再考虑拆表；这不是一期问题。
- 如果当前环境仍是 `AI_MOCK_ENABLED=true`，先验证链路和字段完整性，不要把 Mock 结果误认为真实评分质量。

## Manual Acceptance Checklist

- [ ] 答题成功后不会因为详细评估失败而影响下一题流程
- [ ] 同一题存在多条 attempt 时，详情页只认最新 final attempt
- [ ] 详情页能区分 `generating / ready / failed`
- [ ] 前端不会再默认用本地假评分覆盖后端真实评分
- [ ] 没有实现 `ai-consult` 真后端也不会阻塞本期交付
