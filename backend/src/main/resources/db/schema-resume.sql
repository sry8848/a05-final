-- 简历表：多租户，每用户最多一份默认简历
-- 执行前请确保已执行 01-create-database.sql 与 schema-auth.sql

CREATE TABLE IF NOT EXISTS resumes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '简历名称（文件名或用户编辑名）',
    source_type VARCHAR(32) NOT NULL DEFAULT 'file' COMMENT '来源：file=文件上传',
    file_path VARCHAR(512) NULL COMMENT '存储相对路径或标识',
    parse_status VARCHAR(32) NOT NULL DEFAULT 'parsing' COMMENT '解析状态：parsing=解析中, parsed=已完成, failed=失败',
    parsed_text LONGTEXT NULL COMMENT '识别出的文本（用户可编辑）',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认简历，每用户最多 1',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_resumes_user_id (user_id),
    INDEX idx_resumes_user_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历表';
