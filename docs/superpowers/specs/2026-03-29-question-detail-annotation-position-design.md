# 问答详情页原文定位批注设计

- 日期：2026-03-29
- 范围：单题复盘批注链路（Prompt、后端 DTO/定位、前端渲染）
- 目标：从“模型直接返回可渲染片段”升级为“模型返回原文证据，后端定位到原答案，前端按位置高亮”，支持整句或短句级批注，同时保留未标注正文。

## 背景与现状问题

当前批注实现将模型返回的 `highlightedSegments` 直接作为前端渲染内容：

1. 后端协议只有 `{segment,label,comment}`，没有原文位置。
2. 前端详情页有批注时，不再以 `userAnswer` 为主，而是直接循环渲染这些片段。
3. 因为前端无法补全文，模型倾向于返回整句甚至更长片段，避免丢失上下文。

结果是：

1. 页面表现接近“红绿文本块重组答案”，而不是“在原答案上做批注”。
2. 未命中的正文没有天然位置，只能依赖模型顺手返回。
3. 想做到“只划关键句/短句，其他文本保持原样”会非常困难。

## 设计目标

1. 前端始终渲染用户原始回答全文，而不是用模型片段替代正文。
2. 批注支持整句或短句级高亮，未来可继续收敛到关键词级。
3. 模型不负责计算字符坐标，避免直接输出不稳定的 `start/end`。
4. 后端负责把模型返回的原文证据定位到答案文本中。
5. 对重复单词或重复短句引发的歧义，系统应宁可少标，不可错标。
6. 长答案不应继续撑高详情卡片，正文区域应使用固定窗口高度配合内部滚动。

## 方案结论

采用“`quote -> 后端定位 -> 前端按坐标切片`”方案。

职责划分：

1. Prompt / 模型：返回原答案中的证据引用 `quote`，附带 `label/comment`。
2. 后端：将 `quote` 定位到 `userAnswer` 中，生成 `start/end` 坐标。
3. 前端：按 `userAnswer + highlightedAnnotations` 切片渲染。

## 新数据结构

### 1. 模型原始输出

模型不再输出前端可直接渲染的整段片段，而是输出可定位证据：

```json
{
  "quote": "尽量消除共享，必须共享时确保原子性",
  "label": "strength",
  "comment": "直接点出了并发设计的核心原则"
}
```

### 2. 后端对外出参

在 `QuestionDetailEvaluationOutput` 中新增：

```java
public static class HighlightedAnnotation {
    private Integer start;
    private Integer end; // end exclusive
    private String quote;
    private String label; // strength / weakness
    private String comment;
}
```

新增字段：

```java
private List<HighlightedAnnotation> highlightedAnnotations;
```

说明：

1. `quote` 保留，便于调试和前端兜底展示。
2. `start/end` 是最终前端渲染的权威定位信息。
3. 旧字段 `highlightedSegments` 暂时保留，作为历史数据兼容路径。

## Prompt 设计要求

`question-detail-evaluation.md` 中的高亮规则需要改成“证据引用规则”，核心约束如下：

1. `quote` 必须是回答中的连续原文，不得改写。
2. 优先输出短句或短短语，不要输出整段回答。
3. 单条 `quote` 建议控制在 6~30 个字符；确有必要可以是整句，但不能是大段长文本。
4. 尽量不要只输出容易重复的单个词。
5. 如果必须使用可能重复的短词，应主动带一点上下文，提升后端唯一定位成功率。
6. 如果拿不准原文证据，宁可不输出，也不要编造或改写。

示例：

优先：

```json
{ "quote": "尽量消除共享，必须共享时确保原子性", "label": "strength", "comment": "..." }
```

不优先：

```json
{ "quote": "CAS", "label": "strength", "comment": "..." }
```

可接受的折中：

```json
{ "quote": "利用底层的 CAS 指令", "label": "strength", "comment": "..." }
```

## 后端定位策略

新增独立定位器，例如：

- `HighlightedAnnotationLocator`

职责：

输入：

1. `userAnswer`
2. 模型返回的 `quote/label/comment`

输出：

1. 成功定位的 `HighlightedAnnotation`
2. 无法唯一定位时返回空，不硬猜

### 定位算法

第一层：精确匹配

