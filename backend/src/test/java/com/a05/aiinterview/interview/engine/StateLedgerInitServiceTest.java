package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.interview.dto.InterviewSyllabus;
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

        InterviewSyllabus.SyllabusDomain plan = new InterviewSyllabus.SyllabusDomain();
        plan.setDomainCode("java_core");
        plan.setDomainName("Java Core");

        InterviewSyllabus syllabus = new InterviewSyllabus();
        syllabus.setDomains(List.of(plan));

        AtomicReference<Map<String, Object>> ledgerRef = new AtomicReference<>();
        assertThatCode(() -> ledgerRef.set(service.initLedger(6L, "MIDDLE", syllabus, List.of(domain))))
                .doesNotThrowAnyException();

        Map<String, Object> ledger = ledgerRef.get();
        assertThat(ledger).isNotNull();
        assertThat(ledger.get("asked_total")).isEqualTo(0);
        assertThat(ledger.get("single_question_mode")).isEqualTo(false);
        assertThat(ledger.get("active_item_key")).isNull();
        assertThat(ledger.get("current_focus")).isNull();
        assertThat(ledger.get("covered_domains")).isEqualTo(List.of());
        assertThat(ledger.get("covered_points")).isEqualTo(List.of());
        assertThat(ledger.get("recent_question_families")).isEqualTo(List.of());
        assertThat(ledger.get("interviewer_archetype")).isEqualTo(InterviewerArchetypeSupport.chooseForSession(6L));
        assertThat(ledger.get("max_questions")).isEqualTo(16);
        assertThat(ledger.get("quota_state")).isEqualTo(Map.of(
                "samePointContinue", 0,
                "sameDomainContinue", 0,
                "sameProjectPointContinue", 0,
                "sameProjectContinue", 0,
                "principleTotal", 0,
                "projectTotal", 0,
                "scenarioTotal", 0,
                "behavioralTotal", 0
        ));
        assertThat(ledger.get("domain_states")).isEqualTo(List.of(Map.of(
                "domainCode", "java_core",
                "domainName", "Java Core",
                "status", "UNASKED",
                "saturated", false,
                "evidenceRefs", List.of()
        )));
        verify(mapper, times(1)).insert(any());
    }
}
