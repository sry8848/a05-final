# Structured Output Format Authority Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一 Spring AI 结构化输出链路的格式控制权，让格式 schema 只有一个真源，并让自定义输出约束在代码结构上优先生效。

**Architecture:** 本次改造只处理结构化输出边界，不改动非结构化出题链路。格式语法统一由 `BeanOutputConverter` 通过 `{{outputSchema}}` 注入；Prompt 只保留业务语义约束；当 Prompt 与 DTO/schema 冲突时，通过专用 AI DTO 和后端映射把“自定义约束优先”落实到代码。

**Tech Stack:** Spring Boot 3.2, Spring AI 1.0, Jackson, JUnit 5, AssertJ, Markdown prompt templates

---

## Scope

本计划只覆盖四条结构化输出链路及其测试：

1. `planner`
2. `evaluation-decision`
3. `report-generation`
4. `question-detail-evaluation`

不包含：

1. `question_generation_stream`
2. `question_consult`
3. provider-specific response format 升级

## File Map

- `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
  - 统一结构化输出 schema 注入方式
- `backend/src/main/resources/prompts/planner.md`
  - 删除手写 JSON 结构，改为显式注入 `{{outputSchema}}`
- `backend/src/main/resources/prompts/evaluation-decision.md`
  - 删除手写 JSON 结构，保留语义规则并注入 `{{outputSchema}}`
- `backend/src/main/resources/prompts/report-generation.md`
  - 保持现状，仅作为统一性对照
- `backend/src/main/resources/prompts/question-detail-evaluation.md`
  - 保持 `{{outputSchema}}`，但与新的 AI DTO 对齐
- `backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java`
  - 增加稳定字段顺序声明
- `backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionDetailEvaluationAiOutput.java`
  - 新建模型专用输出 DTO，移除 `start/end`
- `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionDetailEvaluationService.java`
  - 将 AI 输出 DTO 映射为最终落库 DTO
- `backend/src/main/java/com/a05/aiinterview/interview/service/support/HighlightedAnnotationLocator.java`
  - 适配新的 AI 输出候选数据
- `backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java`
  - 增加 `outputSchema` 哨兵断言
- `backend/src/test/java/com/a05/aiinterview/ai/QuestionDetailEvaluationOutputTest.java`
  - 改成覆盖 AI DTO 和最终 DTO 映射
- `backend/src/test/java/com/a05/aiinterview/ai/impl/OpenAiClientEvaluationDecisionVariablesTest.java`
  - 补足 `outputSchema` 变量使用断言
- `backend/src/test/java/com/a05/aiinterview/interview/service/support/HighlightedAnnotationLocatorTest.java`
  - 如不存在则新增，验证 `quote -> start/end` 映射

## Chunk 1: 统一 Prompt Schema 注入方式

### Task 1: 先用测试锁定 `outputSchema` 必须进入最终 Prompt

**Files:**
- Modify: `backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/ai/impl/OpenAiClientEvaluationDecisionVariablesTest.java`

- [ ] **Step 1: 为 `evaluation_decision` 增加失败断言**

在 `PromptTemplateCoverageTest` 中给 `evaluation_decision` 传入哨兵值：

```java
Map.entry("outputSchema", "__SCHEMA_SENTINEL__")
```

并断言：

```java
assertThat(rendered.getUserPrompt()).contains("__SCHEMA_SENTINEL__");
```

- [ ] **Step 2: 为 `planner` 增加失败断言**

新增或扩展 `planner` 覆盖测试，传入 `outputSchema` 哨兵值，并断言渲染后的 Prompt 必须包含该值。

- [ ] **Step 3: 为 `report_generation` 与 `question_detail_evaluation` 补齐统一性断言**

让这两条已有 `{{outputSchema}}` 的链路也统一断言哨兵值存在，防止以后回退。

- [ ] **Step 4: 运行测试确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=PromptTemplateCoverageTest,OpenAiClientEvaluationDecisionVariablesTest" test
```

Expected:

- `evaluation_decision` 测试失败，因为模板尚未使用 `outputSchema`
- `planner` 测试失败，因为模板尚未预留 `outputSchema`

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java backend/src/test/java/com/a05/aiinterview/ai/impl/OpenAiClientEvaluationDecisionVariablesTest.java
git commit -m "test: lock structured output schema injection"
```

### Task 2: 修正 `planner` 与 `evaluation-decision` 的 Prompt 事实来源

**Files:**
- Modify: `backend/src/main/resources/prompts/planner.md`
- Modify: `backend/src/main/resources/prompts/evaluation-decision.md`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`

- [ ] **Step 1: 修改 `planner.md`**

删除手写 `Output Schema` 段落，保留“只输出合法 JSON”的业务要求，并在尾部新增：

```md
【输出格式要求】
{{outputSchema}}
```

- [ ] **Step 2: 修改 `evaluation-decision.md`**

删除整段硬编码 JSON 结构示例，只保留字段语义约束，并在模板末尾新增：

```md
【输出格式要求】
{{outputSchema}}
```

