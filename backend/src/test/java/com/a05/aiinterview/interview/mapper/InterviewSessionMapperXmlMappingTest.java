package com.a05.aiinterview.interview.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewSessionMapper XML mapping tests")
class InterviewSessionMapperXmlMappingTest {

    @Test
    @DisplayName("selectForUpdate should be mapped by XML resultMap with JacksonTypeHandler")
    void selectForUpdate_shouldUseXmlResultMapWithJsonTypeHandler() throws Exception {
        String resourcePath = "mapper/interview/InterviewSessionMapper.xml";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        assertThat(in)
                .as("缺少 Mapper XML: %s", resourcePath)
                .isNotNull();

        String xml;
        try (in) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml).contains("resultMap id=\"InterviewSessionResultMap\"");
        assertThat(xml).contains("column=\"state_ledger_json\"");
        assertThat(xml).contains("property=\"stateLedgerJson\"");
        assertThat(xml).contains("typeHandler=\"com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler\"");
        assertThat(xml).contains("select id=\"selectForUpdate\"");
        assertThat(xml).contains("resultMap=\"InterviewSessionResultMap\"");
    }
}
