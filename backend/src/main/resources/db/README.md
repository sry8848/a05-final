# 数据库脚本目录

本目录同时存放两类 SQL：

1. 基础建表脚本  
用于初始化全新数据库，例如 `01-create-database.sql`、`schema-auth.sql`、`schema-interview-v2.sql`

2. 增量迁移脚本  
用于对已经运行中的数据库做结构修正，例如新增字段、删除旧列

## 当前推荐执行顺序

全新初始化：

1. `01-create-database.sql`
2. `schema-auth.sql`
3. `schema-resume.sql`
4. `schema-position.sql`
5. `schema-question-bank.sql`
6. `schema-interview-v2.sql`

说明：

- `db-init` 用于初始化全新数据库
- 如果需要彻底重置开发环境，必须先执行 `DROP DATABASE ai_interview`
- 删除数据库后，再按以上顺序重跑全部初始化脚本

增量迁移：

1. `alter-interview-attempts-add-detail-evaluation.sql`
2. `alter-question-redo-attempts.sql`
3. `alter-interview-remove-legacy-columns.sql`
4. `alter-interview-sessions-upgrade-legacy-schema.sql`（仅当目标库仍停留在最早的 `schema-interview.sql` 时执行）

## 迁移说明

- `alter-interview-remove-legacy-columns.sql`
  - 作用：从现有面试主链路表中删除已经废弃的旧列
  - 前提：目标库已经是 `schema-interview-v2.sql` 体系，而不是最早的简化版 `schema-interview.sql`
  - 特性：脚本按列存在性做检查，可重复执行

- `alter-interview-sessions-upgrade-legacy-schema.sql`
  - 作用：把最早 `schema-interview.sql` 的旧版 `interview_sessions` 平滑升级到当前字段结构
  - 适用：已经有历史会话数据，且线上 / 本地库缺少 `position_code`、`title`、`state_ledger_json` 等新版字段
  - 特性：脚本会回填 `position_code`、`title`、`started_at` / `finished_at`；只有当 `position_code` 全部成功回填后，才会删除 `target_position`
  - 补充：当前后端启动时也会做同等幂等 autopatch；若要提前手动修库，可执行本脚本

- `alter-interview-questions-add-difficulty.sql`
  - 已废弃，保留仅用于历史追溯
  - 新的面试提问链路已经移除 `difficulty`

## 风险提示

如果目标库仍然停留在早期 `schema-interview.sql` 结构，不要直接执行增量删列脚本。  
应先执行 `alter-interview-sessions-upgrade-legacy-schema.sql`，或者升级到当前后端代码后重启，让启动期 autopatch 先把表结构修正到位。