- [ ] **Step 3: 修改 `OpenAiClient.callPlanner(...)`**

将：

```java
String userPrompt = rendered.getUserPrompt() + "\n\n" + converter.getFormat();
```

改为与其他链路一致的模板注入模式：

```java
rendered = renderPrompt(PROMPT_CODE_PLANNER, buildPlannerVariables(input, converter.getFormat()));
ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());
```

并新增：

```java
private Map<String, Object> buildPlannerVariables(PlannerInput input, String outputSchema)
```

- [ ] **Step 4: 运行测试确认修复**

Run:

```powershell
rtk mvn -q "-Dtest=PromptTemplateCoverageTest,OpenAiClientEvaluationDecisionVariablesTest" test
```

Expected:

- 所有结构化 Prompt 覆盖测试通过
- `planner` 不再依赖字符串追加 schema

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java backend/src/main/resources/prompts/planner.md backend/src/main/resources/prompts/evaluation-decision.md backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java backend/src/test/java/com/a05/aiinterview/ai/impl/OpenAiClientEvaluationDecisionVariablesTest.java
git commit -m "refactor: unify structured output schema injection"
```

## Chunk 2: 让自定义约束在 question-detail 链路优先

### Task 3: 为单题详细评估拆出专用 AI 输出 DTO

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionDetailEvaluationAiOutput.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
- Modify: `backend/src/main/resources/prompts/question-detail-evaluation.md`
- Modify: `backend/src/test/java/com/a05/aiinterview/ai/QuestionDetailEvaluationOutputTest.java`

- [ ] **Step 1: 写失败测试，锁定 AI schema 不得暴露 `start/end`**

在 `QuestionDetailEvaluationOutputTest` 中新增基于新 DTO 的 converter 测试，断言：

```java
assertThat(converter.getFormat()).doesNotContain("\"start\"");
assertThat(converter.getFormat()).doesNotContain("\"end\"");
```

同时保留对 `quote/label/comment` 的解析断言。

- [ ] **Step 2: 运行测试确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=QuestionDetailEvaluationOutputTest" test
```

Expected:

- 失败，因为当前仍使用包含 `start/end` 的 DTO 生成 schema

- [ ] **Step 3: 新建 `QuestionDetailEvaluationAiOutput`**

字段建议：

```java
BigDecimal score;
String commentary;
List<String> strengthPoints;
List<String> weakPoints;
List<EvaluatedDomain> evaluatedDomains;
List<HighlightedSegment> highlightedSegments;
List<HighlightedAnnotationCandidate> highlightedAnnotations;
List<String> idealAnswerOutline;
String rewrittenAnswer;
```

其中 `HighlightedAnnotationCandidate` 只保留：

```java
String quote;
String label;
String comment;
```

- [ ] **Step 4: 修改 `OpenAiClient.callQuestionDetailEvaluation(...)`**

将 converter 目标类型从最终 DTO 改为新的 AI DTO，再在调用结束前映射为现有 `QuestionDetailEvaluationOutput`。

- [ ] **Step 5: 更新 Prompt 注释**

保持 `question-detail-evaluation.md` 业务规则不变，但核对字段名与新的 AI DTO 完全一致，不再让文本规则和 schema 互相打架。

- [ ] **Step 6: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=QuestionDetailEvaluationOutputTest" test
```

Expected:

- schema 不再包含 `start/end`
- JSON 解析测试通过

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionDetailEvaluationAiOutput.java backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java backend/src/main/resources/prompts/question-detail-evaluation.md backend/src/test/java/com/a05/aiinterview/ai/QuestionDetailEvaluationOutputTest.java
git commit -m "refactor: split question detail ai output from persisted output"
```

### Task 4: 把 AI 输出映射为最终带定位区间的 DTO

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/QuestionDetailEvaluationService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/interview/service/support/HighlightedAnnotationLocator.java`
- Create: `backend/src/test/java/com/a05/aiinterview/interview/service/support/HighlightedAnnotationLocatorTest.java`

- [ ] **Step 1: 写失败测试，锁定 `quote -> start/end` 由后端负责**

新增 `HighlightedAnnotationLocatorTest`，示例断言：

```java
assertThat(resolved.getHighlightedAnnotations()).hasSize(1);
assertThat(resolved.getHighlightedAnnotations().getFirst().getStart()).isEqualTo(0);
assertThat(resolved.getHighlightedAnnotations().getFirst().getEnd()).isGreaterThan(0);
```

- [ ] **Step 2: 运行测试确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=HighlightedAnnotationLocatorTest" test
```

Expected:

- 如果测试新建后尚未接线，应因类型不匹配或映射缺失失败

- [ ] **Step 3: 为 `HighlightedAnnotationLocator` 增加候选映射入口**

让定位器可以消费 AI DTO 的注释候选，或者在进入定位器前完成一次显式转换。

- [ ] **Step 4: 更新 `QuestionDetailEvaluationService`**

确保服务层流程变为：

