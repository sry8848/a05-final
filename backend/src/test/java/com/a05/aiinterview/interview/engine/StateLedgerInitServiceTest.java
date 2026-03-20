package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("StateLedgerInitService idempotent tests")
class StateLedgerInitServiceTest {

    @Test
    @DisplayName("initLedger should ignore duplicate skill-state inserts and still return ledger")
    void initLedger_shouldIgnoreDuplicateInsert() {
        SessionSkillStateMapper mapper = mock(SessionSkillStateMapper.class);
        doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any());
        StateLedgerInitService service = new StateLedgerInitService(mapper);

        PositionSkillDomain domain = new PositionSkillDomain();
        domain.setId(1L);
        domain.setDomainCode("java_core");
        domain.setDomainName("Java Core");

        PlannerOutput.DomainPlan plan = new PlannerOutput.DomainPlan();
        plan.setDomainId(1L);
        plan.setDomainCode("java_core");
        plan.setDomainName("Java Core");
        plan.setTargetDepth("L4");

        PlannerOutput output = new PlannerOutput();
        output.setDomains(List.of(plan));
        output.setQuestionMixPlan(Map.of("INTRO", 1, "PRINCIPLE", 2));

        AtomicReference<Map<String, Object>> ledgerRef = new AtomicReference<>();
        assertThatCode(() -> ledgerRef.set(service.initLedger(6L, output, List.of(domain))))
                .doesNotThrowAnyException();

        Map<String, Object> ledger = ledgerRef.get();
        assertThat(ledger).isNotNull();
        assertThat(ledger.get("asked_total")).isEqualTo(0);
        assertThat(ledger.get("active_project_id")).isNull();
        assertThat(ledger.get("current_focus")).isNull();
        assertThat(ledger.get("remaining_turn_budget")).isEqualTo(3);
        assertThat(ledger.get("covered_domains")).isEqualTo(List.of());
        assertThat(ledger.get("covered_points")).isEqualTo(List.of());
        assertThat(ledger.get("weak_signals")).isEqualTo(List.of());
        assertThat(ledger.get("recent_question_families")).isEqualTo(List.of());
        assertThat(ledger.get("rescue_total")).isEqualTo(0);
        assertThat(ledger.get("rescue_counts_by_domain")).isEqualTo(Map.of());
        assertThat(ledger.get("last_focus_point")).isNull();
        assertThat(ledger.get("current_focus_streak")).isEqualTo(0);
        assertThat(ledger.get("question_mix_progress")).isEqualTo(Map.of("INTRO", 0, "PRINCIPLE", 0));
        verify(mapper, times(1)).insert(any());
    }
}
