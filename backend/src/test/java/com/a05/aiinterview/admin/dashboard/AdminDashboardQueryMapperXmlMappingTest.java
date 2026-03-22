package com.a05.aiinterview.admin.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Admin dashboard query mapper XML mapping tests")
class AdminDashboardQueryMapperXmlMappingTest {

    @Test
    @DisplayName("dashboard query XML should define active interview, token, model and prompt aggregations")
    void dashboardQueryXml_shouldContainExpectedAggregations() throws Exception {
        String resourcePath = "mapper/admin/AdminDashboardQueryMapper.xml";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        assertThat(in)
                .as("缺少 Mapper XML: %s", resourcePath)
                .isNotNull();

        String xml;
        try (in) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml).contains("namespace=\"com.a05.aiinterview.admin.dashboard.query.AdminDashboardQueryMapper\"");
        assertThat(xml).contains("planning', 'in_progress', 'report_generating'");
        assertThat(xml).contains("COALESCE(request_tokens, 0) + COALESCE(response_tokens, 0)");
        assertThat(xml).contains("GROUP BY normalized_model_provider, normalized_model_name");
        assertThat(xml).contains("MAX(created_at) AS last_used_at");
        assertThat(xml).contains("calls_last_24_hours");
    }
}
