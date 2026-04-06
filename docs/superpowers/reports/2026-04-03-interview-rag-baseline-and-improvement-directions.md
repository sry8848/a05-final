# 面试 RAG 基线与改进方向

## 文档目的

本文用于统一面试 RAG 的下一阶段推荐基线，并作为后续改造与评审的共同起点。

- 本文描述的是**推荐方案**，不是当前代码的逐行镜像。
- 本文重点回答四件事：检索意图怎么表达、题卡材料怎么组织、双路召回怎么跑、哪些路线明确不采用。
- 本文可单独阅读，不依赖旧版计划文档。

## 推荐基线一句话

下一阶段推荐基线是：

**单库题目卡片 + 单意图检索 + dense 与 lexical/sparse 双路独立召回 + RRF 融合 + 商业 rerank 主排序 + 程序硬护栏 + Top N 题卡注入出题链路。**

也就是：

- dense 分支负责语义召回
- lexical/sparse 分支负责术语锚点召回

## 背景与问题定义

这套 RAG 不是通用问答系统，而是服务端驱动的题库检索增强链路。

服务端要解决的不是“用户刚刚问了什么”，而是：

- 这一轮想确认什么能力
- 应该问成哪种题型
- 需要保住哪些术语锚点
- 如何从题库里选出更像真实面试官会问的问题

当前问题已经很清楚：

- 单纯用 lexical 先裁剪 dense，会真实压缩 dense 的候选空间
- `Top-K` 盲区会让很多本该由 dense 补回来的好题，连进入候选集的机会都没有
- 所以 lexical 不能再担当 dense 的前置主裁剪器

因此，下一阶段推荐方案不是：

- `lexical prefilter -> dense -> rerank`

而是：

- `dense branch + lexical/sparse branch -> RRF -> rerank -> hard guardrails`

## 关键约束

### 1. 单意图，不做多意图检索

当前推荐的是：

- retrieval plan是个数组，这是未来保证未来扩展性，但现阶段强制要求不超过1个retrieval plan

不是：

- 多个并列 retrieval plan 同时执行
- 多个不相关意图一起召回

原因很直接：

- 面试下一题天然应该有一个主焦点
- 多意图会提高召回量，但会伤害聚焦度、可解释性和最终出题质量

### 2. retrieval brief 继续保持 3 字段

AI retrieval brief 继续保持最小集合：

- `queryText`
- `keywordHints`
- `difficultyHint`

它们的职责必须固定：

- `queryText`：完整语义意图，给 dense 分支使用
- `keywordHints`：正向术语锚点，给 lexical/sparse 分支使用，可为空数组
- `difficultyHint`：目标深度提示，只做排序软约束，不做硬过滤


### 3. 单库题目卡片继续保留

下一阶段不重回多语料分裂设计，继续使用单库题目卡片。

单张题卡至少包含：

- `id`
- `question_text`
- `intent_concept`
- `reference_context`
- `scoring_key_points`
- `scoring_pitfalls`
- `follow_up_ids`
- `domainCode`
- `question_type`
- `difficulty`
- `keywords`
- `source`
- `active`
- `version`

这意味着：

- 主材料不是纯问题文本
- 也不是纯知识片段
- 而是可检索、可 grounding、可出题的题目卡片

## 双路召回方案

### 1. 上游仍然由服务端决定检索意图

`evaluation_decision` 仍负责：

- 决定下一题大方向
- 判断这一轮是否值得检索
- 输出 retrieval brief

它不负责：

- 直接做数据库级 DSL
- 直接给出最终排序结果

### 2. `RagPlanCompiler` 负责把 3 字段编译成两条查询分支

编译器的职责应收敛为三件事：

1. 生成 dense 查询
2. 生成 lexical/sparse 查询
3. 生成 rerank brief

推荐编译规则：

- `denseQueryText <- queryText`
- `lexicalAnchors <- keywordHints`
- `rerankBrief <- queryText + keywordHints + questionType + domainCode + focusPoint + difficultyHint`

这里要明确：

- 不要再从 `focusPoint` 偷偷给 lexical 分支造词
- `keywordHints=[]` 时，允许 lexical 分支为空
- `difficultyHint` 不要在编译层变成硬过滤条件

### 3. 前置硬边界只保留低误伤条件

双路召回前，只下推低误伤、刚性的业务边界：

- `active=true`
- `questionType`
- `domainCode`

必要时也可以包含：

- 行为题 `domainCode=""`
- 题型映射，如 `PROJECT_DEEP_DIVE -> PROJECT`

不应前置成硬过滤的东西：

- `difficultyHint`
- `keywordHints`
- 任何自由文本负向约束

### 4. dense 分支

dense 分支的目标是：

- 找到与检索意图语义接近的题卡
- 补回“词不一样但意思一样”的候选

输入要求：

- 只吃 `queryText`
- `queryText` 缺失时才回退到 `focusPoint`
- 不堆关键词，不拼负向词

输出建议：

- 独立召回 `Top 20`

### 5. lexical/sparse 分支

lexical 分支的目标是：

- 精确捞出术语锚点相关题卡
- 补足 dense 对专有词、缩写、机制名不够敏感的部分

输入要求：

- 只吃 `keywordHints`
- 只允许正向术语锚点
- 可以为空

词类型建议包括：

- 技术名词
- 组件名
- 缩写
- 故障名
- 机制名
- 必要别名

输出建议：

- 独立召回 `Top 20`

### lexical 的推荐技术路线

下一阶段推荐 lexical 主路使用：

