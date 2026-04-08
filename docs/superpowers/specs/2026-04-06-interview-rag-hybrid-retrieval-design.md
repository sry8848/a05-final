# 面试 RAG 双路召回改造设计

## 文档目的

本文定义面试 RAG 从当前 `lexical prefilter -> dense -> rerank` 单主链路，迁移到 `dense + lexical/sparse 双路独立召回 -> RRF -> rerank -> 硬护栏` 的目标设计。

本文不是执行计划，而是执行计划的前置设计说明，重点回答：

- 为什么当前链路要被替换
- 双路召回的程序边界怎么划分
- Qdrant collection 和入库数据怎么迁移
- 哪些步骤属于代码改造，哪些步骤需要人工执行

## 当前现状

当前代码已经完成两件重要清理：

- retrieval brief 已收缩为 3 字段：
  - `queryText`
  - `keywordHints`
  - `difficultyHint`
- 旧 retrieval 字段和项目题技术钩子门槛已经从现行代码路径中删除

但当前检索主链路仍然是：

1. lexical 预过滤
2. dense 召回
3. rerank
4. 硬护栏

这条链路的问题已经足够明确：

- lexical 先裁剪 dense 的候选空间，会制造真实的 `Top-K` 盲区
- `keywordHints` 为空时，lexical 分支实际上退化为“没有术语路”
- dense 无法从被 lexical 预裁剪掉的题卡中补回语义相近候选

因此，当前链路不再适合作为推荐基线。

## 目标与非目标

### 目标

本次改造要实现：

1. retrieval brief 继续保持 3 字段，不恢复旧字段
2. dense 与 lexical/sparse 两条召回分支完全独立
3. lexical 主路切换为 Qdrant 原生 sparse/BM25 能力
4. 融合层使用 RRF
5. 首版就单独设计 sparse 文本，不偷懒复用当前唯一的 `retrieval_text`
6. 用新 collection 重建数据并切换，最后删除旧 collection

### 非目标

本次改造不做这些事：

- 不引入多意图检索
- 不保留旧链路的 feature flag 并行开关
- 不恢复 `goal / displayQuery / mustHaveClues / avoidClues`
- 不引入 Python 稀疏向量微服务
- 不把 payload full-text index 继续当 lexical 主方案

## 设计原则

### 1. 单意图，不做多意图检索

一轮面试下一题必须有单一主焦点。

因此：

- `retrievalPlans` 在协议层继续保持数组形态，仅为未来扩展预留
- 现阶段执行语义仍然是 `0 或 1 条`
- 不支持多个并列 retrieval plan 同时执行

### 2. 查询侧与文档侧分开设计

查询侧是 AI 生成的检索意图：

- `queryText`
- `keywordHints`
- `difficultyHint`

文档侧是题卡入库时的检索材料形态：

- dense 文本
- sparse 文本

这两层不能混为一谈。

### 3. 双路召回必须真正独立

双路召回的最低要求是：

- dense 分支不被 lexical 预裁剪
- sparse 分支不依赖 dense 先给候选
- 两条分支各自产生独立 Top K
- 融合发生在召回之后，而不是召回之前

### 4. 迁移必须可验证、可回滚、可删除旧逻辑

本次迁移不接受“兼容旧 collection 再慢慢替换”的拖尾做法。

迁移原则是：

1. 新建 collection
2. 全量重建数据
3. 代码切换到新链路
4. 验证通过
5. 删除旧 collection

## 目标架构

### 1. 上游决策与 retrieval brief

`evaluation_decision` 继续输出：

- `queryText`
- `keywordHints`
- `difficultyHint`

字段职责保持不变：

- `queryText`：dense 分支主查询，表达完整语义意图
- `keywordHints`：sparse 分支主锚点，仅包含正向术语词，可为空数组
- `difficultyHint`：排序软约束，不参与硬过滤

### 2. `RagPlanCompiler`

`RagPlanCompiler` 不再只是把 brief 映射成一个“单路检索请求”，而要在内部显式生成三类输入：

1. `denseQueryText`
2. `sparseQueryText` 或 `sparseAnchors`
3. `rerankBrief`

推荐编译规则：

- `denseQueryText <- queryText`
- `sparseAnchors <- keywordHints`
- `rerankBrief <- queryText + keywordHints + questionType + domainCode + focusPoint + difficultyHint`

约束：

- 不从 `focusPoint` 偷偷给 sparse 分支补词
- `keywordHints=[]` 时允许 sparse 分支查询为空，但链路仍需保持可执行
- `difficultyHint` 不下推为硬过滤条件

### 3. 文档侧：dense 文本与 sparse 文本分离

#### dense 文本

dense 文本继续承载完整语义材料，建议包含：

- `question_text`
- `intent_concept`
- `reference_context`
- `scoring_key_points`
- `keywords`

目标：

- 尽量保留语义上下文
- 让 embedding 能捕捉“问法不同但考点接近”的候选

#### sparse 文本

首版即单独设计 sparse 文本，不复用 dense 文本。

建议包含：

- `question_text`
- `keywords`
- 术语化后的 `intent_concept`
- 术语化后的 `scoring_key_points`

建议不直接纳入：

- 大段叙事型 `reference_context`
- 长篇说明性文字
- `scoring_pitfalls`

目标：

- 强化术语、缩写、组件名、机制名、故障名命中
- 避免 BM25 被长叙事文本稀释

### 4. Qdrant collection 设计

目标 collection 使用新名字，示例：

- `interview_knowledge_hybrid`

目标 schema：

- dense named vector
- sparse named vector
- 保留必要 payload metadata

