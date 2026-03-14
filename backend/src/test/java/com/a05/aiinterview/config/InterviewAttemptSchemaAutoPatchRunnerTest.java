package com.a05.aiinterview.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewAttemptSchemaAutoPatchRunnerTest {

    @Test
    void run_shouldAddBothColumns_whenTableExistsAndColumnsMissing() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockMetadata(jdbcTemplate, true, false, false);

        InterviewAttemptSchemaAutoPatchRunner runner = new InterviewAttemptSchemaAutoPatchRunner(jdbcTemplate);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute("ALTER TABLE interview_attempts ADD COLUMN detail_evaluation_status VARCHAR(32) "
                + "NOT NULL DEFAULT 'pending' COMMENT '单题详细评估状态：pending/generating/ready/failed'");
        verify(jdbcTemplate).execute("ALTER TABLE interview_attempts ADD COLUMN detail_evaluation_json JSON NULL "
                + "COMMENT '单题详细评估结构化结果'");
    }

    @Test
    void run_shouldSkip_whenTableMissing() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockMetadata(jdbcTemplate, false, false, false);

        InterviewAttemptSchemaAutoPatchRunner runner = new InterviewAttemptSchemaAutoPatchRunner(jdbcTemplate);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void run_shouldAddOnlyMissingColumn_whenStatusExistsAndJsonMissing() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockMetadata(jdbcTemplate, true, true, false);

        InterviewAttemptSchemaAutoPatchRunner runner = new InterviewAttemptSchemaAutoPatchRunner(jdbcTemplate);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, never()).execute("ALTER TABLE interview_attempts ADD COLUMN detail_evaluation_status VARCHAR(32) "
                + "NOT NULL DEFAULT 'pending' COMMENT '单题详细评估状态：pending/generating/ready/failed'");
        verify(jdbcTemplate).execute("ALTER TABLE interview_attempts ADD COLUMN detail_evaluation_json JSON NULL "
                + "COMMENT '单题详细评估结构化结果'");
    }

    private static void mockMetadata(JdbcTemplate jdbcTemplate, boolean tableExists, boolean statusColumnExists,
                                     boolean jsonColumnExists) {
        when(jdbcTemplate.queryForList("SHOW TABLES LIKE ?", "interview_attempts"))
                .thenReturn(tableExists ? List.of(java.util.Collections.singletonMap("table", "interview_attempts")) : List.of());
        when(jdbcTemplate.queryForList("SHOW COLUMNS FROM interview_attempts LIKE ?", "detail_evaluation_status"))
                .thenReturn(statusColumnExists
                        ? List.of(java.util.Collections.singletonMap("Field", "detail_evaluation_status"))
                        : List.of());
        when(jdbcTemplate.queryForList("SHOW COLUMNS FROM interview_attempts LIKE ?", "detail_evaluation_json"))
                .thenReturn(jsonColumnExists
                        ? List.of(java.util.Collections.singletonMap("Field", "detail_evaluation_json"))
                        : List.of());
    }
}
