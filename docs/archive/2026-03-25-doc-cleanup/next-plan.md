下面是结合“首题固定为自我介绍，后端题库 + AI 改写为最终目标方案”后的新版执行清单。管理端仍然全部搁置，只规划后端链路。

总体调整

原 C/D 保留，但要把 INTRO 专项能力纳入 Prompt 体系。
在原计划里插入一个新的“首题自我介绍变体引擎”批次。
原 E/F/G 顺延为 F/G/H，这样依赖关系更清晰。
批次 C：补齐剩余 AI 链路模板化，并为 INTRO 改写预留模板位
目标：把 evaluation_decision、report_generation、流式出题 system prompt 从硬编码改为模板加载；同时为后续“自我介绍 AI 改写”预留独立 prompt。
改动范围：OpenAiClient、prompts/*.md，新增：
evaluation-decision.md
report-generation.md
question-generation-stream.md
intro-rewrite.md 或 intro-question.md
产出：
4 条核心 AI 调用全部走 PromptTemplateService
INTRO 不再被视为普通 question_generation 的隐式分支，后续可单独控制
验收：
相关单测/契约测试通过
运行日志中可看到实际 promptCode/promptVersion
新增 INTRO 模板可被加载，但暂不接入业务主流程
批次 D：Prompt 版本配置化（覆盖 INTRO 专项 Prompt）
目标：把所有 prompt 版本从代码常量迁到配置，包含后续 INTRO 改写 prompt。
改动范围：application*.yml、新增 PromptProperties、OpenAiClient
建议配置项：
planner
question_generation
question_generation_stream
evaluation_decision
report_generation
intro_rewrite
产出：
每类 prompt 都可独立切版本
后续调优 INTRO 改写时不需要改代码
验收：
改配置可生效
版本不匹配时有明确失败信息
启动或调用日志能定位具体是哪类 prompt 配错
批次 E：首题自我介绍变体引擎（核心新增批次）
目标：实现“单场不重复、跨场尽量不重复”的首题自我介绍能力，采用“后端题库选底稿 + AI 改写”的两段式方案。
改动范围：
FirstQuestionGenerationService
新增 IntroQuestionStrategyService
新增用户历史 INTRO 查询能力
QuestionGenerationInput 或新增 IntroRewriteInput
InterviewQuestion.generationContextJson
MockAiClient
设计拆分：
第一步：后端题库选底稿
固定维护一组 INTRO 基础变体
优先选择该用户最近 N 场未使用过的变体
若都用过，选择“距离上次使用最久”的变体
第二步：AI 改写
输入：底稿、候选人岗位/年限/模式、历史禁用措辞、避免重复的最近若干 INTRO 题干
输出：更自然的首题话术
建议新增记录：
variantId
basePromptText
rewritten=true/false
historyAvoidCount
rewritePromptCode/rewritePromptVersion
产出：
首题固定为 INTRO
单场不会重复
跨场对同一用户尽量不重复
即使 AI 改写失败，也能回退到底稿，不阻塞开场
验收：
同一场面试只会出现 1 次 INTRO
同一用户连续创建多场面试时，首题底稿按策略轮换
AI 改写失败时仍能返回可用首题
generationContextJson 可追溯本次用了哪个变体
批次 F：调用审计与可观测性增强
目标：把 prompt 与 INTRO 变体元信息落库，方便回归分析和效果追踪。
改动范围：ai_invocation_log 表结构、实体/Mapper/Service、记录逻辑
建议字段：
prompt_code
prompt_version
model
temperature
template_hash（可选）
business_stage（建议，区分 planner/question/intro_rewrite/report）
variant_id（建议，给 INTRO 改写链路用）
产出：
每次 AI 调用都有完整元数据
可按 prompt_code + version + variant_id 查效果
验收：
首题生成链路能区分“题库选底稿”和“AI 改写”两段日志
查询某个用户最近若干场 INTRO 变体使用情况可行
批次 G：测试防线加固
目标：防止模板变量变更、INTRO 变体策略变更导致运行时缺参或重复回归。
改动范围：新增模板测试、策略测试、回归测试
建议测试：
扫描模板占位符，校验变量构造器覆盖完整
版本配置与模板头 promptVersion 一致性校验
INTRO 变体选择策略测试
同用户跨场轮换测试
AI 改写失败回退测试
planner + question + intro-rewrite 的黄金样例测试
产出：
模板改坏、缺参、错版、INTRO 重复都能被测试拦住
验收：
mvn test 全绿
新增测试能明确拦截：
缺变量
版本错配
同用户连续场次重复选同一底稿
改写失败但未回退
批次 H：文档与发布准备
目标：把运行机制、配置方式、排障路径和 INTRO 变体策略文档化。
改动范围：技术文档、发布说明、数据库变更说明
产出：
Prompt 运行时架构说明
Prompt 配置清单与示例
INTRO 首题策略说明
数据库变更与回滚说明
常见故障排查：
模板缺参
版本不匹配
AI 改写失败回退
用户历史查询异常
验收：
新同学可按文档完成本地联调
能独立定位首题为什么用了某个变体、为什么触发了回退
