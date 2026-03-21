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
 * 启动时自动补齐单题重答表，避免联调环境漏跑 SQL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRedoAttemptSchemaAutoPatchRunner implements ApplicationRunner {

    private static final String TABLE_NAME = "question_redo_attempts";
    private static final String CREATE_TABLE_SQL = """
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单题重答记录表'
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (tableExists(TABLE_NAME)) {
            return;
        }
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        log.info("数据库建表完成, table={}", TABLE_NAME);
    }

    private boolean tableExists(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SHOW TABLES LIKE ?",
                tableName
        );
        return rows != null && !rows.isEmpty();
    }
}
