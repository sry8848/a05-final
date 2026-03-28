-- 将早期 schema-interview.sql 的简化版 interview_sessions
-- 升级为当前后端代码要求的字段结构。
--
-- 适用场景：
-- 1. 数据库里已经存在旧版 interview_sessions 表
-- 2. 表中仍使用 target_position / focus_topics_json / resume_text_snapshot 等历史列
-- 3. 当前后端代码已经切到 position_code / title / state_ledger_json 等新版字段
--
-- 风险说明：
-- 1. 本脚本只升级 interview_sessions 主表，不负责其他新表初始化
-- 2. 脚本会物理删除 target_position / focus_topics_json / resume_text_snapshot 历史列
-- 3. 执行前建议先备份数据库

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS drop_column_if_exists;
DROP PROCEDURE IF EXISTS upgrade_legacy_interview_sessions;

DELIMITER $$

CREATE PROCEDURE add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT(
                'ALTER TABLE `',
                REPLACE(p_table_name, '`', '``'),
                '` ADD COLUMN ',
                p_column_definition
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

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

CREATE PROCEDURE upgrade_legacy_interview_sessions()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'interview_sessions'
    ) THEN
        CALL add_column_if_missing(
                'interview_sessions',
                'title',
                '`title` VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''本场标题，如 Java 后端开发模拟面试'''
        );

        CALL add_column_if_missing(
                'interview_sessions',
                'position_code',
                '`position_code` VARCHAR(64) NULL COMMENT ''目标岗位枚举，如 JAVA_BACKEND'''
        );

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'interview_sessions'
              AND column_name = 'target_position'
        ) THEN
            UPDATE interview_sessions
            SET position_code = CASE
                WHEN UPPER(REPLACE(REPLACE(REPLACE(TRIM(target_position), '-', '_'), ' ', ''), '　', '')) IN ('JAVA_BACKEND', 'JAVA', 'JAVA后端', 'JAVA后端开发', 'JAVA后端开发工程师', '后端', '后端开发', '后端开发工程师')
                    THEN 'JAVA_BACKEND'
                WHEN UPPER(REPLACE(REPLACE(REPLACE(TRIM(target_position), '-', '_'), ' ', ''), '　', '')) IN ('GO_BACKEND', 'GO', 'GO后端', 'GO后端开发', 'GO后端开发工程师')
                    THEN 'GO_BACKEND'
                WHEN UPPER(REPLACE(REPLACE(REPLACE(TRIM(target_position), '-', '_'), ' ', ''), '　', '')) IN ('FRONTEND', '前端', '前端开发', '前端开发工程师', '前端工程师')
                    THEN 'FRONTEND'
                WHEN UPPER(REPLACE(REPLACE(REPLACE(TRIM(target_position), '-', '_'), ' ', ''), '　', '')) IN ('DATA_ENGINEER', '数据工程师', '数据开发', '数据开发工程师')
                    THEN 'DATA_ENGINEER'
                WHEN UPPER(REPLACE(REPLACE(REPLACE(TRIM(target_position), '-', '_'), ' ', ''), '　', '')) IN ('QA', '测试', '测试工程师', '测试开发', '测试开发工程师')
                    THEN 'QA'
                WHEN UPPER(REPLACE(REPLACE(REPLACE(TRIM(target_position), '-', '_'), ' ', ''), '　', '')) IN ('DEVOPS', 'DEV_OPS', '运维', '运维工程师', '运维开发')
                    THEN 'DEVOPS'
                ELSE TRIM(target_position)
            END
            WHERE (position_code IS NULL OR TRIM(position_code) = '')
              AND target_position IS NOT NULL;

            IF (
                SELECT COUNT(1)
                FROM interview_sessions
                WHERE position_code IS NULL OR TRIM(position_code) = ''
            ) = 0 THEN
                ALTER TABLE interview_sessions
                    MODIFY COLUMN position_code VARCHAR(64) NOT NULL COMMENT '目标岗位枚举，如 JAVA_BACKEND';

                CALL drop_column_if_exists('interview_sessions', 'target_position');
            END IF;
        END IF;

        UPDATE interview_sessions
        SET status = 'aborted'
        WHERE status = 'cancelled';

        UPDATE interview_sessions
        SET title = CASE
            WHEN position_code = 'JAVA_BACKEND' THEN CONCAT('Java 后端开发 模拟面试 - ', DATE_FORMAT(COALESCE(created_at, NOW()), '%Y-%m-%d'))
            WHEN position_code = 'GO_BACKEND' THEN CONCAT('Go 后端开发 模拟面试 - ', DATE_FORMAT(COALESCE(created_at, NOW()), '%Y-%m-%d'))
            WHEN position_code = 'FRONTEND' THEN CONCAT('前端开发 模拟面试 - ', DATE_FORMAT(COALESCE(created_at, NOW()), '%Y-%m-%d'))
            WHEN position_code = 'DATA_ENGINEER' THEN CONCAT('数据工程师 模拟面试 - ', DATE_FORMAT(COALESCE(created_at, NOW()), '%Y-%m-%d'))
            WHEN position_code = 'QA' THEN CONCAT('测试工程师 模拟面试 - ', DATE_FORMAT(COALESCE(created_at, NOW()), '%Y-%m-%d'))
            WHEN position_code = 'DEVOPS' THEN CONCAT('DevOps 工程师 模拟面试 - ', DATE_FORMAT(COALESCE(created_at, NOW()), '%Y-%m-%d'))
            ELSE CONCAT(COALESCE(NULLIF(TRIM(position_code), ''), '模拟面试'), ' 模拟面试 - ', DATE_FORMAT(COALESCE(created_at, NOW()), '%Y-%m-%d'))
        END
        WHERE title IS NULL OR TRIM(title) = '';

        CALL add_column_if_missing(
                'interview_sessions',
                'focus_topics',
                '`focus_topics` VARCHAR(512) NULL COMMENT ''用户指定侧重点'''
        );

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'interview_sessions'
              AND column_name = 'focus_topics_json'
        ) THEN
            UPDATE interview_sessions
            SET focus_topics = COALESCE(
                NULLIF(TRIM(focus_topics), ''),
                NULLIF(TRIM(BOTH '[]' FROM REPLACE(REPLACE(focus_topics_json, '"', ''), ',', ' / ')), '')
            )
            WHERE (focus_topics IS NULL OR TRIM(focus_topics) = '')
              AND focus_topics_json IS NOT NULL;

            CALL drop_column_if_exists('interview_sessions', 'focus_topics_json');
        END IF;

        CALL add_column_if_missing(
                'interview_sessions',
                'current_question_no',
                '`current_question_no` INT NOT NULL DEFAULT 0 COMMENT ''当前题号（从 1 开始）'''
        );
        CALL add_column_if_missing(
                'interview_sessions',
                'context_window_size',
                '`context_window_size` INT NOT NULL DEFAULT 5 COMMENT ''最近 x 题读取完整 Q/A 的窗口大小'''
        );
        CALL add_column_if_missing(
                'interview_sessions',
                'first_question_json',
                '`first_question_json` JSON NULL COMMENT ''第一题快照'''
        );
        CALL add_column_if_missing(
                'interview_sessions',
                'syllabus_json',
                '`syllabus_json` JSON NULL COMMENT ''Planner 生成的主考纲'''
        );
        CALL add_column_if_missing(
                'interview_sessions',
                'state_ledger_json',
                '`state_ledger_json` JSON NULL COMMENT ''状态账本'''
        );
        CALL add_column_if_missing(
                'interview_sessions',
                'model_provider',
                '`model_provider` VARCHAR(64) NULL COMMENT ''模型供应商'''
        );
        CALL add_column_if_missing(
                'interview_sessions',
                'model_name',
                '`model_name` VARCHAR(128) NULL COMMENT ''模型名称'''
        );
        CALL add_column_if_missing(
                'interview_sessions',
                'started_at',
                '`started_at` DATETIME NULL COMMENT ''正式开始时间'''
        );
        CALL add_column_if_missing(
                'interview_sessions',
                'finished_at',
                '`finished_at` DATETIME NULL COMMENT ''面试结束时间'''
        );

        UPDATE interview_sessions
        SET started_at = COALESCE(started_at, created_at)
        WHERE started_at IS NULL;

        UPDATE interview_sessions
        SET finished_at = COALESCE(finished_at, updated_at)
        WHERE finished_at IS NULL
          AND status IN ('completed', 'aborted');

        CALL drop_column_if_exists('interview_sessions', 'resume_text_snapshot');
    END IF;
END $$

DELIMITER ;

CALL upgrade_legacy_interview_sessions();

DROP PROCEDURE IF EXISTS upgrade_legacy_interview_sessions;
DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS drop_column_if_exists;
