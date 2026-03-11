-- 鉴权相关表（与 docs/db-schema.md 一致）
-- 执行前请先创建库：CREATE DATABASE IF NOT EXISTS aiinterview DEFAULT CHARSET utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(128) NULL UNIQUE COMMENT '邮箱',
    phone VARCHAR(32) NULL UNIQUE COMMENT '手机号',
    password_hash VARCHAR(255) NULL COMMENT '密码哈希',
    nickname VARCHAR(64) NOT NULL DEFAULT '' COMMENT '昵称',
    avatar_url VARCHAR(255) NULL COMMENT '头像地址',
    email_verified TINYINT NOT NULL DEFAULT 0 COMMENT '邮箱是否验证',
    phone_verified TINYINT NOT NULL DEFAULT 0 COMMENT '手机号是否验证',
    status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '账户状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX uk_users_email (email),
    INDEX uk_users_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
