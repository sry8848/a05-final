package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.CreateInterviewRequest;
import com.a05.aiinterview.interview.entity.InterviewPreference;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("InterviewService experience-level compatibility tests")
class InterviewServiceExperienceLevelCompatibilityTest {

    @Test
    @DisplayName("legacy STAFF values should normalize to SENIOR on persisted entities")
    void legacyStaffValuesShouldNormalizeToSeniorOnPersistedEntities() {
        InterviewSession session = new InterviewSession();
        session.setExperienceLevel("STAFF");
        InterviewPreference preference = new InterviewPreference();
        preference.setExperienceLevel("STAFF");

        assertThat(session.getExperienceLevel()).isEqualTo("SENIOR");
        assertThat(preference.getExperienceLevel()).isEqualTo("SENIOR");
    }

    @Test
    @DisplayName("validateEnums should reject legacy STAFF for new requests")
    void validateEnumsShouldRejectLegacyStaffForNewRequests() {
        InterviewService service = new InterviewService(
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(ResumeMapper.class),
                mock(InterviewPreferenceService.class),
                mock(com.a05.aiinterview.interview.engine.PlannerOrchestrationService.class)
        );
        CreateInterviewRequest request = new CreateInterviewRequest();
        request.setTargetRole("JAVA_BACKEND");
        request.setExperienceLevel("STAFF");
        request.setMode("practice");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateEnums", request))
                .hasMessageContaining("experienceLevel 不合法：STAFF");
    }
}
