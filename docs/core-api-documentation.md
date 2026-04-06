# AI 模拟面试系统 - 核心接口文档

> **Base URL**: `/api/v1`  
> **认证方式**: JWT Bearer Token  
> **响应格式**: 统一使用 `ApiResponse<T>` 包装

---

## 目录

1. [系统接口](#1-系统接口)
2. [认证授权接口](#2-认证授权接口)
3. [岗位与知识域接口](#3-岗位与知识域接口)
4. [简历管理接口](#4-简历管理接口)
5. [用户资料接口](#5-用户资料接口)
6. [面试会话接口](#6-面试会话接口)
7. [面试语音播报接口](#7-面试语音播报接口)
8. [问答库接口](#8-问答库接口)
9. [统一响应格式](#9-统一响应格式)

---

## 1. 系统接口

### 1.1 健康检查

**接口描述**: 检查服务是否正常运行

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/system/ping` |
| **认证** | 不需要 |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "serverTime": "2026-03-31T12:00:00Z"
  }
}
```

---

## 2. 认证授权接口

### 2.1 用户注册

**接口描述**: 新用户注册

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/auth/register` |
| **认证** | 不需要 |

**请求体**:
```json
{
  "email": "user@example.com",
  "password": "password123",
  "emailCode": "123456"
}
```

**响应**: `ApiResponse<Void>`

---

### 2.2 密码登录

**接口描述**: 使用邮箱+密码登录

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/auth/login/password` |
| **认证** | 不需要 |

**请求体**:
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "user": {
      "id": 1,
      "email": "user@example.com",
      "nickname": "张三"
    }
  }
}
```

---

### 2.3 邮箱验证码登录

**接口描述**: 使用邮箱+验证码登录

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/auth/login/email-code` |
| **认证** | 不需要 |

**请求体**:
```json
{
  "email": "user@example.com",
  "emailCode": "123456"
}
```

**响应**: 同密码登录

---

### 2.4 发送邮箱验证码

**接口描述**: 发送注册或登录验证码

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/auth/email-code/send` |
| **认证** | 不需要 |

**请求体**:
```json
{
  "email": "user@example.com",
  "scene": "login"
}
```

**scene 说明**:
- `login`: 登录场景
- `register`: 注册场景

---

### 2.5 获取当前用户信息

**接口描述**: 获取当前登录用户信息

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/auth/me` |
| **认证** | 需要 |

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "张三",
    "avatarUrl": "/api/v1/profile/avatar/1/xxx.jpg"
  }
}
```

---

### 2.6 用户登出

**接口描述**: 登出当前用户

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/auth/logout` |
| **认证** | 需要 |

**请求头**:
```
Authorization: Bearer <token>
```

---

### 2.7 管理员登录

**接口描述**: 管理员登录

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/auth/admin/login` |
| **认证** | 不需要 |

**请求体**:
```json
{
  "username": "admin",
  "password": "admin123"
}
```

---

### 2.8 获取当前管理员信息

**接口描述**: 获取当前登录管理员信息

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/auth/admin/me` |
| **认证** | 需要（管理员Token） |

---

### 2.9 管理员登出

**接口描述**: 管理员登出

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/auth/admin/logout` |
| **认证** | 需要 |

---

## 3. 岗位与知识域接口

### 3.1 获取岗位列表

**接口描述**: 获取系统支持的所有岗位

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/positions` |
| **认证** | 不需要 |

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "positionCode": "JAVA_BACKEND",
      "positionName": "Java后端开发"
    },
    {
      "positionCode": "FRONTEND",
      "positionName": "前端开发"
    }
  ]
}
```

---

### 3.2 获取岗位知识域树

**接口描述**: 获取指定岗位的知识域列表

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/positions/{positionCode}/skill-domains` |
| **认证** | 不需要 |

**路径参数**:
- `positionCode`: 岗位编码，如 `JAVA_BACKEND`

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "domainCode": "JAVA_BASE",
      "domainName": "Java基础",
      "sortOrder": 1,
      "children": [...]
    }
  ]
}
```

---

## 4. 简历管理接口

### 4.1 获取简历列表

**接口描述**: 获取当前用户的所有简历

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/resumes` |
| **认证** | 需要 |

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "fileName": "张三_简历.pdf",
      "isDefault": true,
      "parseStatus": "SUCCESS",
      "createdAt": "2026-03-01T10:00:00"
    }
  ]
}
```

---

### 4.2 上传简历

**接口描述**: 上传简历文件并发起解析

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/resumes/upload` |
| **认证** | 需要 |
| **Content-Type** | `multipart/form-data` |

