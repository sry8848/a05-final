# 当前 Prompt 策略

本文只描述当前后端实际接入的 Prompt 集合和职责边界。

## Prompt 目录

当前模板文件位于：

- [backend/src/main/resources/prompts](/D:/a05-cursor/backend/src/main/resources/prompts)

当前有效模板：

| Prompt Code | 文件 | 作用 |
| --- | --- | --- |
| `planner` | `planner.md` | 创建面试会话后的考纲规划 |
| `evaluation_decision` | `evaluation-decision.md` | 评估当前回答并决定下一步动作 |
| `question_generation_stream` | `question-generation-stream.md` | 生成下一题题干文本 |
| `question_detail_evaluation` | `question-detail-evaluation.md` | 生成单题详细复盘 |
| `report_generation` | `report-generation.md` | 生成整场报告 |
| `intro_rewrite` | `intro-rewrite.md` | 改写首题措辞 |

对应代码入口见 [PromptCode.java](/D:/a05-cursor/backend/src/main/java/com/a05/aiinterview/ai/prompt/PromptCode.java)。

## 当前编排职责

### 创建面试

创建面试时，后端会：

1. 读取岗位、简历、JD、历史信息
2. 调用 `planner`
3. 生成 `syllabus_json`
4. 初始化 `state_ledger_json`
5. 返回会话进入 `planning` / `in_progress`

### 提交回答

提交回答时，后端会：

1. 保存 `interview_attempts`
2. 调用 `evaluation_decision`
3. 根据决策更新状态账本
4. 客户端再通过 SSE 触发 `question_generation_stream`
5. 流式落地新题，并按需触发 TTS

### 单题复盘

单题详情和单题重答会使用：

- `question_detail_evaluation`

### 整场报告

面试结束后会使用：

- `report_generation`

### 首题措辞

在首题改写场景下会使用：

- `intro_rewrite`

## 当前配置方式

Prompt 版本号在：

- [backend/src/main/resources/application.yml](/D:/a05-cursor/backend/src/main/resources/application.yml)

当前键包括：

- `ai.prompt.version.planner`
- `ai.prompt.version.question-generation-stream`
- `ai.prompt.version.evaluation-decision`
- `ai.prompt.version.report-generation`
- `ai.prompt.version.intro-rewrite`
- `ai.prompt.version.question-detail-evaluation`

## 当前边界

现行 Prompt 文档只记录当前代码中真正存在并被 `PromptCode` 引用的模板。历史命名和旧拆分方式如需追溯，请查看归档文档。

## 与运行时的关系

- Prompt 模板只负责模型输入输出约束
- 幂等、SSE 缓存、RAG 检索、TTS 片段、状态账本推进，都由后端服务层负责
- “提交回答并继续”是后端编排动作，不是单独的 Prompt 名称
