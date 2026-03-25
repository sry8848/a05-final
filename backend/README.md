# Backend

Spring Boot 后端服务，统一暴露在 `/api/v1`。

完整联调顺序优先看根目录 [README.md](/D:/a05-cursor/README.md)。本文只说明后端自身结构、配置和启动要点。

## 模块结构

```text
src/main/java/com/a05/aiinterview/
├── admin/       # 管理端鉴权与仪表盘
├── ai/          # Prompt、模型适配、契约校验、调用日志
├── auth/        # 注册、登录、验证码、JWT
├── common/      # 统一响应、异常、追踪
├── interview/   # 面试会话、SSE 出题、报告、重答
├── position/    # 岗位与知识域
├── profile/     # 用户资料与成长数据
├── questionbank/# 成长问答库
├── rag/         # Qdrant 检索与知识入库
├── resume/      # 简历上传、解析、编辑
├── speech/      # ASR 票据、TTS 播报、代理桥接
└── system/      # Ping 等系统接口
```

## 配置文件

- `src/main/resources/application.yml`
  公共基础配置，包含数据库、Redis、RabbitMQ、JWT、AI、RAG、语音默认项
- `src/main/resources/application-local.yml`
  本地真实联调配置，使用百炼兼容 OpenAI 接口，适合 `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- `src/main/resources/application-dev.yml`
  开发便捷配置，保留数据库与管理员默认项
- `src/test/resources/application-test.yml`
  自动化测试专用，禁用 RAG / 语音并开启 mock AI

## 启动前提

- JDK 21
- MySQL 8
- Redis
- RabbitMQ
- 可选：Qdrant
- 可选：阿里云百炼 API Key

数据库脚本位置：

- [backend/src/main/resources/db](/D:/a05-cursor/backend/src/main/resources/db)

推荐初始化顺序见：

- [backend/src/main/resources/db/README.md](/D:/a05-cursor/backend/src/main/resources/db/README.md)

## 本地启动

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

打包运行：

```bash
mvn package
java -jar target/aiinterview-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

## 关键环境变量

常用变量包括：

- `AI_BAILIAN_API_KEY`
- `RAG_ENABLED`
- `QDRANT_HOST`
- `QDRANT_PORT`
- `QDRANT_COLLECTION`
- `ASR_ENABLED`
- `ASR_API_KEY`
- `TTS_ENABLED`
- `TTS_API_KEY`

这些变量可以从根目录 `.env` 导入到当前终端，或者直接写到 IDEA 的运行配置里。

## 数据与资源目录

- `src/main/resources/db/`
  初始化与增量 SQL
- `src/main/resources/prompts/`
  当前 Prompt 模板：`planner`、`evaluation-decision`、`question-generation-stream`、`question-detail-evaluation`、`report-generation`、`intro-rewrite`
- `src/main/resources/mapper/`
  MyBatis XML

## 当前接口前缀

所有 HTTP 接口统一以：

```text
/api/v1
```

例如：

```text
GET http://localhost:8080/api/v1/system/ping
```

## 常见问题

### `Unknown database 'ai_interview'`

通常是数据库还没初始化，先执行根 README 里的 `db-init`。

### `VerificationCodeStore` Bean 找不到

当前仓库已经通过 `VerificationCodeStoreConfig` 显式处理 Redis / 内存两套实现；如果还报错，优先检查：

- `auth.config` 包是否被正确扫描
- Redis 配置是否被手动排除

### Qdrant 相关 Bean 启动报错

先确认：

- `rag.enabled=true` 时 Qdrant 真的可连
- `QDRANT_PORT` 使用的是 gRPC 端口 `6334`

### 语音能力无法启用

先确认：

- `speech.asr.enabled` / `speech.tts.enabled`
- `AI_BAILIAN_API_KEY` 已导入当前终端
- 浏览器端不是直接访问云端地址，而是先通过后端拿票据