**请求参数**:
- `file`: 简历文件（支持 PDF、DOCX）

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "resumeId": 1,
    "parseStatus": "PENDING"
  }
}
```

---

### 4.3 查询简历解析状态

**接口描述**: 查询简历解析进度

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/resumes/{resumeId}/parse-status` |
| **认证** | 需要 |

**路径参数**:
- `resumeId`: 简历ID

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "SUCCESS",
    "errorMessage": null
  }
}
```

**status 枚举**:
- `PENDING`: 等待解析
- `PROCESSING`: 解析中
- `SUCCESS`: 解析成功
- `FAILED`: 解析失败

---

### 4.4 获取简历详情

**接口描述**: 获取简历的详细信息（包含解析后的内容）

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/resumes/{resumeId}` |
| **认证** | 需要 |

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "fileName": "张三_简历.pdf",
    "parsedText": "姓名：张三\n教育经历：...",
    "isDefault": true
  }
}
```

---

### 4.5 更新简历

**接口描述**: 更新简历信息（名称、解析文本、默认状态）

| 属性 | 值 |
|------|-----|
| **Method** | `PUT` |
| **Path** | `/resumes/{resumeId}` |
| **认证** | 需要 |

**请求体**:
```json
{
  "fileName": "更新后的文件名.pdf",
  "parsedText": "修改后的简历文本",
  "isDefault": true
}
```

---

### 4.6 设为默认简历

**接口描述**: 将指定简历设为默认简历

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/resumes/{resumeId}/set-default` |
| **认证** | 需要 |

---

### 4.7 删除简历

**接口描述**: 删除指定简历

| 属性 | 值 |
|------|-----|
| **Method** | `DELETE` |
| **Path** | `/resumes/{resumeId}` |
| **认证** | 需要 |

---

## 5. 用户资料接口

### 5.1 获取当前用户资料

**接口描述**: 获取用户详细资料

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/profile` |
| **认证** | 需要 |

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "nickname": "张三",
    "avatarUrl": "/api/v1/profile/avatar/1/xxx.jpg",
    "targetPosition": "JAVA_BACKEND",
    "experienceLevel": "JUNIOR"
  }
}
```

---

### 5.2 更新用户资料

**接口描述**: 更新用户资料

| 属性 | 值 |
|------|-----|
| **Method** | `PUT` |
| **Path** | `/profile` |
| **认证** | 需要 |

**请求体**:
```json
{
  "nickname": "张三",
  "targetPosition": "JAVA_BACKEND",
  "experienceLevel": "JUNIOR"
}
```

---

### 5.3 上传头像

**接口描述**: 上传用户头像

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/profile/avatar` |
| **认证** | 需要 |
| **Content-Type** | `multipart/form-data` |

**请求参数**:
- `file`: 头像图片文件

---

### 5.4 获取成长统计

**接口描述**: 获取用户面试成长统计数据

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/profile/statistics` |
| **认证** | 需要 |

**查询参数**:
- `positionCode` (可选): 岗位编码，不传则统计所有岗位

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalInterviews": 10,
    "avgScore": 75.5,
    "scoreTrend": [...],
    "latestInterviewDate": "2026-03-30"
  }
}
```

---

### 5.5 获取知识域成长概览

**接口描述**: 获取用户各知识域的能力概览

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/profile/skill-overview` |
| **认证** | 需要 |

**查询参数**:
- `positionCode` (可选): 岗位编码

---

### 5.6 获取头像文件

