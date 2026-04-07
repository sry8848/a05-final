# 面试 RAG 检索查询视图分离设计

## 文档目的

本文定义当前面试 RAG 检索链路中查询输入的新边界，目标是把“业务约束字段”和“语义检索字段”物理拆开，避免它们继续在召回、rerank 和审计中互相污染。

本次设计覆盖两个连续改造点：

1. P0：收紧 rerank 输入边界，并把 `difficultyHint` 下沉为召回前结构化过滤。
2. P1：把检索输入拆成语义视图与词法视图，明确 `queryText` 与 `keywordHints` 的职责。

这不是一次“改几行字符串拼接”的局部修补，而是一次查询语义边界重划分。目标是让：

- dense 检索更像语义检索
- sparse/BM25 更像术语命中
- rerank 只处理相关性，不处理业务规则

## 当前问题

当前实现中，查询输入职责混杂，主要表现在两处：

### 1. rerank 输入混入了业务约束字段

当前 `DashScopeRagRerankService.buildQueryBrief(...)` 会把以下字段一起拼进 rerank query：

- `questionType`
- `denseQueryText / queryText`
- `sparseQueryText`
- `focusPoint`
- `keywordQueries`
- `difficultyHint`

这会带来两个问题：

1. `questionType` 与 `difficultyHint` 本质上是业务约束，不是语义相关性判断输入。
2. `sparseQueryText` 与 `keywordQueries` 属于术语锚点，不应继续和 dense/rerank 的语义视图混拼。

### 2. queryText 与 keywordHints 的职责没有被明确区分

当前 `RagPlanCompiler` 产出的执行视图是：

- `denseQueryText = queryText`
- `sparseQueryText = keywordHints join`

这一步本身没有错，但上游 `retrievalPlan.queryText` 的语义还不稳定：它既可能是自然语言句子，也可能只是词袋。

结果是：

- dense 检索有时拿到的是完整语义，有时拿到的是零碎术语串
- rerank 进一步把这些碎片和其他标签字段一起拼接，注意力被稀释
- `keywordHints` 是否为空，会直接影响 sparse 支路是否有意义，但这个行为边界没有明确写死

## 已确认的业务结论

以下结论已作为本次设计前提固定：

### 1. `questionType` 必须保留在召回与护栏，但退出 rerank

- `questionType` 继续用于 Qdrant 召回 filter
- `questionType` 继续用于后置硬护栏
- `questionType` 不再进入 rerank query 文本

### 2. `difficultyHint` 是“带扩窗的硬过滤”

它不是“只允许某一层级”的严格单点过滤，而是：

- 以 AI 输出层级为中心
- 向相邻一级扩窗
- 作为召回前结构化 filter 生效
- 不再进入 rerank query 文本
- 命中为空时不允许无难度回退重查

### 3. `difficultyHint` 只支持全局开关

本次设计只支持：

- 全局配置启用 / 关闭

不支持：

- 单请求覆盖
- 单场面试灰度
- 单题型差异化开关

## 目标与非目标

### 目标

本次设计确认以下目标：

1. `queryText` 成为独立、完整、自然语言的语义查询。
2. `keywordHints` 成为术语锚点，只服务 sparse/BM25。
3. dense 与 rerank 只消费 `queryText`。
4. `keywordHints` 为空时，sparse 支路直接跳过。
5. `questionType` 与 `difficultyHint` 不再进入 rerank query。
6. `difficultyHint` 在启用时转成召回前结构化 difficulty window filter。
7. difficulty filter 同时作用于 dense 与 sparse 两路召回。
8. difficulty filter 命中为空时不做回退重查。

### 非目标

本次设计不做这些事：

- 不改动题卡数据模型中的 `difficulty` 字段格式
- 不引入请求级 difficulty 开关
- 不在 P1 内实现 query rewrite 大模型服务
- 不增加“keywordHints 为空时，用 `queryText` 自动提取关键词”的新逻辑
- 不在本次设计中重做文档侧字段拼接策略
- 不改变题型护栏的现有业务含义

