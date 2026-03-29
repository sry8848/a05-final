-- 成长问答库表（MVP）
CREATE TABLE IF NOT EXISTS question_bank_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    question_id BIGINT NOT NULL COMMENT '题目 ID',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    domain_code VARCHAR(64) NULL COMMENT '知识域 code',
    score DECIMAL(5,2) NULL COMMENT '收藏时分数（无显式真实值可为空）',
    tag VARCHAR(64) NULL COMMENT '用户标签',
    source_snapshot_json JSON NULL COMMENT '题目快照（题干/知识域/回答摘要等）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_qb_user_session_question (user_id, session_id, question_id),
    INDEX idx_qb_user_id (user_id),
    INDEX idx_qb_created_at (created_at),
    INDEX idx_qb_score (score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长问答库收藏表';
