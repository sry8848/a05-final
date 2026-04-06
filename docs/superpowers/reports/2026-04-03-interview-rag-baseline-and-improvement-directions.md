# 面试 RAG 基线与改进方向

## 文档目的

本文用于统一当前面试 RAG 的团队认知，并作为下一阶段方案评审与迭代的基线文档。

- 本文不是执行计划，不使用 `Chunk / Task / Step / Run / Expected` 体例。
- 本文基于当前已经落地的单库题目卡片、真实检索、真实 rerank 和出题注入链路，总结哪些结论稳定，哪些方向应继续优化。
- 本文会吸收早期方案中仍然有效的背景和术语约束，但检索架构、AI brief 字段和评测口径以本文当前结论为准。
- 本文应可单独阅读，不依赖旧文档补充上下文。

当前技术栈口径：

- Java 21
- Spring Boot / Spring AI
- Qdrant
- DashScope OpenAI-compatible Embedding
- DashScope 文本排序 API

## 当前推荐基线一句话结论

当前面试 RAG 的下一阶段推荐基线是：

**单库题目卡片 + 单套 Qdrant + dense 与 lexical/sparse 双路独立召回 + RRF 融合 + 商业 rerank 主排序 + 程序硬护栏 + Top N 结果注入出题链路。**

这条链路服务的对象包括：

- 理论题
- 场景题
- 行为题
- 带明确技术钩子的项目深挖题

## 背景与问题定义

这套 RAG 的目标，不是做一个通用知识问答系统，而是为面试出题链路提供更稳定、更可控、更贴合题型语义的题目素材。

它需要同时解决几类问题：

- 不能只有 `retrievalPlans` 这种“检索意图提示”，而没有真实检索结果。
- 不能把理论题、场景题、行为题、项目题全部混成一套宽泛检索逻辑。
- 不能让项目题退化成泛八股，也不能让行为题被技术材料污染。
- 不能为了追求术语完整性，引入第二套搜索系统或明显超出当前收益的复杂架构。

当前阶段的核心判断已经变化：

- 单纯把 lexical 作为 dense 的预过滤，会真实压缩 dense 的候选空间，并带来 Top-K 盲区。
- 更合理的方式是在同一业务边界内，让 dense 与 lexical/sparse 两路独立召回，再融合、精排和护栏收口。
- 对本地运行的 Java + Qdrant 项目，第一步优先采用 Qdrant 开源版 BM25 sparse 方案即可，不需要为了 sparse 能力先迁云，也不需要第一步就引入 Python 微服务。

## 关键术语与业务边界

### 1. `retrievalPlans` 与 `retrievedMaterials`

- `retrievalPlans` 是上游决策模型输出的检索 brief，用来表达这一轮如果需要检索，想找什么材料。
- `retrievedMaterials` 才是真实命中的题目卡片结果，是下游出题模型优先消费的内容。
- `retrievalPlans` 不是“已经检索到了什么”，只能作为检索编译和审计输入，不能冒充真实上下文。

当前 `retrievalPlans` 的语义字段为：

- `queryText`
- `keywordHints`（可空）
- `difficultyHint`

三者的职责必须明确：

- `queryText`：dense 分支主查询，表达完整自然语言语义。
- `keywordHints`：lexical/sparse 分支主锚点，表达正向技术词、缩写、组件名、机制名和故障名。
- `difficultyHint`：排序软约束，只参与融合和 rerank 的偏置，不做硬过滤。

程序也要进行相应修改
### 2. 三层难度口径

当前难度语义只允许使用下面三层，不再混用旧术语：

- `experienceLevel`：候选人的资历层级，只影响节奏、题型配额、默认深度包络和排序偏置。
- `difficulty`：题目卡片本身的认知深度，唯一合法取值为 `L1-L5`。
- `difficultyHint`：当前这一轮希望问到的目标深度提示，只用于检索编译和排序参考，不是硬过滤条件。

`difficulty` 的业务解释为：

- `L1`：定义和基础概念
- `L2`：原理和常见用途
- `L3`：结合具体场景分析或实现
- `L4`：取舍、排障、优化、深度原理
- `L5`：复杂系统设计或架构判断

### 3. 单库题目卡片边界

当前知识组织方式已经固定为“单库题目卡片”，不再拆成 `QUESTION_PATTERN` / `KNOWLEDGE_EVIDENCE` 两套语料。