## 设计原则

### 1. 业务约束与语义相关性分离

`questionType` 与 `difficultyHint` 是业务规则，不是语义排序器应理解的语言内容。

因此：

- 它们应在结构化过滤和护栏层解决
- 不应继续以文本标签形式注入 rerank

### 2. 语义视图与词法视图分离

一次检索中可以同时存在两类查询视图：

- 语义视图：表达完整检索意图，服务 dense 和 rerank
- 词法视图：表达术语锚点，服务 sparse/BM25

两类视图可以来自同一个上游 retrieval plan，但不能再混为一个字符串垃圾桶。

### 3. sparse 支路是增强，不是强行补齐

`keywordHints` 为空并不代表整次检索不成立。

当 `queryText` 足够清晰，而 `keywordHints` 为空时：

- dense 仍可正常执行
- rerank 仍可正常执行
- sparse 直接跳过

这是一种显式边界，而不是失败后偷偷回退造词。

### 4. Query 质量比字段数量更重要

本次设计不追求“字段越多越强”，而是强调：

- `queryText` 本身要足够独立、可执行、语义完整
- `keywordHints` 要少而准，承担术语锚点职责

## 新边界定义

### 1. 上游 AI RetrievalPlan 契约

上游继续保留：

- `queryText`
- `keywordHints`
- `difficultyHint`

但字段语义重新定义为：

#### `queryText`

必须是：

- 独立的
- 完整的
- 自然语言的
- 可直接用于语义检索的查询句

不应是：

- 纯关键词堆砌
- 长 prompt
- 含大量业务元信息的标签串

#### `keywordHints`

职责是：

- 提供术语锚点
- 补强 sparse/BM25 命中能力

不应承担：

- dense 检索主查询
- rerank 文本主查询

### 2. 编译层：RagPlanCompiler

`RagPlanCompiler` 继续接收上游 retrieval plan，但执行视图明确为：

- `denseQueryText <- queryText`
- `sparseQueryText <- keywordHints join`

并明确：

- dense/rerank 只看 `queryText`
- sparse 只看 `keywordHints`
- `keywordHints` 为空时，`sparseQueryText` 为空，sparse 支路跳过

本次设计不要求 `RagPlanCompiler` 自行把词袋重写成自然语言句。

也就是说：

- 上游必须对 `queryText` 质量负责
- 编译层只做边界明确，不做智能补救

### 3. 召回层：QdrantHybridQueryExecutor

Qdrant 查询 filter 继续保留：

- `active=true`
- `question_type`

并新增可配置 difficulty window filter。

#### difficulty window 规则

开关开启时，按相邻一级扩窗：

- `L1 -> {L1, L2}`
- `L2 -> {L1, L2, L3}`
- `L3 -> {L2, L3, L4}`
- `L4 -> {L3, L4, L5}`
- `L5 -> {L4, L5}`

开关关闭时：

- 不追加任何 difficulty filter

该 filter 同时作用于：

- dense recall
- sparse recall

若过滤后两路命中都为空：

- 直接返回空结果
- 不做“关闭 difficulty 再查一次”的兜底

### 4. rerank 层：DashScopeRagRerankService

rerank query 不再包含：

- `questionType`
- `difficultyHint`

query 侧只保留真正参与语义判断的内容：

- `queryText`
- 如有必要，可保留 `focusPoint` 作为语义补充，但它不能替代 `queryText`

本次设计明确：

- `keywordHints` 不再作为标签串注入 rerank query
- `sparseQueryText` 不再注入 rerank query

### 5. 审计层：RetrievalAudit

为了让 difficulty filter 行为可观察，建议在审计中增加：

- `difficultyWindowApplied`
- `difficultyWindowValues`

否则后续排查 0 命中时，无法判断是：

- 召回本身失败
- 还是被 difficulty filter 截断

## 示例

### 正例 1：原理题