1. 在原答案中查找 `quote`
2. 仅命中一次：直接生成 `start/end`
3. 命中 0 次或多次：进入下一层

第二层：轻量归一化匹配

只允许安全归一化：

1. 去掉首尾空白
2. 折叠连续空白
3. 统一常见全角/半角标点
4. 统一中英文引号样式

### 歧义处理原则

1. 多次命中且无法唯一确定：丢弃
2. 未命中：丢弃
3. 明显过长的整段引用：丢弃或限长拦截
4. 重叠严重的多个批注：按起始位置和长度做去重，优先保留更精确的引用

核心原则：

宁可少标，不可错标。

## 前端渲染策略

前端详情页改为：

1. 始终以 `detail.userAnswer` 作为正文来源
2. 优先消费 `highlightedAnnotations`
3. 依据 `start/end` 将正文切成普通片段和高亮片段
4. `我的回答` 正文框使用固定可视高度，超出部分在容器内部滚动，而不是继续向下撑高卡片

渲染片段示例：

```js
[
  { type: 'plain', text: '我们遵循的核心原则是' },
  { type: 'strength', text: '尽量消除共享，必须共享时确保原子性', comment: '...' },
  { type: 'plain', text: '。我们主要通过' },
  { type: 'weakness', text: 'volatile', comment: '...' }
]
```

这样未命中的文本自然保持普通样式，不再依赖模型补全文。

### 正文容器表现

1. 正文框保留当前浅白阅读底色方案。
2. 为正文框设置固定窗口高度或稳定的 `max-height`，保证页面整体布局稳定。
3. 当答案内容超出窗口时，仅正文框内部出现纵向滚动条。
4. 高亮片段、普通文本和滚动行为必须共存，不能因为切片渲染导致滚动区域断裂。
5. 批注说明卡仍位于正文框下方，不随正文内容无限下移。

## 兼容策略

为避免影响历史数据与未回填记录，采用渐进兼容：

1. 后端新增 `highlightedAnnotations`，不删除 `highlightedSegments`
2. 前端优先渲染 `highlightedAnnotations`
3. 若新字段为空，再回退到旧的 `highlightedSegments`
4. 等新链路稳定后，再评估是否废弃旧字段

## 主要改动点

后端：

1. `backend/src/main/java/com/a05/aiinterview/ai/dto/QuestionDetailEvaluationOutput.java`
2. `backend/src/main/resources/prompts/question-detail-evaluation.md`
3. `backend/src/main/java/com/a05/aiinterview/ai/impl/OpenAiClient.java`
4. `backend/src/main/java/com/a05/aiinterview/interview/service/InterviewQuestionReviewService.java`
5. 新增定位器与相关测试

前端：

1. `frontend/src/App.vue`
2. `frontend/src/components/QuestionDetailPage.vue`
3. 新增切片渲染工具与对应测试

## 测试策略

### 后端

至少覆盖：

1. 精确匹配成功
2. 重复短词匹配失败并丢弃
3. 轻量归一化后匹配成功
4. 超长引用被拒绝
5. DTO / controller 返回新字段

### 前端

至少覆盖：

1. 有 `highlightedAnnotations` 时，原答案全文被切片而不是被替换
2. 未命中的文本保持普通片段
3. 高亮片段按 `start/end` 顺序渲染
4. 无新字段时正确 fallback 到旧 `highlightedSegments`
5. 正文容器具有固定高度/最大高度与内部滚动约束，不因长文本无限扩张

## 风险与取舍

### 风险

1. 模型仍可能偶尔输出无法定位的引用
2. 重复短词可能导致部分批注被主动丢弃
3. 前后端需要同时兼容新旧两套字段一段时间

### 取舍

这些风险优于“错误高亮原文”或“用模型片段替代原答案正文”的风险。系统允许少量批注缺失，但不能接受批注错位或正文失真。

## 验收标准

1. 详情页始终显示完整 `userAnswer`
2. 批注只覆盖被定位到的句子/短句，其余正文保持普通文本
3. 模型返回的重复单个词不会导致错误高亮
4. 新链路上线后，旧数据仍可通过 fallback 正常展示
5. 长答案场景下，“我的回答”正文框保持稳定高度，通过内部滚动浏览
6. 后端测试、前端测试和构建均通过
