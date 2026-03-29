package com.a05.aiinterview.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时自动把旧版 interview_sessions 表升级到当前代码要求的结构，
 * 避免联调环境漏跑“旧简化表 -> v2 完整表”迁移脚本后在运行时炸库。
 *
 * <p>处理原则：
 * 1. 只做幂等补列 / 数据搬运 / 旧列删除，不改业务层兼容逻辑；
 * 2. 旧 target_position / focus_topics_json 等历史列在迁移完成后物理删除；
 * 3. 仅在表已存在时执行；全新数据库初始化仍以 db 脚本为准。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewSessionSchemaAutoPatchRunner implements ApplicationRunner {

    private static final String TABLE_NAME = "interview_sessions";
    private static final String LEGACY_POSITION_COLUMN = "target_position";
    private static final String CURRENT_POSITION_COLUMN = "position_code";
    private static final String LEGACY_FOCUS_TOPICS_COLUMN = "focus_topics_json";
    private static final String CURRENT_FOCUS_TOPICS_COLUMN = "focus_topics";
    private static final String LEGACY_RESUME_SNAPSHOT_COLUMN = "resume_text_snapshot";
    private static final String TITLE_COLUMN = "title";
    private static final String CURRENT_QUESTION_NO_COLUMN = "current_question_no";
    private static final String CONTEXT_WINDOW_SIZE_COLUMN = "context_window_size";
    private static final String FIRST_QUESTION_JSON_COLUMN = "first_question_json";
    private static final String SYLLABUS_JSON_COLUMN = "syllabus_json";
    private static final String STATE_LEDGER_JSON_COLUMN = "state_ledger_json";
    private static final String MODEL_PROVIDER_COLUMN = "model_provider";
    private static final String MODEL_NAME_COLUMN = "model_name";
    private static final String STARTED_AT_COLUMN = "started_at";
    private static final String FINISHED_AT_COLUMN = "finished_at";

    private static final String ADD_POSITION_CODE_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN position_code VARCHAR(64) NULL "
                    + "COMMENT '目标岗位枚举，如 JAVA_BACKEND'";
    private static final String BACKFILL_POSITION_CODE_SQL = """
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
              AND target_position IS NOT NULL
            """;
    private static final String ENFORCE_POSITION_CODE_NOT_NULL_SQL =
            "ALTER TABLE interview_sessions MODIFY COLUMN position_code VARCHAR(64) NOT NULL "
                    + "COMMENT '目标岗位枚举，如 JAVA_BACKEND'";
    private static final String ADD_TITLE_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN title VARCHAR(255) NOT NULL DEFAULT '' "
                    + "COMMENT '本场标题，如 Java 后端开发模拟面试'";
    private static final String BACKFILL_TITLE_SQL = """
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
            WHERE title IS NULL OR TRIM(title) = ''
            """;
    private static final String NORMALIZE_CANCELLED_STATUS_SQL =
            "UPDATE interview_sessions SET status = 'aborted' WHERE status = 'cancelled'";
    private static final String ADD_FOCUS_TOPICS_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN focus_topics VARCHAR(512) NULL "
                    + "COMMENT '用户指定侧重点'";
    private static final String BACKFILL_FOCUS_TOPICS_SQL = """
            UPDATE interview_sessions
            SET focus_topics = COALESCE(
                NULLIF(TRIM(focus_topics), ''),
                NULLIF(TRIM(BOTH '[]' FROM REPLACE(REPLACE(focus_topics_json, '"', ''), ',', ' / ')), '')
            )
            WHERE (focus_topics IS NULL OR TRIM(focus_topics) = '')
              AND focus_topics_json IS NOT NULL
            """;
    private static final String ADD_CURRENT_QUESTION_NO_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN current_question_no INT NOT NULL DEFAULT 0 "
                    + "COMMENT '当前题号（从 1 开始）'";
    private static final String ADD_CONTEXT_WINDOW_SIZE_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN context_window_size INT NOT NULL DEFAULT 5 "
                    + "COMMENT '最近 x 题读取完整 Q/A 的窗口大小'";
    private static final String ADD_FIRST_QUESTION_JSON_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN first_question_json JSON NULL "
                    + "COMMENT '第一题快照'";
    private static final String ADD_SYLLABUS_JSON_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN syllabus_json JSON NULL "
                    + "COMMENT 'Planner 生成的主考纲'";
    private static final String ADD_STATE_LEDGER_JSON_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN state_ledger_json JSON NULL "
                    + "COMMENT '状态账本'";
    private static final String ADD_MODEL_PROVIDER_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN model_provider VARCHAR(64) NULL "
                    + "COMMENT '模型供应商'";
    private static final String ADD_MODEL_NAME_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN model_name VARCHAR(128) NULL "
                    + "COMMENT '模型名称'";
    private static final String ADD_STARTED_AT_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN started_at DATETIME NULL "
                    + "COMMENT '正式开始时间'";
    private static final String ADD_FINISHED_AT_SQL =
            "ALTER TABLE interview_sessions ADD COLUMN finished_at DATETIME NULL "
                    + "COMMENT '面试结束时间'";
    private static final String BACKFILL_STARTED_AT_SQL = """
            UPDATE interview_sessions
            SET started_at = COALESCE(started_at, created_at)
            WHERE started_at IS NULL
            """;
    private static final String BACKFILL_FINISHED_AT_SQL = """
            UPDATE interview_sessions
            SET finished_at = COALESCE(finished_at, updated_at)
            WHERE finished_at IS NULL
              AND status IN ('completed', 'aborted')
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists(TABLE_NAME)) {
            log.warn("数据库表不存在，跳过 interview_sessions 自动迁移, table={}", TABLE_NAME);
            return;
        }

        boolean hasLegacyPosition = columnExists(TABLE_NAME, LEGACY_POSITION_COLUMN);
        boolean hasLegacyFocusTopics = columnExists(TABLE_NAME, LEGACY_FOCUS_TOPICS_COLUMN);
        boolean hasLegacyResumeSnapshot = columnExists(TABLE_NAME, LEGACY_RESUME_SNAPSHOT_COLUMN);

        ensureColumn(TITLE_COLUMN, ADD_TITLE_SQL);
        ensurePositionCodeColumn(hasLegacyPosition);
        execute(NORMALIZE_CANCELLED_STATUS_SQL);
        execute(BACKFILL_TITLE_SQL);

        ensureColumn(CURRENT_FOCUS_TOPICS_COLUMN, ADD_FOCUS_TOPICS_SQL);
        if (hasLegacyFocusTopics) {
            execute(BACKFILL_FOCUS_TOPICS_SQL);
        }

        ensureColumn(CURRENT_QUESTION_NO_COLUMN, ADD_CURRENT_QUESTION_NO_SQL);
        ensureColumn(CONTEXT_WINDOW_SIZE_COLUMN, ADD_CONTEXT_WINDOW_SIZE_SQL);
        ensureColumn(FIRST_QUESTION_JSON_COLUMN, ADD_FIRST_QUESTION_JSON_SQL);
        ensureColumn(SYLLABUS_JSON_COLUMN, ADD_SYLLABUS_JSON_SQL);
        ensureColumn(STATE_LEDGER_JSON_COLUMN, ADD_STATE_LEDGER_JSON_SQL);
        ensureColumn(MODEL_PROVIDER_COLUMN, ADD_MODEL_PROVIDER_SQL);
        ensureColumn(MODEL_NAME_COLUMN, ADD_MODEL_NAME_SQL);
        ensureColumn(STARTED_AT_COLUMN, ADD_STARTED_AT_SQL);
        ensureColumn(FINISHED_AT_COLUMN, ADD_FINISHED_AT_SQL);
        execute(BACKFILL_STARTED_AT_SQL);
        execute(BACKFILL_FINISHED_AT_SQL);

        if (hasLegacyFocusTopics) {
            dropColumn(LEGACY_FOCUS_TOPICS_COLUMN);
        }
        if (hasLegacyResumeSnapshot) {
            dropColumn(LEGACY_RESUME_SNAPSHOT_COLUMN);
        }
    }

    private void ensurePositionCodeColumn(boolean hasLegacyPosition) {
        boolean hasCurrentPosition = columnExists(TABLE_NAME, CURRENT_POSITION_COLUMN);
        if (!hasCurrentPosition) {
            execute(ADD_POSITION_CODE_SQL);
        }
        if (hasLegacyPosition) {
            execute(BACKFILL_POSITION_CODE_SQL);
        }

        long unresolvedCount = countBlankPositionCodeRows();
        if (unresolvedCount == 0L) {
            execute(ENFORCE_POSITION_CODE_NOT_NULL_SQL);
            if (hasLegacyPosition) {
                dropColumn(LEGACY_POSITION_COLUMN);
            }
            return;
        }

        if (hasLegacyPosition) {
            log.warn("position_code 仍有 {} 条记录无法回填，跳过 NOT NULL 收紧并保留 target_position 供人工修复",
                    unresolvedCount);
            return;
        }

        log.warn("position_code 仍有 {} 条记录为空，且无 target_position 可回填；跳过 NOT NULL 收紧", unresolvedCount);
    }

    private void ensureColumn(String columnName, String alterSql) {
        if (columnExists(TABLE_NAME, columnName)) {
            return;
        }
        execute(alterSql);
    }

    private void dropColumn(String columnName) {
        jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " DROP COLUMN " + columnName);
        log.info("数据库旧列删除完成, table={}, column={}", TABLE_NAME, columnName);
    }

    private void execute(String sql) {
        jdbcTemplate.execute(sql);
    }

    private boolean tableExists(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SHOW TABLES LIKE ?",
                tableName
        );
        return rows != null && !rows.isEmpty();
    }

    private boolean columnExists(String tableName, String columnName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SHOW COLUMNS FROM " + tableName + " LIKE ?",
                columnName
        );
        return rows != null && !rows.isEmpty();
    }

    private long countBlankPositionCodeRows() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM interview_sessions WHERE position_code IS NULL OR TRIM(position_code) = ''",
                Long.class
        );
        return count == null ? 0L : count;
    }
}