1. AI 返回 `QuestionDetailEvaluationAiOutput`
2. 转成无 `start/end` 的最终候选结构
3. 调用 `HighlightedAnnotationLocator.resolve(...)`
4. 落库 `QuestionDetailEvaluationOutput`

- [ ] **Step 5: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=QuestionDetailEvaluationOutputTest,HighlightedAnnotationLocatorTest" test
```

Expected:

- AI DTO 解析测试通过
- 定位器测试通过

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/interview/service/QuestionDetailEvaluationService.java backend/src/main/java/com/a05/aiinterview/interview/service/support/HighlightedAnnotationLocator.java backend/src/test/java/com/a05/aiinterview/interview/service/support/HighlightedAnnotationLocatorTest.java backend/src/test/java/com/a05/aiinterview/ai/QuestionDetailEvaluationOutputTest.java
git commit -m "refactor: compute question detail annotation ranges in backend"
```

## Chunk 3: 固化 evaluation-decision 的顺序与契约

### Task 5: 用 DTO 声明固定字段顺序，并补足回归测试

**Files:**
- Modify: `backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java`
- Modify: `backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java`

- [ ] **Step 1: 写失败测试，锁定 schema 顶层字段顺序**

新增基于 `BeanOutputConverter<EvaluationDecisionOutput>` 的断言，至少检查以下字段顺序：

```java
"answerUnderstanding"
"newCoveredDomains"
"newCoveredPoints"
"planningIntent"
"interviewAction"
"decisionReason"
"finalDecision"
"nextFocus"
```

可通过比较 `indexOf(...)` 顺序实现。

- [ ] **Step 2: 运行测试确认当前失败**

Run:

```powershell
rtk mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest" test
```

Expected:

- 若默认导出顺序与 Prompt 约束不一致，测试失败

- [ ] **Step 3: 为 `EvaluationDecisionOutput` 添加 `@JsonPropertyOrder`**

顺序必须与 `evaluation-decision.md` 中的业务约束完全一致。

- [ ] **Step 4: 复跑测试**

Run:

```powershell
rtk mvn -q "-Dtest=EvaluationDecisionContractTest,PromptTemplateCoverageTest" test
```

Expected:

- schema 顺序与 Prompt 约束一致
- 现有契约测试保持通过

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java
git commit -m "test: lock evaluation decision schema field order"
```

## Chunk 4: 全链路回归验证

### Task 6: 运行结构化输出回归测试集

**Files:**
- Test: `backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/ai/impl/OpenAiClientEvaluationDecisionVariablesTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/ai/QuestionDetailEvaluationOutputTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/interview/service/support/HighlightedAnnotationLocatorTest.java`

- [ ] **Step 1: 运行定向测试集**

Run:

```powershell
rtk mvn -q "-Dtest=PromptTemplateCoverageTest,OpenAiClientEvaluationDecisionVariablesTest,EvaluationDecisionContractTest,QuestionDetailEvaluationOutputTest,HighlightedAnnotationLocatorTest" test
```

Expected:

- 全部通过
- 不再出现 `planner` 双重 schema
- 不再出现 `evaluation-decision` schema 漏注入
- `question-detail-evaluation` schema 不再暴露 `start/end`

- [ ] **Step 2: 运行后端最小回归**

Run:

```powershell
rtk mvn -q "-Dtest=QuestionDetailEvaluationServiceTest,ReportGenerationServiceTest" test
```

Expected:

- 受影响服务回归通过

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java backend/src/main/java/com/a05/aiinterview/ai/dto/EvaluationDecisionOutput.java backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionDetailEvaluationAiOutput.java backend/src/main/java/com/a05/aiinterview/interview/service/QuestionDetailEvaluationService.java backend/src/main/java/com/a05/aiinterview/interview/service/support/HighlightedAnnotationLocator.java backend/src/main/resources/prompts/planner.md backend/src/main/resources/prompts/evaluation-decision.md backend/src/main/resources/prompts/question-detail-evaluation.md backend/src/test/java/com/a05/aiinterview/ai/prompt/PromptTemplateCoverageTest.java backend/src/test/java/com/a05/aiinterview/ai/impl/OpenAiClientEvaluationDecisionVariablesTest.java backend/src/test/java/com/a05/aiinterview/ai/contract/EvaluationDecisionContractTest.java backend/src/test/java/com/a05/aiinterview/ai/QuestionDetailEvaluationOutputTest.java backend/src/test/java/com/a05/aiinterview/interview/service/support/HighlightedAnnotationLocatorTest.java
git commit -m "refactor: make structured output format authority explicit"
```

## Notes

- `PromptTemplateService.warnUnusedVariables(...)` 当前只记录日志，不足以阻止结构化输出漏接线。本计划用测试兜底，而不是先扩大运行时行为变更面。
- 若执行过程中发现 `report-generation` 或其他结构化链路也有 DTO 与 Prompt 规则冲突，应继续沿用同一原则：模型专用 DTO 与最终持久化 DTO 分离。
- 本计划默认不引入 provider-specific schema response 功能，避免把职责边界修复和供应商能力切换绑在一起。
