# 当前仓库结构

本文只描述当前目录结构和职责，不记录初始化阶段设想。

## 顶层目录

```text
.
├── backend/
├── docs/
├── frontend/
├── docker-compose.yml
└── .env.example
```

## 根目录文件

- [README.md](/D:/a05-cursor/README.md)
  仓库总览与本地联调入口
- [docker-compose.yml](/D:/a05-cursor/docker-compose.yml)
  MySQL、Redis、RabbitMQ、Qdrant、db-init 工具服务
- [.env.example](/D:/a05-cursor/.env.example)
  联调环境变量模板

## `frontend/`

当前前端工程。

```text
frontend/
├── src/
│   ├── api/
│   ├── assets/
│   ├── components/
│   ├── services/
│   ├── utils/
│   ├── App.vue
│   └── main.js
├── tests/
├── package.json
├── vite.config.js
└── README.md
```

职责说明：

- `src/api/`
  HTTP 接口封装与 API 基地址解析
- `src/components/`
  用户端与管理端页面组件
- `src/services/`
  ASR、TTS 等运行时服务
- `src/utils/`
  状态整理、视图模型、存储工具
- `tests/`
  前端单元测试

## `backend/`

当前后端工程。

```text
backend/
├── src/main/java/com/a05/aiinterview/
│   ├── admin/
│   ├── ai/
│   ├── auth/
│   ├── common/
│   ├── interview/
│   ├── position/
│   ├── profile/
│   ├── questionbank/
│   ├── rag/
│   ├── resume/
│   ├── speech/
│   └── system/
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   ├── application-dev.yml
│   ├── db/
│   ├── mapper/
│   └── prompts/
├── src/test/
└── README.md
```

职责说明：

- `src/main/resources/db/`
  初始化与增量 SQL
- `src/main/resources/prompts/`
  Prompt 模板
- `src/main/resources/mapper/`
  MyBatis XML
- `src/test/`
  后端自动化测试

## `docs/`

```text
docs/
├── archive/
├── superpowers/
├── README.md
├── api-design.md
├── db-schema.md
├── page-list.md
├── product-scope.md
├── project-structure.md
└── prompt-strategy.md
```

职责说明：

- `docs/*.md`
  现行说明文档
- `docs/archive/`
  历史方案、审计、路线图归档
- `docs/superpowers/plans/`
  执行计划记录
- `docs/superpowers/specs/`
  规格记录

`docs/superpowers/**` 属于工程记录，不应直接当作当前实现说明。
