CREATE TABLE IF NOT EXISTS question_redo_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    source_session_id BIGINT NOT NULL COMMENT '原会话 ID',
    source_question_id BIGINT NOT NULL COMMENT '原题目 ID',
    source_snapshot_json JSON NULL COMMENT '冻结原题快照',
    answer_text LONGTEXT NULL COMMENT '重答内容',
    evaluation_status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '单题重答评估状态：pending/generating/ready/failed',
    evaluation_json JSON NULL COMMENT '单题重答评估结构化结果',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_qra_user_source (user_id, source_session_id, source_question_id),
    INDEX idx_qra_source_question (source_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单题重答记录表';
