# a05-cursor

AI 模拟面试系统单仓库项目。

当前仓库包含：

- `frontend/`：Vue 3 + Vite 前端
- `backend/`：Spring Boot 后端
- `docs/`：现行文档、历史归档、工程记录

文档入口见 [docs/README.md](/D:/a05-cursor/docs/README.md)。

## 技术栈

- 前端：Vue 3、Vite、Fetch API
- 后端：Spring Boot 3、MyBatis-Plus、Spring Security、Spring AI、WebFlux SSE
- 基础设施：MySQL、Redis、RabbitMQ、Qdrant
- AI / 语音：阿里云百炼兼容 OpenAI 接口，ASR / TTS 走百炼

## 仓库结构

```text
.
├── backend/
├── docs/
├── frontend/
├── docker-compose.yml
└── .env.example
```

## 先看成功标准

完成本地联调后，至少应看到以下结果：

1. `docker compose ps` 中 `mysql`、`redis`、`rabbitmq`、`qdrant` 处于运行状态
2. 浏览器打开 `http://localhost:6333/dashboard` 能看到 Qdrant 面板
3. 浏览器打开 `http://localhost:8080/api/v1/system/ping` 能收到后端响应
4. 浏览器打开 `http://localhost:5173` 能看到前端页面

## 环境要求

在终端执行：

```bash
java -version
mvn -v
node -v
npm -v
docker -v
docker compose version
```

推荐版本：

- Java 21
- Maven 可正常识别 Java
- Node.js 20+
- Docker Desktop 可正常运行

如果这里有任何一个命令失败，先修好环境再继续。

## 快速启动

### 1. 复制环境变量模板

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

macOS / Linux / Git Bash：

```bash
cp .env.example .env
```

首次联调通常只需要先填写：

```dotenv
AI_BAILIAN_API_KEY=你的百炼Key
```

### 2. 导入 `.env` 到当前终端

根目录 `.env` 会被 `docker compose` 和前端读取，但后端本地 `mvn spring-boot:run` 不会自动读取，所以需要先导入。

Windows PowerShell：

```powershell
Get-Content .env | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  if ($name -and $value -ne $null) {
    Set-Item -Path "Env:$name" -Value $value
  }
}
```

macOS / Linux / Git Bash：

```bash
set -a
source .env
set +a
```

### 3. 启动基础服务

```bash
docker compose up -d mysql redis rabbitmq qdrant
docker compose ps
```

### 4. 初始化数据库

首次启动执行：

```bash
docker compose --profile tools run --rm db-init
```

这会按当前推荐顺序执行数据库脚本。

### 5. 启动后端

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

配置文件职责：

- `backend/src/main/resources/application.yml`：公共基础配置
- `backend/src/main/resources/application-local.yml`：本地真实联调配置
- `backend/src/main/resources/application-dev.yml`：开发期便捷配置
- `backend/src/test/resources/application-test.yml`：自动化测试专用

### 6. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认通过 Vite 代理访问 `/api/v1`；如果根目录 `.env` 配了 `VITE_API_BASE_URL`，则直接访问该后端地址。

## IntelliJ IDEA 启动后端

如果你用 IDEA 跑后端，建议这样配置：

1. 打开 `backend` 工程
2. `Run` -> `Edit Configurations`
3. 新建或选中 Spring Boot 启动项
4. `Active profiles` 填 `local`
5. `Environment variables` 填入 `.env` 中后端需要的变量

最少要保证：

- `AI_BAILIAN_API_KEY`
- 需要时的 `RAG_ENABLED`、`QDRANT_*`
- 需要时的 `ASR_*`、`TTS_*`

## 最小联调检查

建议按这个顺序验证：

1. `http://localhost:6333/dashboard`
2. `http://localhost:8080/api/v1/system/ping`
3. `http://localhost:5173`
4. 登录用户端页面，检查简历、面试、报告等基础流转
5. 如需验证管理端，确认管理员配置和管理后台接口可访问

## 常见问题

### `Unknown database 'ai_interview'`

通常是还没有执行数据库初始化。

```bash
docker compose --profile tools run --rm db-init
```

### 后端提示缺少 `AI_BAILIAN_API_KEY`

说明你只改了 `.env`，但没有把变量导入当前终端，或者 IDEA 启动项没填环境变量。

### Qdrant 页面能打开，但 RAG 没生效

先检查：

- `.env` 中 `RAG_ENABLED=true`
- `.env` 中 `QDRANT_PORT=6334`
- 后端实际使用的是 `local` profile

### 前端页面能打开，但接口失败

先单独确认：

- 后端已成功启动
- `http://localhost:8080/api/v1/system/ping` 可访问
- 前端当前使用的 API 基地址与你预期一致

## 相关文档

- 文档总览：[docs/README.md](/D:/a05-cursor/docs/README.md)
- 后端说明：[backend/README.md](/D:/a05-cursor/backend/README.md)
- 前端说明：[frontend/README.md](/D:/a05-cursor/frontend/README.md)
- API 文档：[docs/api-design.md](/D:/a05-cursor/docs/api-design.md)
- 页面与状态流转：[docs/page-list.md](/D:/a05-cursor/docs/page-list.md)
- 数据模型：[docs/db-schema.md](/D:/a05-cursor/docs/db-schema.md)
