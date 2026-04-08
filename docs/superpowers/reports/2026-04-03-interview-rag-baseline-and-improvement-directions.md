# 面试 RAG 基线与改进方向

## 文档目的

本文用于统一当前已经落地的面试 RAG 基线，并明确后续演进时哪些约束仍然成立、哪些内容不再视为现行契约。

- 本文描述的是**当前代码口径下的现行基线**。
- 本文重点回答四件事：检索意图怎么表达、题卡材料怎么组织、双路召回怎么跑、当前不再采用哪些路线。
- 更专业的 RAG 质量评估体系后续单独设计；本文不再把主观质量门槛写成现行 blocker。

## 现行基线一句话

当前面试 RAG 基线是：

**单库题目卡片 + 单意图检索 + dense 与 sparse/BM25 双路独立召回 + RRF 融合 + 商业 rerank 主排序 + 程序硬护栏 + Top N 题卡注入出题链路。**

也就是：

- dense 分支负责语义召回
- sparse/BM25 分支负责术语锚点召回
- 两路候选先融合，再做最终排序和后置护栏

## 现行实现摘要

### 1. retrieval brief 继续保持 3 字段

AI retrieval brief 当前仍然只保留：

- `queryText`
- `keywordHints`
- `difficultyHint`

它们的职责是：

- `queryText`：完整语义意图
- `keywordHints`：术语锚点
- `difficultyHint`：排序软提示

当前不再恢复旧 retrieval 字段，也不再让程序为旧字段兜底。

### 2. 题卡材料已经拆成 dense 文本与 sparse 文本

当前题卡仍然是单库单卡设计，但文档侧已经显式拆成两类检索文本：

- dense 文本：保留完整语义上下文
- sparse 文本：保留术语锚点并压缩叙事噪音

因此当前实现不再使用“一份正文同时兼顾所有阶段”的旧折中口径。

### 3. collection 与 schema 已按 hybrid 方式组织

当前默认 collection 为：

- `interview_knowledge_hybrid`

当前默认 named vector 为：

- dense：`dense`
- sparse：`bm25`

当前 payload 索引只保留仍有业务价值的元数据索引：

- `question_type`
- `domain_code`
- `active`

### 4. 当前真实检索主链路

当前真实主链路已经收敛为：

1. `RagPlanCompiler` 生成 `queryText / denseQueryText / sparseQueryText`
2. `QdrantHybridQueryExecutor` 执行 dense 分支查询
3. `QdrantHybridQueryExecutor` 执行 sparse/BM25 分支查询
4. `RrfFusion` 融合两路候选
5. `DashScopeRagRerankService` 执行最终主排序
6. `RagRetrievalServiceImpl` 执行题型与领域硬护栏
7. `QuestionGenerationInput` 注入 Top N 材料

### 5. 当前入库主路径

当前入库已经切换到原生 Qdrant Java Client：

- `KnowledgeIngestionService` 负责计算 dense embedding
- `QdrantHybridPointMapper` 负责构造 stable point、payload 与 named vectors
- 入库通过 Qdrant 原生 upsert 写入 dense vector、sparse document 与 payload

## 仍然成立的约束

### 1. 单意图，不做多意图并行召回

当前执行语义仍然要求：

- `retrievalPlans` 最多 1 条
- 一轮下一题只围绕一个主焦点检索

### 2. 前置过滤只保留低误伤边界

当前查询执行前只下推这些刚性边界：

- `active=true`
- `questionType`
- `domainCode` 可选精确过滤

### 3. `difficultyHint` 仍然不是硬过滤

当前 `difficultyHint` 仍然只作为排序提示，不作为召回层硬约束。

### 4. 最终硬护栏仍然必须存在

无论前面怎么召回和排序，最终都保留程序级硬护栏：

- 行为题不能混入技术题
- 非行为题不能题型漂移
- 明显领域冲突的候选要剔除
- 项目题不能退化成脱离项目语境的泛八股

## 当前不再采用的路线

下面这些不再视为现行主线：

- 让单一路径先压缩 dense 候选空间
- 用通用 payload 文本匹配充当主术语召回路线
- 恢复旧 retrieval 字段
- 把主观命中率门槛当作当前代码正确性的硬标准

## 当前审计与回归边界

### 1. 现行审计字段

当前检索审计字段已经统一为：

- `retrievalTriggered`
- `denseCandidateCount`
- `sparseCandidateCount`
- `fusionTopQuestionIds`
- `rerankPreTopQuestionIds`
- `rerankPostTopQuestionIds`
- `injectedQuestionIds`

### 2. 当前明确契约

当前代码层面明确保留的回归契约只有两类：

- `shouldRetrieve=false` 不触发真实检索
- `RagPlanCompiler` 对 `questionType / queryText / denseQueryText / sparseQueryText` 的编译结果

像 `domainCode`、`keywordQueries`、主观命中率、污染率、follow-up 精确命中等内容，不再作为当前代码正确性的硬门槛。

### 3. 当前没有保留主观评测夹具

仓库当前已经删除旧的主观评测夹具；更专业的 RAG 质量评估需要后续单独建设。

## 运维与升级注意事项

当前 cutover 仍然有两类人工风险需要单独处理：

- Qdrant server 是否支持 sparse/BM25 与当前 named vector schema
- Qdrant client 与 server 的版本兼容性是否满足要求

因此上线切换仍应遵循：

- 新建 collection
- 重建数据
- 验证检索
- 切换默认 collection
- 最后再删除旧 collection

## 结论

当前面试 RAG 已经不再是旧的单路裁剪方案，而是：

- 继续保留单库题目卡片
- 继续保留 3 字段 retrieval brief
- 使用 dense + sparse/BM25 双路独立召回
- 使用 RRF 做第一版融合
- 使用商业 rerank 做最终主排序
- 使用程序硬护栏做最后兜底

后续如果继续演进，应该沿着更专业的评估体系、版本兼容治理、collection 运维自动化去做，而不是回到已被放弃的旧路线。