```json
{
  "queryText": "寻找考察 Redis 缓存穿透防护方案的题目，重点包含布隆过滤器、空值缓存和数据库保护。",
  "keywordHints": ["Redis", "缓存穿透", "布隆过滤器", "空值缓存"],
  "difficultyHint": "L2"
}
```

执行视图：

- dense/rerank：使用完整自然语言句
- sparse：使用 `Redis 缓存穿透 布隆过滤器 空值缓存`
- difficulty：过滤 `L1/L2/L3`

### 正例 2：场景题

```json
{
  "queryText": "寻找订单超时关闭场景下，考察幂等、事务状态机和 DB 与 MQ 顺序一致性的题目。",
  "keywordHints": ["订单超时关闭", "幂等", "DB+MQ", "顺序一致性"],
  "difficultyHint": "L3"
}
```

### 正例 3：行为题但无 keywordHints

```json
{
  "queryText": "寻找行为面试中考察与产品意见不一致时如何沟通、推进和复盘的题目。",
  "keywordHints": [],
  "difficultyHint": "L2"
}
```

执行视图：

- dense/rerank：正常执行
- sparse：跳过
- difficulty：过滤 `L1/L2/L3`

### 反例 1：词袋式 queryText

```json
{
  "queryText": "Redis 缓存穿透 布隆过滤器 空值缓存 数据库保护",
  "keywordHints": ["Redis", "缓存穿透", "布隆过滤器"],
  "difficultyHint": "L2"
}
```

问题：

- 语义主查询不完整
- dense 与 rerank 看到的是词堆，不是意图句

### 反例 2：长 prompt 式 queryText

```json
{
  "queryText": "请你帮我找一些适合 Java 后端初级工程师在面试中考察 Redis 缓存穿透原理、防护手段、业务降级方案以及是否能区分击穿和雪崩的题目，最好不要太难。",
  "keywordHints": ["Redis", "缓存穿透", "降级"],
  "difficultyHint": "L2"
}
```

问题：

- 混入了 prompt 语气与偏好描述
- 不像独立检索句

## 风险与权衡

### 1. 上游 `queryText` 质量不稳会直接伤 dense/rerank

本次设计故意不让编译层替上游“智能修复”。

优点是：

- 边界清楚
- 行为可解释

代价是：

- 上游 prompt 和契约必须足够明确

### 2. `keywordHints` 为空时 sparse 支路会直接丢失

这是本次设计明确接受的结果。

原因是：

- sparse 是术语增强，不是必须存在
- 比起偷偷回退造词，显式跳过更可解释

### 3. difficulty filter 可能降低召回率

这是业务明确接受的硬约束结果。

因为 `difficultyHint` 已被定义为：

- 带扩窗的硬过滤

而不是：

- 仅排序偏置
- 可在 0 命中时放宽

## 验收标准

设计落地后，至少应满足：

1. `queryText` 被文档和测试明确描述为自然语言语义查询。
2. `keywordHints` 被文档和测试明确描述为 sparse 术语锚点。
3. dense/rerank 不再消费 `keywordHints` 或 `sparseQueryText`。
4. rerank query 不再包含 `questionType`。
5. rerank query 不再包含 `difficultyHint`。
6. difficulty filter 支持全局配置开关。
7. difficulty filter 同时作用于 dense 与 sparse。
8. difficulty filter 命中为空时不发生无难度回退。
9. `keywordHints` 为空时，sparse 支路跳过，而不是回退到 `queryText` 造词。
10. 审计可明确看出本次是否应用了 difficulty window 以及应用了哪些层级。

## 结论

本次设计的核心不是“让 query 更像一句话”这么简单，而是明确三条边界：

1. 语义查询归 `queryText`
2. 词法锚点归 `keywordHints`
3. 业务约束归结构化 filter 与护栏

当这三条边界落地后：

- dense 检索会更像真正的语义检索
- sparse/BM25 会更像真正的术语召回
- rerank 会回到“相关性排序器”的职责范围
- difficulty 与题型规则也不再以文本噪音的方式污染排序
