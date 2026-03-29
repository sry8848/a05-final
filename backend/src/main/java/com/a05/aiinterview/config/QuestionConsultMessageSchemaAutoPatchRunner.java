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
 * 启动时自动补齐单题追问消息表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionConsultMessageSchemaAutoPatchRunner implements ApplicationRunner {

    private static final String TABLE_NAME = "question_consult_messages";
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS question_consult_messages (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL COMMENT '用户 ID',
                session_id BIGINT NOT NULL COMMENT '会话 ID',
                question_id BIGINT NOT NULL COMMENT '题目 ID',
                role VARCHAR(16) NOT NULL COMMENT '角色：user/assistant',
                status VARCHAR(32) NOT NULL COMMENT '状态：ready/generating/failed/cancelled',
                content LONGTEXT NULL COMMENT '消息内容',
                reply_to_message_id BIGINT NULL COMMENT 'assistant 对应的 user 消息 ID',
                error_message VARCHAR(512) NULL COMMENT '失败或取消原因',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_qcm_user_question (user_id, session_id, question_id),
                INDEX idx_qcm_generating (user_id, session_id, question_id, role, status)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单题追问消息表'
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
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW TABLES LIKE ?", tableName);
        return rows != null && !rows.isEmpty();
    }
}