**接口描述**: 读取用户头像文件

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/profile/avatar/{uid}/{fileName}` |
| **认证** | 需要（仅能访问自己的头像） |

---

## 6. 面试会话接口

### 6.1 获取面试历史列表

**接口描述**: 分页获取用户的面试历史

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews` |
| **认证** | 需要 |

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | int | 否 | 页码，默认1 |
| `pageSize` | int | 否 | 每页数量，默认10 |
| `status` | string | 否 | 面试状态过滤 |
| `positionCode` | string | 否 | 岗位编码过滤 |
| `dateFrom` | datetime | 否 | 开始日期 |
| `dateTo` | datetime | 否 | 结束日期 |
| `sortBy` | string | 否 | 排序字段 |
| `sortOrder` | string | 否 | 排序方向：asc/desc |

---

### 6.2 创建面试会话

**接口描述**: 创建新的面试会话

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/interviews` |
| **认证** | 需要 |

**请求体**:
```json
{
  "positionCode": "JAVA_BACKEND",
  "experienceLevel": "JUNIOR",
  "resumeId": 1,
  "interviewMode": "NORMAL",
  "selectedDomains": ["JAVA_BASE", "SPRING"]
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": 1001,
    "status": "IN_PROGRESS"
  }
}
```

---

### 6.3 获取面试会话详情

**接口描述**: 获取指定面试会话的详细信息

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}` |
| **认证** | 需要 |

---

### 6.4 提交面试答案

**接口描述**: 提交当前问题的答案，获取下一步决策

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/interviews/{sessionId}/attempts` |
| **认证** | 需要 |

**请求体**:
```json
{
  "questionId": 2001,
  "answerText": "我的回答内容...",
  "answerAudioUrl": null
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "attemptId": "att_xxx",
    "decision": {
      "strategyCode": "S_P_DEEP_LINK",
      "nextFocus": "线程池核心参数"
    },
    "evaluation": {
      "score": 80,
      "strengths": ["理解基本概念"],
      "weaknesses": ["缺少实践细节"]
    }
  }
}
```

---

### 6.5 流式获取下一个问题 (SSE)

**接口描述**: 通过 Server-Sent Events 流式获取下一题

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/questions/stream` |
| **认证** | 需要 |
| **Content-Type** | `text/event-stream` |

**查询参数**:
- `attemptId`: 上一次提交答案返回的 attemptId

**请求头** (断点续传用):
```
Last-Event-ID: <last-event-id>
```

**SSE 事件类型**:
| 事件 | 说明 |
|------|------|
| `start` | 开始生成 |
| `delta` | 文本增量 |
| `tts_ready` | TTS语音就绪 |
| `done` | 生成完成 |
| `error` | 错误 |

---

### 6.6 完成面试并生成报告

**接口描述**: 手动结束面试并触发报告生成

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/interviews/{sessionId}/finish` |
| **认证** | 需要 |

---

### 6.7 获取面试报告

**接口描述**: 获取面试报告详情

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/report` |
| **认证** | 需要 |

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "overallScore": 78,
    "radarScores": [
      { "dimension": "Java基础", "score": 85 },
      { "dimension": "框架应用", "score": 72 }
    ],
    "summary": "整体表现良好，基础扎实...",
    "strengths": [...],
    "improvements": [...]
  }
}
```

---

### 6.8 删除面试会话

**接口描述**: 删除指定的面试会话

| 属性 | 值 |
|------|-----|
| **Method** | `DELETE` |
| **Path** | `/interviews/{sessionId}` |
| **认证** | 需要 |

---

### 6.9 获取问题回顾详情

**接口描述**: 获取面试中某个问题的详细回顾（含AI点评）

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/questions/{questionId}` |
| **认证** | 需要 |

---

### 6.10 获取学习建议

**接口描述**: 根据面试报告获取个性化学习建议

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/report/learning-recommendations` |
| **认证** | 需要 |

---

### 6.11 创建题目重做尝试

**接口描述**: 对某道题目进行重做练习

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/interviews/{sessionId}/questions/{questionId}/redo-attempts` |
| **认证** | 需要 |

---

### 6.12 获取最新重做尝试

