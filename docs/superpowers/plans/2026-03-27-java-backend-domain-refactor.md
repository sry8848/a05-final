# Java 后端知识域重构实施计划（当前版）

## 目标

在开发阶段一次性完成 Java 后端知识域收口，让主链路只认 `domainCode`。

## 当前原则

- 不做版本并存
- 不做历史脏数据兼容
- 非法知识域直接 Fail Fast
- 运行态、持久化、Prompt、测试统一使用真实小写编码

## 当前 10 域

- `java_core`
- `concurrency`
- `jvm`
- `mysql`
- `redis`
- `spring`
- `mq`
- `microservice`
- `distributed`
- `cs_basics`

## 已落地的关键动作

### 1. 真源覆写

- `schema-position.sql` 已固定为唯一一套 Java 后端 10 域定义
- 旧过宽架构域已删除

### 2. 持久化收口

- 会话知识域状态表使用 `domain_code`
- 题目表使用 `domain_code`
- 题目表的多知识域补充字段使用 `secondary_domain_codes`
- 问答库收藏表使用 `domain_code`

### 3. 运行态收口

- `PlannerDomainNormalizationService` 只接受合法 `domainCode`
- `InterviewSyllabusAssembler` 不再按名称或数字猜测知识域
- `stateLedgerJson` 只接受结构化知识域对象
- 当前题、历史题、剩余知识域菜单统一使用 `domainCode`

### 4. Prompt 与测试收口

- Prompt 示例全部使用真实小写编码
- 测试不再保留旧知识域兜底路径
- 新测试只校验合法编码路径和非法编码拒绝逻辑

## 验收方式

1. 定向测试通过
2. 主链路代码与测试中看不到旧知识域主键字段
3. Prompt 和测试样例中看不到伪知识域编码
4. 文档只保留当前正确口径

## 当前结论

Java 后端知识域重构的主方向已经明确：

- 数据库只持久化知识域编码
- 运行态只流转知识域编码
- AI 契约只暴露知识域编码和展示名
