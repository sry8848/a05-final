-- 作用：
-- 1. 将历史遗留的 STAFF 经验等级折叠为 SENIOR
-- 2. 配合代码层移除 STAFF 枚举，避免旧数据继续向下游 prompt 透传
--
-- 使用前提：
-- - 当前数据库已存在 interview_sessions / interview_preferences
-- - 仅做数据修正，不修改表结构

UPDATE interview_sessions
SET experience_level = 'SENIOR'
WHERE UPPER(TRIM(experience_level)) = 'STAFF';

UPDATE interview_preferences
SET experience_level = 'SENIOR'
WHERE UPPER(TRIM(experience_level)) = 'STAFF';
