# IntelliJ IDEA 开发环境启动与测试指南

> 适用版本：IntelliJ IDEA 2023.x / 2024.x，JDK 21，Maven 3.9+

---

## 一、前置环境准备

### 1.1 必须启动的本地服务

| 服务 | 默认端口 | 用途 | 是否必须 |
|------|---------|------|---------|
| MySQL 8.x | 3306 | 业务数据主库 | **必须** |
| Redis 7.x | 6379 | 分布式锁、验证码、幂等缓存 | **必须** |
| RabbitMQ 3.x | 5672 | 异步报告生成消息队列 | **必须** |
| Qdrant | 6333(REST) / 6334(gRPC) | 向量知识库（RAG） | 仅 `rag.enabled=true` 时需要 |

**启动 MySQL / Redis / RabbitMQ（推荐用 Docker Compose）**

```bash
# 项目根目录下执行（如无 docker-compose.yml 则参考下方单独命令）
docker run -d --name mysql8 -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:8.0
docker run -d --name redis7  -p 6379:6379 redis:7
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

**启动 Qdrant（仅 RAG 模式需要）**

```bash
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant
```

---

### 1.2 初始化数据库

首次运行前，按顺序执行以下 SQL 脚本：

```bash
# 进入 MySQL 命令行（密码默认 123456）
mysql -u root -p

# 依次执行（在 IDEA 的 Database 工具或命令行均可）
source backend/src/main/resources/db/01-create-database.sql
source backend/src/main/resources/db/schema-auth.sql
source backend/src/main/resources/db/schema-resume.sql
source backend/src/main/resources/db/schema-position.sql
source backend/src/main/resources/db/schema-interview.sql
source backend/src/main/resources/db/schema-interview-v2.sql
```

> **IDEA 操作**：在右侧 `Database` 面板中添加 MySQL 数据源后，右键 → `Run SQL Script` 依次执行上述文件。

---

## 二、IDEA 导入与 Maven 配置

### 2.1 导入项目

1. **File → Open** → 选择 `d:\a05-cursor\backend` 目录（pom.xml 所在目录）
2. IDEA 自动识别为 Maven 项目，点击 **Load Maven Project**
3. 等待 Maven 下载依赖（首次约需 2~5 分钟，依网络而定）
4. 确认右下角 Maven 进度条消失后继续

> 若 Maven 下载缓慢，可在 `Settings → Build → Maven → Repositories` 中使用阿里云镜像：
> `https://maven.aliyun.com/repository/public`

### 2.2 SDK 配置

**File → Project Structure → Project**

| 配置项 | 值 |
|--------|-----|
| SDK | `21`（需提前在 IDEA 中安装 JDK 21） |
| Language Level | `21 - ... (Preview)` 或 `21` |

---

## 三、Run Configuration 配置

### 3.1 模式一：Mock AI（默认，推荐联调阶段）

> **特点**：无需真实 OpenAI Key，无需 Qdrant，最快启动，适合前端联调和主链路功能验证。

**操作步骤：**

1. 打开 **Run → Edit Configurations**
2. 点击 `+` → **Spring Boot**
3. 填写以下配置：

| 字段 | 值 |
|------|----|
| Name | `AI Interview - Mock 模式` |
| Main class | `com.a05.aiinterview.AiInterviewApplication` |
| Working directory | `$MODULE_WORKING_DIR$` |
| Environment variables | 见下方 |

**Environment variables（复制粘贴到 IDEA 的环境变量输入框）：**

```
DB_PASSWORD=123456;AI_MOCK_ENABLED=true;RAG_ENABLED=false
```

4. 点击 **Apply → OK**
5. 点击绿色 ▶ 启动，观察控制台输出 `Started AiInterviewApplication`

---

### 3.2 模式二：真实 AI + RAG（需 OpenAI Key 和 Qdrant）

> **特点**：调用真实 GPT 模型出题，RAG 检索向量知识库增强出题质量。

**Environment variables：**

```
DB_PASSWORD=123456;AI_MOCK_ENABLED=false;OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxx;OPENAI_BASE_URL=https://api.openai.com;OPENAI_MODEL=gpt-4o-mini;RAG_ENABLED=true;RAG_INIT_SAMPLE=true
```

