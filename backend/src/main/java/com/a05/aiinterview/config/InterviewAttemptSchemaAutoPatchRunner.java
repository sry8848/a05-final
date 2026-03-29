package com.a05.aiinterview.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 启动时自动补齐 interview_attempts 详细评估字段，避免联调环境因漏跑 SQL 产生运行时故障。
 * 仅做幂等补列，不修改已有字段定义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewAttemptSchemaAutoPatchRunner implements ApplicationRunner {

    private static final String TABLE_NAME = "interview_attempts";
    private static final String DETAIL_STATUS_COLUMN = "detail_evaluation_status";
    private static final String DETAIL_JSON_COLUMN = "detail_evaluation_json";
    private static final String ADD_DETAIL_STATUS_SQL =
            "ALTER TABLE interview_attempts ADD COLUMN detail_evaluation_status VARCHAR(32) "
                    + "NOT NULL DEFAULT 'pending' COMMENT '单题详细评估状态：pending/generating/ready/failed'";
    private static final String ADD_DETAIL_JSON_SQL =
            "ALTER TABLE interview_attempts ADD COLUMN detail_evaluation_json JSON NULL "
                    + "COMMENT '单题详细评估结构化结果'";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists(TABLE_NAME)) {
            log.warn("数据库表不存在，跳过补列, table={}", TABLE_NAME);
            return;
        }

        ensureColumn(DETAIL_STATUS_COLUMN, ADD_DETAIL_STATUS_SQL);
        ensureColumn(DETAIL_JSON_COLUMN, ADD_DETAIL_JSON_SQL);
    }

    private void ensureColumn(String columnName, String alterSql) {
        if (columnExists(TABLE_NAME, columnName)) {
            return;
        }
        jdbcTemplate.execute(alterSql);
        log.info("数据库补列完成, table={}, column={}", TABLE_NAME, columnName);
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
}
