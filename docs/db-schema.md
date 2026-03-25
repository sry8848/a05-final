# 当前数据库结构

当前数据库结构以 [backend/src/main/resources/db](/D:/a05-cursor/backend/src/main/resources/db) 下的 SQL 为准。

## 初始化顺序

全新数据库推荐按以下顺序执行：

1. `01-create-database.sql`
2. `schema-auth.sql`
3. `schema-resume.sql`
4. `schema-position.sql`
5. `schema-question-bank.sql`
6. `schema-interview-v2.sql`

`docker compose --profile tools run --rm db-init` 也是按这个顺序执行。

## 当前实际表

### `schema-auth.sql`

- `users`

说明：

- 当前验证码不落 MySQL 表
- 邮箱验证码实际由 Redis / 内存存储组件承担

### `schema-resume.sql`

- `resumes`

### `schema-position.sql`

- `position_skill_domains`

同时包含岗位知识域种子数据。

### `schema-question-bank.sql`

- `question_bank_items`

### `schema-interview-v2.sql`

- `interview_preferences`
- `interview_sessions`
- `session_skill_states`
- `interview_questions`
- `interview_attempts`
- `question_redo_attempts`
- `interview_reports`
- `ai_invocation_logs`

## 当前核心表说明

### `users`

用户主表，包含：

- 邮箱 / 手机号
- 密码哈希
- 昵称
- 头像地址
- 邮箱 / 手机验证状态
- 账户状态

### `resumes`

简历表，当前字段重点是：

- `user_id`
- `name`
- `source_type`
- `file_path`
- `parse_status`
- `parsed_text`
- `is_default`

当前实现只支持文件上传，不存在独立的“纯文本创建简历”表结构。

### `position_skill_domains`

岗位知识域配置表，包含：

- `position_code`
- `version`
- `domain_code`
- `domain_name`
- `description`
- `sort_order`

### `interview_preferences`

记录用户最近一次面试准备偏好：

- `target_role`
- `experience_level`
- `mode`
- `focus_topics`
- `think_time_limit_seconds`
- `answer_time_limit_seconds`

### `interview_sessions`

面试会话主表，当前重点字段包括：

- `resume_id`
- `target_role`
- `experience_level`
- `mode`
- `job_description`
- `focus_topics`
- `current_question_no`
- `first_question_json`
- `syllabus_json`
- `state_ledger_json`
- `status`
- `started_at`
- `finished_at`

### `session_skill_states`

会话维度的知识域状态表，用于跟踪每场面试对每个知识域的覆盖情况。

### `interview_questions`

动态生成的题目表，当前重点字段包括：

- `question_no`
- `question_type`
- `domain_id`
- `secondary_domain_ids`
- `stem`
- `target_skill`
- `expected_points`
- `status`
- `hint_text`
- `generation_context_json`

### `interview_attempts`

当前主链路回答表，已经取代旧版回答表设定。

重点字段：

- `attempt_id`
- `session_id`
- `question_id`
- `answer_text`
- `is_final`
- `evaluation_json`
- `detail_evaluation_status`
- `detail_evaluation_json`

### `question_redo_attempts`

单题重答表，不污染原始面试会话。

重点字段：

- `source_session_id`
- `source_question_id`
- `source_snapshot_json`
- `answer_text`
- `evaluation_status`
- `evaluation_json`

### `interview_reports`

整场报告表，当前重点字段包括：

- `overall_score`
- `skill_domain_scores`
- `comprehensive_radar_scores`
- `summary`
- `strengths`
- `weaknesses`
- `improvement_suggestions`
- `recommended_topics`
- `interactive_transcript_json`

### `question_bank_items`

问答库收藏表，当前重点字段包括：

- `user_id`
- `question_id`
- `session_id`
- `domain_id`
- `score`
- `tag`
- `source_snapshot_json`

### `ai_invocation_logs`

AI 调用审计表，当前重点字段包括：

- `prompt_code`
- `prompt_version`
- `model_provider`
- `model_name`
- `request_tokens`
- `response_tokens`
- `latency_ms`
- `retrieval_context_json`
- `request_payload_json`
- `response_payload_json`
- `success`
- `error_message`

## 当前迁移脚本

增量迁移脚本位于同一目录。当前推荐关注：

- `alter-interview-attempts-add-detail-evaluation.sql`
- `alter-question-redo-attempts.sql`
- `alter-interview-remove-legacy-columns.sql`
- `alter-interview-experience-level-staff-to-senior.sql`

历史遗留脚本：

- `schema-interview.sql`
  旧版结构，仅用于追溯
- `alter-interview-questions-add-difficulty.sql`
  已废弃，不应作为当前结构依据

## 当前事实边界

现阶段不应再把以下内容写成当前数据库事实：

- 历史验证码表方案
- 旧版回答表作为主链路表
- 历史单题追问消息表方案
- “文本粘贴建简历”对应的独立持久化结构

这些都属于历史方案或未落地设计。