> `RAG_INIT_SAMPLE=true` 表示首次启动时自动向 Qdrant 注入两个知识域（`java_memory_model` + `jvm_gc`）的样本数据，之后可改为 `false`。

> 如果使用国内代理/兼容接口，修改 `OPENAI_BASE_URL` 为对应地址，如：
> `OPENAI_BASE_URL=https://your-proxy.com/v1`

---

### 3.3 模式三：真实 AI、禁用 RAG

> **特点**：调用真实模型但不启用向量检索，适合单独验证 AI 出题质量。

**Environment variables：**

```
DB_PASSWORD=123456;AI_MOCK_ENABLED=false;OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxx;OPENAI_BASE_URL=https://api.openai.com;OPENAI_MODEL=gpt-4o-mini;RAG_ENABLED=false
```

---

## 四、启动验证

### 4.1 控制台关键日志

启动成功后，在控制台中确认以下日志出现：

```
Started AiInterviewApplication in XX.XXX seconds
```

**Mock 模式额外确认：**
```
# 无 Qdrant 相关连接日志，说明 RAG 已按预期跳过
```

**RAG 模式额外确认：**
```
INFO  RagConfiguration - 初始化 Qdrant 客户端, host=localhost, port=6334
INFO  RagConfiguration - 初始化 Qdrant VectorStore, collection=interview_knowledge, initSchema=true
INFO  SampleKnowledgeDataLoader - 样本知识数据加载完成, 写入片段数=X
```

### 4.2 接口健康检查

```bash
# 系统 Ping（无需登录）
curl http://localhost:8080/api/v1/system/ping
# 期望返回：{"code":0,"message":"OK","data":{"serverTime":"..."}}
```

---

## 五、主流程 API 测试

以下用例覆盖 T1.4 的端到端验收，可在 IDEA 内置的 **HTTP Client** 或 Postman 执行。

### 5.1 注册 + 登录

```http
### 1. 注册
POST http://localhost:8080/api/v1/auth/register
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "Test@1234",
  "nickname": "测试用户"
}

### 2. 登录，获取 Token
POST http://localhost:8080/api/v1/auth/login/password
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "Test@1234"
}
```

> 将返回的 `token` 保存，后续请求在 Header 中携带：`Authorization: Bearer <token>`

---

### 5.2 创建面试会话（自动触发 Planner）

```http
POST http://localhost:8080/api/v1/interviews
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "targetRole": "backend_java",
  "experienceLevel": "MID",
  "mode": "practice"
}
```

> 返回 `sessionId`，保存备用。Planner 异步生成考纲，约 1~3 秒。

---

### 5.3 轮询会话状态（等待首题就绪）

```http
GET http://localhost:8080/api/v1/interviews/{{sessionId}}
Authorization: Bearer {{token}}
```

> 当 `status` 变为 `in_progress` 且 `currentQuestionId` 非空时，进入答题流程。

---

### 5.4 提交回答（核心：验证 RAG 接入）

```http
POST http://localhost:8080/api/v1/interviews/{{sessionId}}/attempts
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "questionId": {{currentQuestionId}},
  "attemptId": "test-attempt-001",
  "answerText": "Java 内存模型定义了主内存和工作内存的交互规则，volatile 保证可见性但不保证原子性...",
  "isFinal": true
}
```

**验证点：**

- `evalautionSignal` 为 `NEXT_DOMAIN` 或 `DEEPEN`，说明评估决策正常
- `nextQuestion.stem` 非空，说明下一题生成成功
- **RAG 模式**：打开数据库查询 `ai_invocation_logs` 最新记录：

```sql
SELECT id, prompt_code, retrieval_context_json, success, created_at
FROM ai_invocation_logs
WHERE prompt_code = 'question_generation'
ORDER BY id DESC
LIMIT 5;
```

> `retrieval_context_json` 字段不为 `null` 且 `hitCount > 0` 即表示 RAG 检索命中。

---

### 5.5 幂等验证

```http
# 使用完全相同的 attemptId 重复提交
POST http://localhost:8080/api/v1/interviews/{{sessionId}}/attempts
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "questionId": {{currentQuestionId}},
  "attemptId": "test-attempt-001",
  "answerText": "重复提交",
  "isFinal": true
}
```

> 期望返回与第一次相同的结果，且控制台打印 `幂等命中，直接返回历史结果`。

---

### 5.6 RAG 降级验证（仅 RAG 模式）

