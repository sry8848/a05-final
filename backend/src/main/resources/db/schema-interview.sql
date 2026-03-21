-- 面试会话主表：记录每一次模拟面试的准备配置与状态
-- 执行前请确保已执行 01-create-database.sql 与 schema-auth.sql
-- 约定：
-- 1. JD（job_description）为可选字段
-- 2. 请求端不传最大题量，服务端内部可使用默认值（如 10）控制题量策略
-- 3. 仅允许从简历库选择简历，resume_id 关联 resumes.id，不接受临时粘贴的 resumeText

CREATE TABLE IF NOT EXISTS interview_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    target_position VARCHAR(128) NOT NULL COMMENT '目标岗位名称或编码',
    experience_level VARCHAR(64) NOT NULL COMMENT '工作年限，例如: 应届 / 1-3年',
    mode VARCHAR(32) NOT NULL COMMENT '面试模式：practice=练习模式, professional=专业模式',
    job_description TEXT NULL COMMENT 'JD 文本（可选）',
    resume_id BIGINT NULL COMMENT '本次面试选用的简历ID（来自简历库）',
    resume_text_snapshot LONGTEXT NULL COMMENT '创建面试时的简历文本快照，来自简历库 parsed_text',
    focus_topics_json TEXT NULL COMMENT '侧重知识点，JSON 数组字符串',
    think_time_limit_seconds INT NULL COMMENT '思考时间（秒），专业模式使用',
    answer_time_limit_seconds INT NULL COMMENT '回答时间（秒），专业模式使用',
    status VARCHAR(32) NOT NULL DEFAULT 'planning' COMMENT '会话状态：planning / in_progress / completed / cancelled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_interview_sessions_user_id (user_id),
    INDEX idx_interview_sessions_status (status),
    INDEX idx_interview_sessions_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试会话表';