**接口描述**: 获取某道题目的最新重做记录

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/questions/{questionId}/redo-attempts/latest` |
| **认证** | 需要 |

---

### 6.13 获取题目AI咨询消息列表

**接口描述**: 获取与AI讨论某道题目的历史消息

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/questions/{questionId}/ai-consult/messages` |
| **认证** | 需要 |

---

### 6.14 发送题目AI咨询消息

**接口描述**: 向AI提问关于某道题目的问题

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/interviews/{sessionId}/questions/{questionId}/ai-consult/messages` |
| **认证** | 需要 |

---

### 6.15 流式获取AI咨询回复 (SSE)

**接口描述**: 流式获取AI对咨询问题的回复

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/questions/{questionId}/ai-consult/messages/{assistantMessageId}/stream` |
| **认证** | 需要 |
| **Content-Type** | `text/event-stream` |

---

### 6.16 获取调试用状态账本

**接口描述**: 获取面试的状态账本（仅用于调试）

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/debug/ledger` |
| **认证** | 需要 |

---

## 7. 面试语音播报接口

### 7.1 查询题目播报音频状态

**接口描述**: 查询某道题目的语音是否已准备好

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/questions/{questionId}/audio` |
| **认证** | 需要 |

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "ready": true,
    "audioUrl": "/api/v1/interviews/1001/questions/2001/audio/file"
  }
}
```

---

### 7.2 获取题目播报音频文件

**接口描述**: 下载题目的语音播报音频

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/questions/{questionId}/audio/file` |
| **认证** | 需要 |
| **响应类型** | `audio/mpeg` |

---

### 7.3 获取题目片段播报音频

**接口描述**: 获取某段文本片段的语音

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/interviews/{sessionId}/attempts/{attemptId}/audio/segments/{segmentIndex}/file` |
| **认证** | 需要 |

---

## 8. 问答库接口

### 8.1 收藏题目到问答库

**接口描述**: 将面试中的题目收藏到个人问答库

| 属性 | 值 |
|------|-----|
| **Method** | `POST` |
| **Path** | `/question-bank` |
| **认证** | 需要 |

**请求体**:
```json
{
  "sessionId": 1001,
  "questionId": 2001,
  "tag": "Java并发"
}
```

---

### 8.2 获取问答库列表

**接口描述**: 分页获取个人问答库

| 属性 | 值 |
|------|-----|
| **Method** | `GET` |
| **Path** | `/question-bank` |
| **认证** | 需要 |

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | int | 否 | 页码 |
| `pageSize` | int | 否 | 每页数量 |
| `tag` | string | 否 | 标签过滤 |
| `minScore` | decimal | 否 | 最低分数 |
| `maxScore` | decimal | 否 | 最高分数 |
| `sortBy` | string | 否 | 排序字段 |
| `sortOrder` | string | 否 | 排序方向 |

---

### 8.3 删除问答库条目

**接口描述**: 从问答库中删除某道题目

| 属性 | 值 |
|------|-----|
| **Method** | `DELETE` |
| **Path** | `/question-bank/{itemId}` |
| **认证** | 需要 |

---

## 9. 统一响应格式

所有接口响应统一使用 `ApiResponse<T>` 包装：

### 成功响应
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 错误响应
```json
{
  "code": 400,
  "message": "参数错误",
  "data": null
}
```

### 状态码说明
| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权/未登录 |
| 403 | 无权访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 附录：面试完整流程示例

```
1. POST /auth/login/password           → 获取 Token
2. GET  /positions                      → 选择岗位
3. GET  /positions/{code}/skill-domains → 查看知识域
4. GET  /resumes                        → 选择简历
5. POST /interviews                     → 创建面试会话
6. GET  /interviews/{sessionId}/questions/stream → 流式获取首题
7. POST /interviews/{sessionId}/attempts → 提交答案
8. GET  /interviews/{sessionId}/questions/stream → 获取下一题
   ... (重复 7-8 直到面试结束)
9. POST /interviews/{sessionId}/finish → 结束面试
10.GET  /interviews/{sessionId}/report → 获取面试报告
```

---

*文档版本: v1.0 | 更新日期: 2026-03-31*