首版要求：

- 新 collection 与旧 collection 并存直到验证完成
- 不在旧 collection 上原地硬改 schema
- 验证通过后删除旧 collection

### 5. 入库链路

入库链路需要从“单文本 + Spring AI VectorStore 自动写入”升级为可控制的 hybrid 入库。

目标能力：

1. 生成 stable point id
2. 生成 dense 文本
3. 生成 sparse 文本
4. 写入 dense vector
5. 写入 sparse vector
6. 写入 metadata

这里要特别注意：

- 当前 `KnowledgeIngestionService` 只会写一份 `Document(text, metadata)`
- 这不足以表达双文本、双向量需求
- 因此首版大概率需要绕开当前过于抽象的 `VectorStore.add(...)` 黑盒写法，直接控制 Qdrant 写入

### 6. 召回链路

目标链路：

1. 下推刚性边界过滤
2. dense 独立召回 Top K
3. sparse 独立召回 Top K
4. RRF 融合
5. rerank
6. 最终硬护栏

#### 前置硬边界

仅保留低误伤条件：

- `active=true`
- `questionType`
- 必要时的 `domainCode`

不允许把这些东西重新塞成高误伤前置裁剪：

- `difficultyHint`
- `keywordHints`

#### dense 分支

输入：

- `queryText`
- `queryText` 缺失时，才回退 `focusPoint`

输出：

- dense Top 20

#### sparse 分支

输入：

- `keywordHints`
- 通过编译器整理成 sparse query text

输出：

- sparse Top 20

技术路线：

- Qdrant 原生 sparse/BM25

#### 融合层

融合方式：

- RRF

原因：

- dense 与 sparse 分数空间不一致
- 首版先用 rank-based 融合，稳定且简单

#### rerank 层

继续保留商业 rerank，输入至少包含：

- `queryText`
- `keywordHints`
- `focusPoint`
- `difficultyHint`
- `questionType`
- 融合候选题卡

#### 最终硬护栏

即使进入双路召回，仍保留最终护栏：

- 行为题防技术污染
- 题型漂移拦截
- 明显 domain 错位拦截
- 项目题防退化成纯泛八股

## 迁移策略

### 推荐迁移路径

1. 增加新的 hybrid collection 配置项
2. 创建新 collection schema
3. 改造题卡文本构造与入库逻辑
4. 全量重建新 collection 数据
5. 改造检索服务为双路召回
6. 跑评测与回归
7. 切换生产/默认配置到新 collection
8. 删除旧 collection

### 为什么不用原 collection 原地升级

因为当前旧 collection 的前提是：

- 单 dense 文本
- 单向量主检索
- payload index 辅助 lexical 预过滤

直接原地升级会把这些动作绑在一起：

- schema 变更
- 数据重建
- 应用切换
- 回滚

风险过高，而且不利于验证。

## 需要人工执行的动作

这些步骤不应由代码“假实现”，而要在执行计划中明确标注为人工动作：

1. 核对本地 Qdrant 版本是否满足目标能力
2. 如当前版本不满足，手动升级本地 Qdrant
3. 按计划执行新 collection 创建
4. 验证通过后手动确认删除旧 collection

原因：

- 你已经要求安装和外部环境动作由你自己执行
- collection 删除属于不可逆操作，不能由代码静默代劳

## 主要风险与对应控制

### 1. Spring AI `VectorStore` 不能继续担当主实现

风险：

- 当前 `VectorStore` 过于偏单文本、单向量
- 可能不适合直接表达 named dense vector + sparse vector + 自定义 query 融合

控制：

- 检索与入库主路径必须改用 Qdrant Java Client
- `VectorStore.add(...)` 与 `similaritySearch(...)` 不再承担 hybrid 检索主职责
- Spring AI 仅保留业务层仍然合适的能力，例如 `EmbeddingModel`

### 2. sparse 文本设计不佳，导致 BM25 收益不明显

风险：

- 如果 sparse 文本仍然过长或术语密度不够，lexical 分支收益会被稀释

控制：

- 首版就把 sparse 文本和 dense 文本分离
- 评测中单独观察 sparse 命中率和 hybrid 命中率

### 3. 直接替换旧链路带来回归风险

风险：

- 不保留 feature flag，意味着切换后主路径全部走新链路

控制：

- 迁移前必须有新 collection 全量重建
- 测试必须覆盖：
  - 编译器
  - 入库
  - 双路召回
  - RRF 融合
  - rerank
  - 评测夹具

## 验收标准

本次设计对应的执行计划，至少要能验证这些结果：

1. retrieval brief 仍然只有 3 字段
2. 题卡入库已支持 dense 文本与 sparse 文本双表示
3. 新 collection 能完成全量重建
4. 检索主链路已替换为 `dense + sparse -> RRF -> rerank`
5. 当前旧 collection 不再作为默认检索目标
6. 评测至少能对比：
   - dense only
   - dense + sparse + RRF
   - dense + sparse + RRF + rerank
7. 验证通过后可删除旧 collection

## 结论

本次改造不是对当前链路做小修，而是明确替换推荐基线：

- 保留 3 字段 retrieval brief
- 保留单意图检索
- 题卡文档侧拆成 dense 文本和 sparse 文本
- Qdrant 侧新建 hybrid collection
- 检索侧切换为双路独立召回 + RRF + rerank
- 迁移完成后删除旧 collection

这份设计确认后，后续执行计划应围绕以下四条主线展开：

1. collection 与配置迁移
2. 文档构造与入库重建
3. 检索链路替换
4. 评测、回归与旧资源清理
