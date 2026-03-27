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

## 迁移说明

- `alter-interview-remove-legacy-columns.sql`
  - 作用：从现有面试主链路表中删除已经废弃的旧列
  - 前提：目标库已经是 `schema-interview-v2.sql` 体系，而不是最早的简化版 `schema-interview.sql`
  - 特性：脚本按列存在性做检查，可重复执行

- `alter-interview-questions-add-difficulty.sql`
  - 已废弃，保留仅用于历史追溯
  - 新的面试提问链路已经移除 `difficulty`

## 风险提示

如果目标库仍然停留在早期 `schema-interview.sql` 结构，不要直接执行增量删列脚本。  
那种情况需要单独设计“旧简化表 -> v2 完整表”的迁移方案，不能用这次的清理脚本替代。