单张题目卡片至少包含这些业务字段：

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

其中：

- `retrieval_text` 是内部检索拼接字段，用于统一构造 dense embedding 和通用检索正文。
- `question_text` 不能单独承担检索职责，`retrieval_text` 至少应覆盖题面、考点、参考语境和评分要点。
- `follow_up_ids` 是增强项，只用于补充候选追问，不是主检索入口，也不是强制跳题规则。

第一版双路召回允许 dense 与 sparse 共用同一份 `retrieval_text` 做验证；如果后续评测显示 BM25 被长叙事稀释，再考虑为 sparse 单独派生更偏术语化的检索文本。

### 4. 题型路由边界

当前检索路由必须遵守下面的业务边界：

- `PRINCIPLE`：允许检索题目卡片。
- `SCENARIO`：允许检索题目卡片。
- `BEHAVIORAL`：允许检索题目卡片，但目标是行为面试素材，不能被技术知识污染。
- `PROJECT_DEEP_DIVE`：只有在 `focus` 或 `retrievalPlans` 明确包含技术钩子时才进入检索。

这里的“技术钩子”是刚性边界。泛项目叙述、泛项目介绍、没有明确技术问题指向的项目题，不应该为了“看起来更智能”而强行发起检索。

### 5. 行为题领域编码边界

当 `BEHAVIORAL` 题卡不绑定技术知识域时，`domainCode` 应输出空字符串 `""`，不能使用 `behavior`、`behavioral` 之类的伪编码代替。

## 当前推荐架构基线

### 1. 上游决策仍负责“是否值得检索”

`evaluation_decision` 仍然负责决定下一题的 `focus`，并产出 `retrievalPlans`。

它的职责是：

- 识别当前问题类型和追问目标
- 给出最小可编译的检索 brief
- 不把 brief 伪装成真实命中材料

上游 AI brief 的目标是表达检索意图，而不是输出完整的检索执行 DSL。

### 2. 检索计划编译是独立一层

`RagPlanCompiler` 负责把 `retrievalPlans` 编译为真正可执行的检索请求，并在这一层做业务判定：

- 当前这一题是否应该检索
- 项目题是否存在明确技术钩子
- `queryText` 应如何编译为 dense 查询
- `keywordHints` 应如何编译为 lexical/sparse 查询锚点
- `difficultyHint` 如何只作为排序偏置参与融合和 rerank
- 哪些场景应该直接返回 `shouldRetrieve=false`

这一步的职责是收窄与编译，而不是发明宽泛 query 去硬凑检索。

### 3. 在开头做硬过滤

第一层是检索前和分支内的刚性边界：

- `shouldRetrieve`
- `active=true`
- `domainCode`
- 项目题技术钩子是否存在

必须明确：

- `difficultyHint` 不参与硬过滤
- `keywordHints` 不是硬过滤条件，而是 lexical/sparse 查询锚点

### 4. dense 分支负责完整语义召回

dense 召回仍然是当前方案的主语义召回路径。

它负责解决的问题是：

- 用户问法和题卡措辞不一致
- 问题表述是场景化的，但底层考点一致
- 项目题技术钩子和题库卡片存在语义近义关系

dense 分支的输入规则应固定为：

- 主输入是 `queryText`
- 缺失时才回退到 `focusPoint`
- 不拼负向词
- 不把一长串锚点词直接堆成 dense 查询

当前结论不是“dense 完美”，而是“dense 仍然值得保留为主召回路径，不需要推翻重做”。

### 5. lexical/sparse 分支负责术语锚点召回

lexical 分支的正确定位，不是 dense 的预过滤，而是独立召回分支。

当前推荐方案是：

- 使用 Qdrant 开源版 BM25 sparse vector
- 与 dense 在同一业务边界内独立召回
- 每路各取 Top 20 左右，再做融合

lexical/sparse 分支的输入规则应固定为：

- 主输入是 `keywordHints`
- 只允许正向锚点词
- 重点放技术术语、缩写、组件名、机制名、故障名和必要别名
- 不放负向词
- 不放大段自然语言解释

行为题允许 sparse 分支很弱，甚至在锚点质量不足时退化为空；行为题的主稳定性来自题型护栏和 dense 语义，而不是强行构造技术 lexical 查询。

### 6. 融合采用 RRF，而不是 lexical 先裁剪 dense

双路召回的推荐融合方式是 RRF。

原因是：

