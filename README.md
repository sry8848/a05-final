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
- 邮件：SMTP，用于邮箱验证码登录 / 注册

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
4. 调用 `POST /api/v1/auth/email-code/send` 后，你的邮箱能收到验证码
5. 浏览器打开 `http://localhost:5173` 能看到前端页面

如果上面任何一项没做到，就不要往“项目已经跑通”这个结论上靠。

## 这份文档适合谁

这份文档按“计算机系大一学生第一次部署这个项目”来写。

你不需要一开始就理解所有架构名词，但你要记住一个原则：

- 每一步成功了再做下一步
- 当前步骤失败时，先看本页对应的排查说明，不要跳着改

## 先认识几个词

- `Spring Profile`
  后端的“运行模式开关”。这里我们统一用 `local`，表示本地真实联调模式。
- `Vite 代理`
  前端运行在 `5173`，后端运行在 `8080`。浏览器直接跨端口请求会遇到跨域问题，所以前端开发服务器会帮你转发请求。
- `RAG`
  模型回答前先查知识库，再结合知识生成回答。
- `Qdrant`
  存放知识向量的数据库，RAG 要用它。
- `gRPC 6334`
  这是后端连接 Qdrant 用的端口，不是给你在浏览器里打开页面的。
- `SMTP`
  邮件发送协议。这个项目用它给用户发送邮箱验证码。

## 第 0 步：环境自检

先在终端执行：

```bash
java -version
mvn -v
node -v
npm -v
docker -v
docker compose version
```

推荐状态：

- Java 21
- Maven 能识别到 Java 21
- Node.js 20+
- Docker Desktop 已经正常启动
- `docker compose` 命令可用

如果这里有任何一个命令失败，先修环境，不要继续。

Windows 同学额外注意：

- Docker Desktop 通常需要 WSL2 或 Hyper-V
- Maven 下载依赖很慢时，通常要自己配置镜像源
- `JAVA_HOME` 配错会直接导致 `mvn -v` 失败

## 第 1 步：复制 `.env` 模板

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

macOS / Linux / Git Bash：

```bash
cp .env.example .env
```

## 第 2 步：填写 `.env`

第一次部署时，至少要认真填写下面这些变量：

```dotenv
AI_BAILIAN_API_KEY=

SMTP_HOST=
SMTP_PORT=
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=
```

其余变量如果你不确定，先保持 `.env.example` 默认值。

当前推荐主流程里，百炼大模型、ASR、TTS 默认共用同一个 `AI_BAILIAN_API_KEY`，所以你第一次部署通常不需要再找第二套、第三套 Key。
如果你后面要切到别的 OpenAI-compatible 服务，再额外覆盖 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL` 即可。

### SMTP 为什么现在就必须填

这个项目的邮箱验证码功能依赖 SMTP。

更关键的是：后端启动时默认会做一次 SMTP 连通 / 认证检查。也就是说，如果 `SMTP` 没配好，后端不是“发验证码时报错”，而是可能在启动阶段就直接失败。

### SMTP 应该怎么填

- `SMTP_HOST`
  邮箱服务商给你的 SMTP 服务器地址，例如 `smtp.qq.com`
- `SMTP_PORT`
  常见是 `587` 或 `465`
- `SMTP_USERNAME`
  发件邮箱账号，通常就是完整邮箱地址
- `SMTP_PASSWORD`
  很多邮箱这里填的不是登录密码，而是 SMTP 授权码 / 应用专用密码
- `SMTP_FROM`
  发件人地址，通常和 `SMTP_USERNAME` 相同

### `587` 和 `465` 怎么选

- 如果你的邮箱服务商文档写的是 `587 + STARTTLS`，通常使用：

```dotenv
SMTP_PORT=587
SMTP_AUTH=true
SMTP_STARTTLS_ENABLE=true
SMTP_SSL_ENABLE=false
```

- 如果你的邮箱服务商文档写的是 `465 + SSL`，通常使用：

```dotenv
SMTP_PORT=465
SMTP_AUTH=true
SMTP_STARTTLS_ENABLE=false
SMTP_SSL_ENABLE=true
```

不要把这两套参数混着填。

## 第 3 步：把 `.env` 导入当前终端

根目录 `.env` 会被 `docker compose` 和前端读取，但后端本地 `mvn spring-boot:run` 不会自动读取。

原因很简单：Maven 启动 Spring Boot 时，只会继承你当前终端里已经存在的环境变量，不会主动去根目录帮你解析 `.env` 文件。

所以你改完 `.env` 后，必须做这一步。

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

如果你后面又修改了 `.env`，要重新导入一次。

## 第 4 步：启动基础服务

执行：

```bash
docker compose up -d mysql redis rabbitmq qdrant
docker compose ps
```

成功标准：

- 能看到 `mysql`、`redis`、`rabbitmq`、`qdrant` 处于运行状态

如果失败，先检查：

- Docker Desktop 是否真的启动了
- 端口 `3306`、`6379`、`5672`、`15672`、`6333`、`6334` 是否被占用

## 第 5 步：初始化数据库

首次启动执行：

```bash
docker compose --profile tools run --rm db-init
```

这一步会创建数据库并执行初始化脚本。

如果这一步失败，不要继续启动后端。

## 第 6 步：启动后端

执行：

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

这里的 `local` 就是本地真实联调模式。

配置文件职责：

- `backend/src/main/resources/application.yml`：公共基础配置
- `backend/src/main/resources/application-local.yml`：本地真实联调配置
- `backend/src/main/resources/application-dev.yml`：开发期便捷配置
- `backend/src/test/resources/application-test.yml`：自动化测试专用

### 如果你用 IntelliJ IDEA 启动后端

路径是：

`Run -> Edit Configurations -> 选中 Spring Boot 启动项`

然后检查这 2 个地方：

1. `Active profiles` 填 `local`
2. `Environment variables` 填入 `.env` 中后端需要的变量

至少要保证这些变量存在：

- `AI_BAILIAN_API_KEY`
- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `SMTP_FROM`
- `RAG_ENABLED`
- `QDRANT_HOST`
- `QDRANT_PORT`
- `ASR_ENABLED`
- `TTS_ENABLED`

### 后端启动成功的标志

- 终端没有立刻退出
- 浏览器打开 `http://localhost:8080/api/v1/system/ping` 能收到响应

