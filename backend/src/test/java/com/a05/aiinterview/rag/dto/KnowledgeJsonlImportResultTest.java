package com.a05.aiinterview.rag.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KnowledgeJsonlImportResult tests")
class KnowledgeJsonlImportResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("success result should serialize stable summary fields")
    void successResult_shouldSerializeStableSummaryFields() throws Exception {
        KnowledgeJsonlImportResult result = KnowledgeJsonlImportResult.success(
                "rag-java-backend.jsonl",
                20,
                20,
                20
        );

        String json = objectMapper.writeValueAsString(result);

        assertThat(json)
                .contains("\"fileName\":\"rag-java-backend.jsonl\"")
                .contains("\"totalLines\":20")
                .contains("\"validLines\":20")
                .contains("\"ingestedCount\":20")
                .contains("\"errors\":[]");
    }

    @Test
    @DisplayName("failure result should preserve ordered file and line errors")
    void failureResult_shouldPreserveOrderedFileAndLineErrors() {
        KnowledgeJsonlImportResult result = KnowledgeJsonlImportResult.failure(
                "rag-java-backend.jsonl",
                20,
                17,
                java.util.List.of(
                        KnowledgeJsonlImportError.file("文件类型不支持", ".jsonl"),
                        KnowledgeJsonlImportError.line(18, "difficulty", "字段值非法", "L1-L5")
                )
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors())
                .extracting(KnowledgeJsonlImportError::getScope, KnowledgeJsonlImportError::getLineNo,
                        KnowledgeJsonlImportError::getField, KnowledgeJsonlImportError::getReason,
                        KnowledgeJsonlImportError::getExpected)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("file", null, null, "文件类型不支持", ".jsonl"),
                        org.assertj.core.groups.Tuple.tuple("line", 18, "difficulty", "字段值非法", "L1-L5")
                );
    }
}