- dense 与 sparse 的原始分值空间不同，不应直接比较绝对分
- RRF 更适合作为第一版稳定融合策略
- 它能避免 lexical 先把 dense 召回空间压死

因此当前推荐顺序是：

1. 前置刚性边界
2. dense 与 sparse 双路独立召回
3. RRF 融合
4. rerank
5. 最终护栏
6. Top N 注入

### 7. 商业 rerank 作为主排序器

对所有已经进入检索链路的请求，商业 rerank 仍然是默认主排序方案。

但 rerank 的职责也应收敛：

- 输入不是全库，而是 dense 与 sparse 融合后的轻量候选集
- query brief 以 `queryText + keywordHints + 程序上下文` 为主
- `difficultyHint` 只作为排序偏置，不做强裁剪

documents 侧至少要带：

- `questionText`
- `intentConcept`
- `referenceContext`
- `scoringKeyPoints`

这一步的核心目标仍然是提升最终排序质量，而不是继续堆本地关键词规则分。

### 8. 程序规则收缩为硬护栏，而不是自由文本负约束

程序规则仍然必须存在，但职责已经收缩成硬护栏，而不是主排序器。

当前必须保留的护栏包括：

- `questionType` 不串题
- 行为题不被技术污染
- 泛项目题不误触发检索
- 技术钩子型项目题不丢项目锚点
- rerank 失败时存在本地回退链路

当前已经明确不再采用的方式是：

- 把 lexical 当作 dense 的前置主裁剪器
- 把 `difficultyHint` 当等值过滤条件
- 把 AI 输出的自由文本负词当成检索层主逻辑
- 把污染控制责任甩给生成模型自行处理

### 9. 出题链路优先消费真实题卡结果

当前出题链路已经接入真实 `retrievedMaterials`，而不是只看 `retrievalPlans`。

下游使用原则是：

- 有真实 `retrievedMaterials` 时，优先基于真实题卡出题
- `retrievalPlans` 继续保留，用于审计和必要的弱提示
- 无检索结果或不适用检索时，仍允许根据项目上下文或面试上下文自然追问

## 明确不采用的路线

下面这些方向，当前基线已经排除，不应在没有新证据的前提下反复回摆：

### 1. 不引入 Elasticsearch

- 当前目标不是扩建第二套搜索系统
- 不能为了“BM25”三个字就引入新的基础设施复杂度

### 2. 不把 payload full-text index 当成 lexical 主方案

- payload text index 更适合辅助过滤、phrase 调试或约束条件
- 它不是当前推荐的 lexical 主召回方案
- 当前推荐 lexical 主路应是 sparse/BM25

### 3. 不把 Python 稀疏向量微服务作为第一步

- 当前本地 Java + Qdrant 架构可以先验证 BM25 sparse 路线
- Python 微服务应视为 BM25 实测不达标后的升级选项，而不是起步前提

### 4. 不要求先迁 Qdrant Cloud 才能做 sparse

- 当前第一步不要求迁云
- 云端能力可以作为未来升级选项，但不是当前落地前提

### 5. 不再把 lexical prefilter 当主架构

- lexical 可以保留辅助过滤能力
- 但主架构不再是 `lexical prefilter -> dense`
- 当前推荐主架构是 `dense + sparse` 双路独立召回

### 6. 不让 `difficultyHint` 参与硬过滤

- `difficultyHint` 只表达当前轮的目标深度提示
- 它不应成为硬性筛选条件

## 当前稳定结论

下面这些结论，当前可以视为团队基线，而不是待验证假设：

1. 单库题目卡片路线成立，当前不需要恢复成多语料分库设计。
2. `retrievalPlans` 和 `retrievedMaterials` 必须严格区分，不能再混淆。
3. dense 主召回仍是当前最值得保留的语义召回路径。
4. lexical/sparse 的正确定位是独立召回，而不是 dense 的前置主裁剪。
5. `queryText + keywordHints + difficultyHint` 足以组成 AI 侧最小检索 brief。
6. 商业 rerank 值得作为进入检索链路请求的默认主排序器。
7. 程序硬护栏不是可选项，尤其是行为题和项目题。
8. `follow_up_ids` 只能做增强，不应主导整个出题链路。

## 当前主要问题与瓶颈

虽然当前链路已经从“只有检索意图、没有真实材料”进化为真实可用链路，但下一阶段仍有几个明确瓶颈。

### 1. 双路 query 构造是否足够清晰

