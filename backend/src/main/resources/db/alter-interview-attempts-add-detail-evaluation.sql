-- 为 interview_attempts 表添加详细评估相关字段
-- 执行时间：2026-03-14
-- 原因：实体类新增了 detailEvaluationStatus 和 detailEvaluationJson 字段，数据库表需要同步

ALTER TABLE interview_attempts
    ADD COLUMN detail_evaluation_status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '单题详细评估状态：pending/generating/ready/failed' AFTER evaluation_json,
    ADD COLUMN detail_evaluation_json JSON NULL COMMENT '单题详细评估结构化结果' AFTER detail_evaluation_status;