- `Qdrant sparse / BM25`

不推荐把下面这些当主 lexical 方案：

- payload full-text filter
- 手工 clue 打分主排序
- 继续把 lexical 当作 dense 的前置筛子

### 6. 融合层使用 RRF

dense 和 sparse 的分数空间不同，不应该直接比较绝对分值。

推荐第一版融合方式：

- `RRF`

原因：

- 简单
- 稳定
- 适合两路独立召回后的第一版融合

推荐顺序：

1. dense Top 20
2. sparse Top 20
3. RRF 融合
4. 得到融合候选集

### 7. rerank 继续保留

RRF 负责把两路候选拉到一起，但不负责最终最优排序。

因此下一阶段仍应保留商业 rerank，作为主排序器。

rerank 输入建议：

- `questionType`
- `queryText`
- `focusPoint`
- `keywordHints`
- `difficultyHint`
- 融合后的候选题卡

rerank 的职责是：

- 按当前检索意图重新排序
- 提升 Top N 质量
- 降低 dense 和 sparse 单独偏科的问题

### 8. 最终硬护栏

无论前面怎么召回和排序，最后都必须有硬护栏。

当前推荐保留：

- 行为题不能混入技术题
- 非行为题不能错题型漂移
- `domainCode` 明显冲突的候选要剔除
- 项目题不能退化成完全脱离项目上下文的泛八股

这里的原则是：

- 强约束尽量前置
- 但最终仍要保留一次后置硬兜底

## 当前推荐的数据与查询形态

### 1. 文档侧

第一版可以继续共用一份题卡检索正文，例如：

- `retrieval_text = question_text + intent_concept + reference_context + scoring_key_points + keywords`

但要明确：

- 这是第一版折中
- 如果后续评测发现 sparse 被长叙事稀释，再拆出单独的 sparse 文本

### 2. 查询侧

查询侧不再是“一份 query 通吃所有阶段”，而是至少要拆成：

- `denseQueryText`
- `lexicalAnchors`
- `rerankBrief`

尽管 AI 只输出 3 字段，但程序内部不应该继续把三层职责混成一团。

## 明确不采用的路线

下面这些路线，下一阶段不建议采用：

### 1. 不把 lexical prefilter 当主链路

不再采用：

- `lexical -> dense` 作为推荐目标架构

它可以是当前过渡实现，但不是推荐基线。

### 2. 不把 payload full-text filter 当 lexical 主方案

payload full-text 更适合：

- 辅助过滤
- phrase 调试
- 局部约束

它不是推荐的独立 lexical 召回主路。

### 3. 不把 `difficultyHint` 当硬过滤

`difficultyHint` 只做软偏置。

### 4. 不恢复旧 retrieval 字段

不恢复：

- `goal`
- `displayQuery`
- `mustHaveClues`
- `avoidClues`

这些字段已经被证明会制造语义重叠和职责漂移。

### 5. 不第一步就引入 Python 稀疏向量微服务

如果后续要上 neural sparse，可以再评估。

但第一步不需要为了 lexical 分支就先搭 Python 服务。

## 与当前实现的差异

这部分必须写清楚，否则文档会误导团队。

当前代码现状仍然是：

- lexical 预过滤
- dense 召回
- rerank
- hard guardrails

当前代码**还没有**完全落地：

- dense + sparse 双路独立召回
- RRF 融合
- BM25 sparse 主 lexical 路

所以这份文档表达的是：

- **下一阶段推荐基线**

而不是：

- 当前代码已经全部完成

## 下一阶段最值得验证的问题

### 1. 双路召回是否真的比当前单路链路更好

要验证的不是概念，而是：

- target card 是否更稳定进入 Top 20
- dense 是否少被 lexical 误伤
- rerank 之后 Top N 是否更自然

### 2. `keywordHints` 是否足以支撑 lexical/sparse 分支

要重点观察：

- principle / scenario / project 题是否能稳定给出高价值锚点
- behavior 题是否能允许空关键词而不被误伤

### 3. 项目题是否会继续八股漂移

项目题最危险的退化仍然是：

- 明明应该围绕真实项目追问
- 最后却被拉回成纯知识问答

双路召回能否缓解这点，需要评测，而不是想当然。

## 评测与审计口径

下一阶段评测至少要能对比三种路径：

1. dense only
2. dense + sparse + RRF
3. dense + sparse + RRF + rerank

建议持续记录的审计字段：

- `retrievalTriggered`
- `denseCandidateCount`
- `sparseCandidateCount`
- `fusionTopQuestionIds`
- `rerankPreTopQuestionIds`
- `rerankPostTopQuestionIds`
- `injectedQuestionIds`

建议重点看这些指标：

- `routingAccuracy`
- `retrievalApplicableHitRate`
- `denseOnlyHitRate`
- `hybridHitRate`
- `rerankTop3HitRate`
- `behavioralPollutionRate`
- `projectAnchorRetentionRate`

## 结论

这份文档要表达的核心不是“当前代码怎样”，而是“下一阶段应该往哪条线上收敛”。

当前推荐路线已经很明确：

- 继续保留单库题目卡片
- 继续保留 3 字段 retrieval brief
- 放弃 lexical 先裁剪 dense 的推荐口径
- 改成单意图、双路独立召回
- 用 RRF 做第一版融合
- 用商业 rerank 做最终主排序
- 用程序硬护栏做最后兜底

如果后续方案继续演进，也不应再回到旧字段恢复、技术钩子词表门槛、payload full-text 充当主 lexical 召回这些已经被排除的方向。