真正需要稳定下来的，不是“字段数量多不多”，而是不同阶段到底吃什么输入。

需要持续验证的重点包括：

- `queryText` 是否稳定表达 dense 所需的完整语义
- `keywordHints` 是否稳定表达 sparse 所需的正向术语锚点
- 是否仍有人把负向提示词、解释句或大段自然语言塞进 lexical 查询

### 2. BM25 sparse 在本地 Qdrant 下的术语命中质量

本地 Qdrant + BM25 sparse 是当前推荐的第一步，但中文技术词和中英混合术语仍需要实测。

重点关注：

- 中文技术术语能否稳定命中
- 中英混合缩写能否稳定命中
- 行为题是否会因为词面相似而被误伤

### 3. rerank 是否能稳定利用 `queryText + keywordHints + 程序上下文`

rerank 仍然是排序质量的核心瓶颈。

主要关注点包括：

- rerank brief 是否足够表达当前问题意图
- `keywordHints` 是否真正增强了排序，而不是制造词面噪声
- rerank 超时、失败或退化时，本地回退是否足够稳

### 4. 技术钩子型项目题仍然容易发生八股漂移

项目题最常见的退化方式，不是完全没检索，而是：

- 明明应该围绕候选人的真实项目经历深挖
- 最后却被拉回成一套通用知识题

因此项目题的核心要求仍然是：

- 明确技术钩子
- 保住项目锚点
- 让检索服务于项目追问，而不是反过来吞掉项目上下文

### 5. 行为题仍然需要零容忍污染控制

行为题不应该为了让 lexical 路“看起来也有价值”而强行技术化。

下一阶段重点不是提升行为题的技术术语命中率，而是保证：

- 路由正确
- 护栏严格
- 结果不被技术题污染

### 6. 评测样本和人工抽样仍然需要持续扩充

当前链路已经可以用真实评测和人工回放做验证，但这套验证能力还需要继续补强。

重点包括：

- 扩充真实日志样本
- 提高负样本覆盖
- 明确不同失败是路由问题、召回问题、融合问题、排序问题还是护栏问题

## 评测与审计口径

### 1. 评测必须继续存在

后续任何优化都不应该只靠“感觉更智能”来判断，而应继续落在统一评测口径上。

当前应持续关注的指标包括：

- `routingAccuracy`
- `retrievalApplicableHitRate`
- `denseOnlyHitRate`
- `hybridRrfHitRate`
- `rerankTop3HitRate`
- `behavioralPollutionRate`
- `projectAnchorRetentionRate`

如果后续要继续细分，也应明确区分：

- dense 查询构造问题
- sparse 查询构造问题
- RRF 融合问题
- rerank 主排序问题
- 硬护栏失效问题

### 2. 最小审计字段

当前检索链路至少应稳定记录这些审计字段：

- 是否触发检索
- dense 候选数
- sparse 候选数
- fusion 后 top ids
- rerank 前 top ids
- rerank 后 top ids
- 最终注入题卡 ids

这些字段的作用包括：

- `questionGenerationInput` 调试快照
- 问题回放排查
- 人工抽样核查

### 3. 人工抽样建议

人工抽样仍然是必要补充，尤其适合发现“看起来指标没坏，但体验已经漂了”的问题。

建议抽样至少覆盖：

- 理论题
- 场景题
- 行为题
- 技术钩子型项目题
- 泛项目负样本

每条样本至少核对：

1. 是否触发检索
2. 审计字段是否合理
3. 注入题卡是否合理
4. 最终问题是否更自然
5. 是否出现八股漂移
6. 是否出现技术污染

## 结论

当前面试 RAG 的团队基线已经清晰：

- 题库组织上，采用单库题目卡片
- 检索路径上，采用 dense 与 lexical/sparse 双路独立召回
- 融合路径上，采用 RRF 做第一版稳定融合
- 排序路径上，采用商业 rerank 主排序
- 风险控制上，依赖前置刚性边界与最终程序硬护栏
- 消费方式上，由出题链路优先消费真实 `retrievedMaterials`

后续新方案不应再回到“多套搜索系统”“lexical 先裁剪 dense”“把 `difficultyHint` 当硬过滤”“依赖 AI 输出大量自由文本负约束”这些已被排除的路线，而应在当前基线上继续提升 query 构造质量、BM25 sparse 命中质量、项目题锚点保护和评测可解释性。
