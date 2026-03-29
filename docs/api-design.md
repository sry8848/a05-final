# 当前 API 设计

本文记录当前后端已经实现的 HTTP / SSE / 语音相关接口。

## 通用约定

### 基础前缀

所有 HTTP 接口统一挂在：

```text
/api/v1
```

### 通用响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "traceId": "..."
}
```

- `code = 0` 表示成功
- 非 `0` 表示业务失败
- `traceId` 用于日志追踪

### 鉴权

- 普通用户接口使用用户 token
- 管理端接口使用管理员 token
- `system/ping`、`positions/*` 为公开接口
- `admin/knowledge/ingest` 只有在 `rag.enabled=true` 时才注册

## 账户接口

### `POST /auth/register`

邮箱注册。

请求字段：

- `email`
- `code`
- `nickname`
- `password`

### `POST /auth/login/password`

邮箱密码登录。

请求字段：

- `email`
- `password`

返回字段：

- `token`
- `userId`
- `nickname`

### `POST /auth/login/email-code`

邮箱验证码登录。

请求字段：

- `email`
- `code`

### `POST /auth/email-code/send`

发送邮箱验证码。

请求字段：

- `email`
- `scene`，当前取值为 `login` 或 `register`

返回字段：

- `devCode`
  当前为兼容字段，真实邮件模式下固定为 `null`

### `GET /auth/me`

获取当前用户信息。

返回字段：

- `id`
- `email`
- `nickname`

### `POST /auth/logout`

用户退出登录。

### 管理员接口

- `POST /auth/admin/login`
- `GET /auth/admin/me`
- `POST /auth/admin/logout`

管理员登录返回：

- `token`
- `username`
- `displayName`

## 公共配置接口

### `GET /system/ping`

公开接口，返回：

- `serverTime`

### `GET /positions`

公开接口，返回岗位列表。

### `GET /positions/{positionCode}/skill-domains`

公开接口，返回指定岗位的知识域列表。

## 简历接口

### `GET /resumes`

返回当前用户简历列表。

### `POST /resumes/upload`

上传简历文件并触发解析。

表单字段：

- `file`

当前支持格式：

- `pdf`
- `docx`
- `md`

当前行为：

- `pdf` / `docx` 上传后先返回 `parseStatus=parsing`，再通过状态接口轮询结果
- `md` 上传后直接返回 `parseStatus=parsed`，`parsedText` 保留 Markdown 原文

### `GET /resumes/{resumeId}/parse-status`

查询解析状态。

### `GET /resumes/{resumeId}`

获取简历详情。

### `PUT /resumes/{resumeId}`

更新简历名称、解析文本、默认状态。

### `POST /resumes/{resumeId}/set-default`

设为默认简历。

### `DELETE /resumes/{resumeId}`

删除简历。

## 用户资料与成长接口

### `GET /profile`

获取当前用户资料。

### `PUT /profile`

更新资料。

### `POST /profile/avatar`

上传头像文件。

### `GET /profile/avatar/{uid}/{fileName}`

读取头像文件。

### `GET /profile/statistics`

获取成长统计。

可选查询参数：

- `positionCode`

### `GET /profile/skill-overview`

获取知识域成长概览。

可选查询参数：

- `positionCode`

## 面试偏好接口

### `GET /interview-preferences/latest`

获取最近一次面试准备偏好。

### `PUT /interview-preferences/latest`

保存最近一次面试准备偏好。

字段包括：

- `positionCode`
- `experienceLevel`
- `mode`
- `focusTopics`
- `thinkTimeLimitSeconds`
- `answerTimeLimitSeconds`

## 面试主链路接口

### `GET /interviews`

历史列表查询。

支持的查询参数：

- `page`
- `pageSize`
- `status`
- `positionCode`
- `dateFrom`
- `dateTo`
- `sortBy`
- `sortOrder`

### `POST /interviews`

创建面试会话。

请求字段：

- `positionCode`
- `experienceLevel`
- `mode`
- `jobDescription`
- `resumeId`
- `focusTopics`
- `rememberSettings`
- `thinkTimeLimitSeconds`
- `answerTimeLimitSeconds`

返回字段：

- `sessionId`
- `status`

### `GET /interviews/{sessionId}`

查询会话详情。

返回重点字段：

- `id`
- `title`
- `positionCode`
- `experienceLevel`
- `mode`
- `currentQuestionNo`
- `status`
- `syllabusSummary`
- `currentQuestion`

### `POST /interviews/{sessionId}/attempts`

提交当前题回答。

请求重点字段：

- `questionId`
- `attemptId`
- `answerText`
- `rawAsrText`
- `asrCorrectionChanges`
- `isFinal`
- `pauseStats`
- `asrSegments`
- `audioUrl`

返回重点字段：

- `attemptId`
- `decision`
- `streamAttemptId`
- `sessionStatus`

当前主链路不是“同步返回下一题”，而是：

1. 先调用本接口提交回答
2. 再用 `streamAttemptId` 调题目流接口

### `GET /interviews/{sessionId}/questions/stream?attemptId=...`

SSE 题目流接口。

当前事件类型：

- `start`
- `delta`
- `tts_ready`
- `done`
- `error`

支持 `Last-Event-ID` 断点续传。

### `POST /interviews/{sessionId}/finish`

结束面试并触发报告生成。

### `GET /interviews/{sessionId}/report`

查询整场报告。

当前 `reportStatus` 可能为：

- `generating`
- `ready`
- `failed`

### `DELETE /interviews/{sessionId}`

删除会话。

### `GET /interviews/{sessionId}/questions/{questionId}`

查询单题详细复盘。

### `GET /interviews/{sessionId}/report/learning-recommendations`

查询报告页学习建议。

### `POST /interviews/{sessionId}/questions/{questionId}/redo-attempts`

提交单题重答。

请求字段：

- `answerText`

### `GET /interviews/{sessionId}/questions/{questionId}/redo-attempts/latest`

查询最近一次单题重答结果。

### `GET /interviews/{sessionId}/questions/{questionId}/ai-consult/messages`

查询当前题目的 AI 追问历史消息。

返回字段：

- `id`
- `role`
- `status`
- `content`
- `replyToMessageId`
- `createdAt`

### `POST /interviews/{sessionId}/questions/{questionId}/ai-consult/messages`

创建一轮新的 AI 追问。

请求字段：

- `content`

返回字段：

- `userMessageId`
- `assistantMessageId`

### `GET /interviews/{sessionId}/questions/{questionId}/ai-consult/messages/{assistantMessageId}/stream`

单题追问 SSE 流接口。

当前事件类型：

- `start`
- `delta`
- `done`
- `error`

### `GET /interviews/{sessionId}/debug/ledger`

查询当前会话的状态账本，仅用于调试。

## 题目播报与语音接口

### `GET /interviews/{sessionId}/questions/{questionId}/audio`

查询题目播报音频状态。

返回字段：

- `ready`
- `audioUrl`

### `GET /interviews/{sessionId}/questions/{questionId}/audio/file`

下载题目播报音频。

### `GET /interviews/{sessionId}/attempts/{attemptId}/audio/segments/{segmentIndex}/file`

下载题目片段播报音频。

### `GET /asr/token`

获取 ASR 后端代理票据。

返回重点字段：

- `enabled`
- `wsUrl`
- `ticket`
- `expiresAt`
- `protocolVersion`
- `audio`

### `GET /config/asr-pause-thresholds`

获取各题型停顿阈值配置。

## 问答库接口

### `POST /question-bank`

收藏题目到问答库。

### `GET /question-bank`

分页查询问答库。

支持的查询参数：

- `page`
- `pageSize`
- `tag`
- `minScore`
- `maxScore`
- `sortBy`
- `sortOrder`

### `DELETE /question-bank/{itemId}`

删除问答库条目。

## 管理端接口

### 仪表盘

- `GET /admin/dashboard/overview`
- `GET /admin/dashboard/trends?days=7`
- `GET /admin/dashboard/models?windowMinutes=15`
- `GET /admin/dashboard/prompts`

这些接口要求管理员 token。

### 知识入库

`POST /admin/knowledge/ingest`

请求体：

- `documents`

返回字段：

- `ingestedChunks`

当前仅在 `rag.enabled=true` 时注册。

## 当前接口边界

现行接口说明只保留当前后端已经实现并可联调的接口。历史命名和废弃方案如需追溯，请查看归档文档。