### 如果后端启动时报 SMTP 错

看到这些报错时，不要继续下一步：

- `SMTP_HOST 未配置`
- `SMTP_PORT 未配置或无效`
- `SMTP_USERNAME 未配置`
- `SMTP_PASSWORD 未配置`
- `SMTP_FROM 未配置`
- `SMTP 连接或认证失败，请检查邮箱配置`

这说明你要先回到第 2 步和第 3 步，检查 SMTP 配置和环境变量导入。

## 第 7 步：先单独验证 SMTP

不要一上来就点前端页面。先单独验证后端能不能把验证码邮件发出去。

PowerShell：

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/email-code/send" `
  -ContentType "application/json" `
  -Body '{"email":"你的邮箱@example.com","scene":"login"}'
```

macOS / Linux / Git Bash：

```bash
curl -X POST "http://localhost:8080/api/v1/auth/email-code/send" \
  -H "Content-Type: application/json" \
  -d '{"email":"你的邮箱@example.com","scene":"login"}'
```

说明：

- `scene` 只能填 `login` 或 `register`
- 这里建议先用你自己的真实邮箱测试

成功标准：

1. 接口返回成功
2. 你的邮箱真的收到验证码邮件

如果接口成功但邮箱没收到，先检查垃圾邮件箱，再检查 SMTP 服务商限制。

## 第 8 步：确认 Qdrant 和 RAG

浏览器打开：

```text
http://localhost:6333/dashboard
```

能打开面板，说明 Qdrant 服务本身已经起来了。

但这不等于 RAG 已经有知识数据。

如果你想第一次就验证知识库链路，可以在 `.env` 中临时设置：

```dotenv
RAG_INIT_SAMPLE=true
```

成功启动一次后，建议再改回 `false`，避免重复写入样本数据。

## 第 9 步：启动前端

执行：

```bash
cd frontend
npm install
npm run dev
```

前端默认通过 Vite 代理访问 `/api/v1`；如果根目录 `.env` 配了 `VITE_API_BASE_URL`，则直接访问该后端地址。

## 第 10 步：完整联调检查

建议按这个顺序验证：

1. `http://localhost:6333/dashboard`
2. `http://localhost:8080/api/v1/system/ping`
3. `POST /api/v1/auth/email-code/send` 能把验证码发到你的邮箱
4. `http://localhost:5173`
5. 登录用户端页面，检查简历、面试、报告等基础流转
6. 如需验证管理端，确认管理员配置和管理后台接口可访问

## 常见问题

### `Unknown database 'ai_interview'`

通常是还没有执行数据库初始化。

```bash
docker compose --profile tools run --rm db-init
```

### 后端提示缺少 `AI_BAILIAN_API_KEY`

说明你只改了 `.env`，但没有把变量导入当前终端，或者 IDEA 启动项没填环境变量。
如果你不是走百炼，而是接了别的 OpenAI-compatible 服务，也可以检查 `OPENAI_API_KEY` 是否已经正确注入。

### 后端启动时报 SMTP 相关错误

优先检查这几件事：

1. 你改完 `.env` 后，是否重新导入了环境变量
2. `SMTP_PASSWORD` 填的是不是授权码，而不是邮箱网页登录密码
3. `SMTP_FROM` 是否真的填了发件邮箱
4. `587 + STARTTLS` 和 `465 + SSL` 是否配反了

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
