package com.a05.aiinterview.interview.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateLedgerPatchServiceDebugLogTest {

    @Test
    void applyReduction_shouldEmitStructuredDebugLogWhenEnabled() throws Exception {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        SessionSkillStateMapper skillStateMapper = mock(SessionSkillStateMapper.class);
        StateLedgerReducer reducer = mock(StateLedgerReducer.class);
        StateLedgerDiffService diffService = mock(StateLedgerDiffService.class);
        StateLedgerPatchService service = new StateLedgerPatchService(
                sessionMapper,
                skillStateMapper,
                reducer,
                diffService
        );
        setField(service, "interviewDebugEnabled", true);
        setField(service, "interviewDebugMaxTextChars", 2000);

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setStateLedgerJson(Map.of(
                "active_project_id", "p_order",
                "current_focus", "订单超时关闭",
                "covered_domains", List.of("mysql"),
                "covered_points", List.of("mysql:transaction"),
                "weak_signals", List.of("事务边界不清晰"),
                "recent_question_families", List.of("mysql.transaction.boundary"),
                "remaining_turn_budget", 6,
                "domain_states", List.of()
        ));
        when(sessionMapper.selectForUpdate(1L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(11L);
        question.setQuestionType("INTRO");
        question.setCreatedAt(LocalDateTime.now());

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .decision("probe")
                .answerVerdict("PARTIAL")
                .domainOutcome("continue")
                .focusPoint("项目经验")
                .build();

        Map<String, Object> newLedger = Map.of(
                "active_project_id", "p_order",
                "current_focus", "项目经验",
                "covered_domains", List.of("mysql"),
                "covered_points", List.of("mysql:transaction"),
                "weak_signals", List.of("事务边界不清晰"),
                "recent_question_families", List.of("mysql.transaction.boundary"),
                "remaining_turn_budget", 5,
                "domain_states", List.of()
        );
        when(reducer.reduce(any(), any(), any(), any())).thenReturn(newLedger);
        when(diffService.diff(any(), any())).thenReturn(Map.of("current_focus", Map.of("after", "项目经验")));

        ListAppender<ILoggingEvent> appender = startLogCapture();
        service.applyReduction(1L, output, question, "attempt-1", 99L, "回答内容");

        List<String> logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(logs).anyMatch(msg -> msg.contains("[INTERVIEW-DEBUG][ledger.patch]")
                && msg.contains("attempt-1")
                && msg.contains("项目经验"));
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = StateLedgerPatchService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ListAppender<ILoggingEvent> startLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(StateLedgerPatchService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