**方法一：暂停 Qdrant 进程后提交回答**

```bash
docker stop qdrant
```

再次执行 5.4 的提交回答请求，期望：
- 接口正常返回（不报错）
- 控制台出现 `ERROR ... RAG 检索异常，降级返回空上下文`
- `retrieval_context_json.empty = true`

```bash
# 恢复
docker start qdrant
```

**方法二：清空 Qdrant 集合后测试无命中路径**

```bash
# 访问 Qdrant Dashboard 删除集合
http://localhost:6333/dashboard
# 或用 REST API
curl -X DELETE http://localhost:6333/collections/interview_knowledge
```

再次提交回答，期望：
- 接口正常返回（不报错）
- 控制台打印 `RAG 检索无命中`
- `retrieval_context_json.empty = true`，`hitCount = 0`

---

## 六、IDEA HTTP Client 文件（可直接使用）

在 `backend/src/test/http/` 目录下创建 `dev.http`，将以下内容粘贴：

```http
### 系统 Ping
GET http://localhost:8080/api/v1/system/ping

### 登录
# @name login
POST http://localhost:8080/api/v1/auth/login/password
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "Test@1234"
}

### 创建面试会话
# @name createSession
POST http://localhost:8080/api/v1/interviews
Authorization: Bearer {{login.response.body.data.token}}
Content-Type: application/json

{
  "targetRole": "backend_java",
  "experienceLevel": "MID",
  "mode": "practice"
}

### 查询会话状态
GET http://localhost:8080/api/v1/interviews/{{createSession.response.body.data.sessionId}}
Authorization: Bearer {{login.response.body.data.token}}

### 提交回答
POST http://localhost:8080/api/v1/interviews/{{createSession.response.body.data.sessionId}}/attempts
Authorization: Bearer {{login.response.body.data.token}}
Content-Type: application/json

{
  "questionId": 1,
  "attemptId": "manual-test-001",
  "answerText": "Java 内存模型（JMM）规定了主内存与工作内存的交互规则...",
  "isFinal": true
}
```

---

## 七、常见问题排查

| 现象 | 原因 | 解决 |
|------|------|------|
| 启动报 `Communications link failure` | MySQL 未启动或端口不对 | 检查 Docker / 本地 MySQL 服务 |
| 启动报 `NOAUTH Authentication required` | Redis 设置了密码但配置未填 | 在环境变量中加 `REDIS_PASSWORD=你的密码` |
| 启动报 `Connection refused: localhost:5672` | RabbitMQ 未启动 | `docker start rabbitmq` 或重新拉起 |
| 启动报 `QdrantClient ... Connection refused` | `rag.enabled=true` 但 Qdrant 未启动 | 先 `docker start qdrant`，或将 `RAG_ENABLED` 设为 `false` |
| 提交回答返回 `评估决策失败` | MockAiClient 出现异常 | 查看控制台完整堆栈，通常是 JSON 解析问题 |
| `retrieval_context_json` 一直为 null | `rag.enabled=false` 是预期行为 | 如需测试 RAG，将 `RAG_ENABLED=true` 加入环境变量 |
| Maven 构建报 `Could not resolve dependencies` | 网络问题或镜像源未配置 | 在 `~/.m2/settings.xml` 配置阿里云 mirror |
| 端口 8080 被占用 | 其他应用占用 | 修改 `application.yml` 的 `server.port`，或关闭占用进程 |

---

## 八、多模式配置速查

| 变量名 | Mock 联调 | 真实 AI | 真实 AI + RAG |
|--------|----------|---------|---------------|
| `AI_MOCK_ENABLED` | `true` | `false` | `false` |
| `OPENAI_API_KEY` | （不需要） | `sk-xxx` | `sk-xxx` |
| `OPENAI_BASE_URL` | （不需要） | API 地址 | API 地址 |
| `RAG_ENABLED` | `false` | `false` | `true` |
| `RAG_INIT_SAMPLE` | （不需要） | （不需要） | `true`（首次）/ `false`（之后） |
| `DB_PASSWORD` | `123456` | `123456` | `123456` |

---

> 如遇其他问题，优先查看 IDEA 控制台的完整日志（`com.a05.aiinterview` 包日志级别为 DEBUG），
> 关键错误均有 `log.error(...)` 包含完整堆栈。
