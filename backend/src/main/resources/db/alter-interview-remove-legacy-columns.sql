-- 清理面试主链路中已废弃的旧列
-- 执行前提：
-- 1. 当前数据库已经使用 schema-interview-v2.sql 体系
-- 2. 本脚本只处理“删旧列”，不负责把早期 schema-interview.sql 自动迁到 v2
-- 3. 脚本为幂等脚本，可重复执行

DROP PROCEDURE IF EXISTS drop_column_if_exists;

DELIMITER $$

CREATE PROCEDURE drop_column_if_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT(
                'ALTER TABLE `',
                REPLACE(p_table_name, '`', '``'),
                '` DROP COLUMN `',
                REPLACE(p_column_name, '`', '``'),
                '`'
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

-- 旧会话难度字段，已从面试主链路移除
CALL drop_column_if_exists('interview_sessions', 'difficulty');

-- 旧知识域深度字段，已由最小账本和覆盖状态替代
CALL drop_column_if_exists('session_skill_states', 'target_depth');
CALL drop_column_if_exists('session_skill_states', 'current_depth');

-- 旧项目锚点 / 深度 / 难度字段，已由 generation_context_json 与 activeItemKey 体系替代
CALL drop_column_if_exists('interview_questions', 'project_id');
CALL drop_column_if_exists('interview_questions', 'target_depth');
CALL drop_column_if_exists('interview_questions', 'difficulty');

DROP PROCEDURE IF EXISTS drop_column_if_exists;
