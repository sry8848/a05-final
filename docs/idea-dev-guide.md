# IntelliJ IDEA 本地开发指南

这份文档只解决一件事：让你分清 `application.yml`、`application-local.yml`、`application-test.yml` 分别是干什么的，并且知道在 IDEA 里该怎么启动项目。

## 1. 先记住这一条

- 手动运行项目，用 `local`
- 运行自动化测试，用 `test`

不要把这两件事混在一起。

## 2. 三个配置文件分别负责什么

### `backend/src/main/resources/application.yml`

这是公共基础配置。

它的作用：

- 放所有环境都可能共用的默认值
- 作为整个项目的基础配置

它不应该做的事：

- 不应该写死 `spring.profiles.active`
- 不应该只为你这台电脑服务
- 不应该承担“测试专用配置”的职责

你可以把它理解成“底座”。

### `backend/src/main/resources/application-local.yml`

这是你在自己电脑上手动启动项目时用的配置。

它的作用：

- 放本地开发时才需要的覆盖项
- 例如本地调试日志、关闭某些本地不想启动的中间件自动配置

当前这个项目里，`local` 的意义主要是：

- 本地启动时排除 RabbitMQ 自动配置
- 保留 Redis 自动配置，避免 `StringRedisTemplate` 缺失
- 保持开发时更容易看日志

重点：

- `local` 不是测试环境
- `local` 要靠 IDEA 明确指定
- 不是靠 `application.yml` 写死

### `backend/src/test/resources/application-test.yml`

这是自动化测试专用配置。

它的作用：

- 给 JUnit 测试使用
- 让测试尽量不要依赖不必要的外部集成

当前这个项目里，`test` 的意义主要是：

- 排除 RabbitMQ 自动配置
- 关闭语音功能
- 强制使用 mock AI

重点：

- `test` 不是给你平时点启动按钮用的
- 只有测试类显式指定 `@ActiveProfiles("test")` 时它才生效

## 3. 现在这个项目正确的运行方式

### 手动启动后端

在 IDEA 里使用 `local`。

步骤：

1. 打开 **Run -> Edit Configurations**
2. 新建一个 **Spring Boot** 配置
3. `Main class` 选择 `com.a05.aiinterview.AiInterviewApplication`
4. `Working directory` 选择 `backend`
5. 在 `Active profiles` 填入 `local`
6. 保存并启动

你真正需要关注的是这一个地方：

```text
Active profiles = local
```

只要这里配对了，你就不用再去代码里写死 `spring.profiles.active`。

### 运行测试

直接运行测试类或 Maven 测试命令即可。

例如：

```bash
mvn test
```

测试会通过测试代码里的 `@ActiveProfiles("test")` 使用 `test` 配置，不需要你手动切换。

## 4. 为什么不把 `local` 写死在 `application.yml`

因为那会让“本地启动”和“测试运行”的边界变模糊。

一旦主配置里写了：

```yaml
spring:
  profiles:
    active: local
```

你就会开始误以为：

- 项目永远应该跑在 `local`
- `test` 好像没什么意义
- 启动失败时也不容易判断到底是 profile 问题还是中间件问题

更规范的方式是：

- 共享配置不写死 profile
- 谁启动，谁明确指定 profile

对你来说，就是：

- IDEA 手动运行时指定 `local`
- 测试代码自己指定 `test`

## 5. 这次报错的根因是什么

之前 `local` 里排除了 Redis 自动配置，但代码里又有服务直接依赖 `StringRedisTemplate`。

结果就是：

- Spring Boot 没有创建 `StringRedisTemplate`
- 但 `TtsService` 还在强依赖它
- 应用在启动阶段就直接失败

这不是 IDEA 的问题，也不是 Spring 的问题，是配置和代码要求互相冲突。

现在已经修正为：

- `local` 仍然可以排除 RabbitMQ
- `local` 不再排除 Redis 自动配置

## 6. 启动成功，不等于所有功能都可用

这是新手最容易误解的一点。

### 情况一：应用根本起不来

这通常是启动阶段就缺少必须的 Bean 或关键配置。

例如：

- MySQL 配置错误且项目启动时就要连数据库
- Redis 自动配置被排除，导致 `StringRedisTemplate` 根本不存在

这种问题会直接表现为：

- 控制台报 `APPLICATION FAILED TO START`

### 情况二：应用能启动，但某个功能 later 才报错

这通常表示：

- Spring 容器已经起来了
- 但是某个中间件在真正使用时才连接失败

例如：

- Redis 服务没启动，但 Bean 已经创建成功
- 当你真正调用验证码、缓存、SSE、TTS 等 Redis 相关功能时，才出现连接错误

所以你要学会区分：

- “项目能启动”
- “某个依赖服务真的可用”

这不是一回事。

## 7. 本地开发时通常需要哪些服务

最常见的是：

- MySQL
- Redis

RabbitMQ 在当前项目里可以先不启动，因为 `local` 已经排除了它的自动配置。  
Qdrant 只有你真的开启 RAG 相关功能时才需要。

## 8. 你在 IDEA 里最推荐的配置

建议只保留一个最常用的启动配置：

- Name: `backend-local`
- Main class: `com.a05.aiinterview.AiInterviewApplication`
- Working directory: `D:\a05-cursor\backend`
- Active profiles: `local`

如果你还需要环境变量，可以再加这些常见值：

```text
DB_PASSWORD=123456
```

如果你只是本地联调，通常还可以继续使用默认 mock AI，不需要立刻配真实 API Key。

## 9. 常见问题怎么判断

### 报 `APPLICATION FAILED TO START`

先看是不是下面这类问题：

- Bean 缺失
- 自动配置被排除了
- 数据源配置有误

这是“启动阶段问题”。

### 报数据库连接失败

优先检查：

- MySQL 是否启动
- 用户名密码是否正确
- 3306 端口是否可访问

### 报 Redis 连接失败

优先检查：

- Redis 服务是否启动
- 6379 端口是否可访问
- 是否配置了密码却没填 `REDIS_PASSWORD`

### 测试跑得和手动启动表现不一样

优先怀疑是不是你把 `local` 和 `test` 混了。

记住：

- 手动运行看 `local`
- 自动化测试看 `test`

## 10. 你现在应该怎么做

你只需要按下面执行：

1. 启动本地 MySQL
2. 启动本地 Redis
3. 在 IDEA 里把 `Active profiles` 设置为 `local`
4. 启动 `AiInterviewApplication`

如果还是报错，再看它属于：

- 启动阶段问题
- 还是功能访问阶段问题

先把这两类分清，再排查才不会乱。
