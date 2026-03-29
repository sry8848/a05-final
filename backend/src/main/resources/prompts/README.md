# Prompt 模板目录

当前后端实际使用的 Prompt 模板如下：

- `planner.md`
- `evaluation-decision.md`
- `question-generation-stream.md`
- `question-detail-evaluation.md`
- `question-consult.md`
- `report-generation.md`
- `intro-rewrite.md`

这些模板由 `PromptCode` 和 `PromptTemplateService` 统一加载，版本号通过配置项 `ai.prompt.version.*` 管理。

历史上出现过但当前后端未使用的 Prompt 名称，不应再作为现行说明写入文档。
