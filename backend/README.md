# AI 模拟面试系统 - 后端

Spring Boot 3 + MyBatis-Plus + MySQL + Redis + RabbitMQ。

## 模块结构

```
src/main/java/com/a05/aiinterview/
  common/      # 统一响应、异常处理、工具
  auth/        # 注册、验证码、登录、鉴权
  system/      # Ping、环境检测
  resume/      # 简历上传、解析、编辑
  position/    # 岗位与知识域树
  interview/   # 会话、题目、提交并继续
  report/      # 报告、单题详情、AI 追问
  questionbank/# 成长问答库
  profile/     # 个人资料、成长统计
  ai/          # Prompt、模型适配、RAG、AI 调用日志
  admin/       # 后台管理扩展
```

## 运行前准备

- JDK 21
- **MySQL 8**：先创建库并执行建表脚本，否则会报 `Unknown database 'aiinterview'`：
  ```bash
  # 1）创建数据库
  mysql -u root -p < src/main/resources/db/01-create-database.sql
  # 2）进入库并执行鉴权表结构（可选：按需执行各 schema-*.sql）
  mysql -u root -p aiinterview < src/main/resources/db/schema-auth.sql
  ```
  或手动在 MySQL 中执行：
  ```sql
  CREATE DATABASE IF NOT EXISTS aiinterview DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
  USE aiinterview;
  -- 然后执行 schema-auth.sql 中的建表语句
  ```
- Redis（可选：不启用则使用内存验证码存储）
- RabbitMQ

## 运行

```bash
# 开发环境（使用 application-dev.yml）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 或先打包
mvn package
java -jar target/aiinterview-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

## 接口前缀

- context-path: `/api/v1`
- 示例：`GET http://localhost:8080/api/v1/system/ping`

## 配置

- `application.yml`：通用配置
- `application-dev.yml`：开发环境（本地 MySQL/Redis/RabbitMQ）
- 数据库密码等可通过环境变量 `DB_PASSWORD`、`REDIS_PASSWORD` 覆盖

## 常见启动问题

### 1. `Unknown database 'ai_interview'`（或 `aiinterview`）

**原因**：MySQL 中尚未创建应用使用的库名。  
**处理**：在 MySQL 中执行建库（库名需与 `application.yml` 里 `spring.datasource.url` 中的库名一致，例如 `ai_interview` 或 `aiinterview`）：

```sql
CREATE DATABASE IF NOT EXISTS ai_interview DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 然后执行 schema-auth.sql 等建表脚本
```

### 2. `required a bean of type 'VerificationCodeStore' that could not be found`

**原因**：验证码存储有两种实现（Redis / 内存），原先依赖 `@ConditionalOnBean(StringRedisTemplate)` 与 `@ConditionalOnMissingBean(VerificationCodeStore)`。在部分环境下（如 Redis 未启动、Bean 创建顺序等），两个条件都未满足，导致没有任何 `VerificationCodeStore` 实现被注册，`AuthService` 注入失败。

**处理**：已通过 `auth.config.VerificationCodeStoreConfig` 显式提供唯一 Bean：有 Redis 时用 Redis 实现，否则用内存实现。无需再改配置；若仍报错，请确认未排除 `com.a05.aiinterview.auth.config` 包扫描。

### 3. MyBatis 实体或 Mapper 找不到

**原因**：`application.yml` 中 `mybatis-plus.type-aliases-package` 或日志中的包名必须与 Java 包一致（`com.a05.aiinterview`），不能写成 `com.a05.ai_interview`，否则实体别名与日志包路径会失效。
