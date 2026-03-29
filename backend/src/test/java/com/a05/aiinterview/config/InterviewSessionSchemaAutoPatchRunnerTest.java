package com.a05.aiinterview.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewSessionSchemaAutoPatchRunnerTest {

    @Test
    void run_shouldUpgradeLegacyInterviewSessionsTable_whenOldSchemaDetected() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockMetadata(jdbcTemplate, true, Set.of(
                "id",
                "user_id",
                "target_position",
                "experience_level",
                "mode",
                "job_description",
                "resume_id",
                "resume_text_snapshot",
                "focus_topics_json",
                "think_time_limit_seconds",
                "answer_time_limit_seconds",
                "status",
                "created_at",
                "updated_at"
        ));

        ApplicationRunner runner = newRunner(jdbcTemplate);

        runner.run(new DefaultApplicationArguments(new String[0]));

        List<String> executedSqls = org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> "execute".equals(invocation.getMethod().getName()))
                .map(invocation -> (String) invocation.getArgument(0))
                .toList();

        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN position_code"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("SET position_code = CASE"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("MODIFY COLUMN position_code VARCHAR(64) NOT NULL"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN title"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("SET title = CASE"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("SET status = 'aborted' WHERE status = 'cancelled'"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN focus_topics"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("SET focus_topics = COALESCE"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN current_question_no"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN context_window_size"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN first_question_json"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN syllabus_json"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN state_ledger_json"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN model_provider"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN model_name"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN started_at"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN finished_at"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("SET started_at = COALESCE(started_at, created_at)"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("SET finished_at = COALESCE(finished_at, updated_at)"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("DROP COLUMN target_position"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("DROP COLUMN focus_topics_json"));
        assertThat(executedSqls).anyMatch(sql -> sql.contains("DROP COLUMN resume_text_snapshot"));
    }

    @Test
    void run_shouldSkip_whenInterviewSessionsTableMissing() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockMetadata(jdbcTemplate, false, Set.of());

        ApplicationRunner runner = newRunner(jdbcTemplate);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void run_shouldAddOnlyMissingCurrentColumns_whenLegacyColumnsAbsent() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockMetadata(jdbcTemplate, true, Set.of(
                "id",
                "user_id",
                "resume_id",
                "title",
                "position_code",
                "experience_level",
                "mode",
                "job_description",
                "focus_topics",
                "current_question_no",
                "context_window_size",
                "think_time_limit_seconds",
                "answer_time_limit_seconds",
                "first_question_json",
                "syllabus_json",
                "state_ledger_json",
                "status",
                "model_provider",
                "started_at",
                "finished_at",
                "created_at",
                "updated_at"
        ));

        ApplicationRunner runner = newRunner(jdbcTemplate);

        runner.run(new DefaultApplicationArguments(new String[0]));

        List<String> executedSqls = org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> "execute".equals(invocation.getMethod().getName()))
                .map(invocation -> (String) invocation.getArgument(0))
                .toList();

        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN model_name"));
        assertThat(executedSqls).noneMatch(sql -> sql.contains("ADD COLUMN position_code"));
        assertThat(executedSqls).noneMatch(sql -> sql.contains("DROP COLUMN target_position"));
    }

    @Test
    void run_shouldNotForcePositionCodeNotNull_whenNoLegacySourceExists() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockMetadata(jdbcTemplate, true, Set.of(
                "id",
                "user_id",
                "experience_level",
                "mode",
                "job_description",
                "resume_id",
                "status",
                "created_at",
                "updated_at"
        ));
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM interview_sessions WHERE position_code IS NULL OR TRIM(position_code) = ''",
                Long.class
        )).thenReturn(3L);

        ApplicationRunner runner = newRunner(jdbcTemplate);

        runner.run(new DefaultApplicationArguments(new String[0]));

        List<String> executedSqls = org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> "execute".equals(invocation.getMethod().getName()))
                .map(invocation -> (String) invocation.getArgument(0))
                .toList();

        assertThat(executedSqls).anyMatch(sql -> sql.contains("ADD COLUMN position_code"));
        assertThat(executedSqls).noneMatch(sql -> sql.contains("MODIFY COLUMN position_code VARCHAR(64) NOT NULL"));
    }

    private static ApplicationRunner newRunner(JdbcTemplate jdbcTemplate) throws Exception {
        Class<?> type = Class.forName("com.a05.aiinterview.config.InterviewSessionSchemaAutoPatchRunner");
        return (ApplicationRunner) type.getConstructor(JdbcTemplate.class).newInstance(jdbcTemplate);
    }

    private static void mockMetadata(JdbcTemplate jdbcTemplate, boolean tableExists, Set<String> existingColumns) {
        when(jdbcTemplate.queryForList("SHOW TABLES LIKE ?", "interview_sessions"))
                .thenReturn(tableExists ? List.of(java.util.Collections.singletonMap("table", "interview_sessions")) : List.of());

        Set<String> columnsToCheck = Set.of(
                "target_position",
                "position_code",
                "title",
                "focus_topics_json",
                "focus_topics",
                "resume_text_snapshot",
                "current_question_no",
                "context_window_size",
                "first_question_json",
                "syllabus_json",
                "state_ledger_json",
                "model_provider",
                "model_name",
                "started_at",
                "finished_at"
        );

        for (String column : columnsToCheck) {
            when(jdbcTemplate.queryForList("SHOW COLUMNS FROM interview_sessions LIKE ?", column))
                    .thenReturn(existingColumns.contains(column)
                            ? List.of(java.util.Collections.singletonMap("Field", column))
                            : List.of());
        }
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM interview_sessions WHERE position_code IS NULL OR TRIM(position_code) = ''",
                Long.class
        )).thenReturn(0L);
    }
}
