package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.a05.aiinterview.position.mapper.PositionSkillDomainMapper;
import com.a05.aiinterview.rag.dto.KnowledgeJsonlImportResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeJsonlImportService tests")
class KnowledgeJsonlImportServiceTest {

    @Test
    @DisplayName("importJsonl should reject non jsonl extension before parsing")
    void importJsonl_shouldRejectNonJsonlExtensionBeforeParsing() {
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        PositionSkillDomainMapper domainMapper = mock(PositionSkillDomainMapper.class);
        KnowledgeJsonlImportService service = new KnowledgeJsonlImportService(
                new ObjectMapper(),
                ingestionService,
                domainMapper
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cards.json",
                "application/json",
                "{\"id\":\"q1\"}".getBytes(StandardCharsets.UTF_8)
        );

        KnowledgeJsonlImportResult result = service.importJsonl(file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.getScope()).isEqualTo("file");
                    assertThat(error.getReason()).isEqualTo("文件类型不支持");
                    assertThat(error.getExpected()).isEqualTo(".jsonl");
                });
        verify(ingestionService, never()).ingest(any());
        verify(domainMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("importJsonl should reject invalid json and stop batch ingestion")
    void importJsonl_shouldRejectInvalidJsonAndStopBatchIngestion() {
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        PositionSkillDomainMapper domainMapper = mock(PositionSkillDomainMapper.class);
        when(domainMapper.selectList(any())).thenReturn(List.of(domain("redis")));
        KnowledgeJsonlImportService service = new KnowledgeJsonlImportService(
                new ObjectMapper(),
                ingestionService,
                domainMapper
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cards.jsonl",
                "application/json",
                """
                {"id":"redis-001","questionText":"讲一下 Redis 缓存穿透","intentConcept":"考察空对象缓存和布隆过滤器","referenceContext":"高并发查询不存在数据时需要兜底。","questionType":"PRINCIPLE","difficulty":"L2","domainCode":"redis","source":"manual","version":"v1"}
                not-json
                """.getBytes(StandardCharsets.UTF_8)
        );

        KnowledgeJsonlImportResult result = service.importJsonl(file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getTotalLines()).isEqualTo(2);
        assertThat(result.getValidLines()).isEqualTo(1);
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.getScope()).isEqualTo("line");
                    assertThat(error.getLineNo()).isEqualTo(2);
                    assertThat(error.getReason()).isEqualTo("不是合法 JSON");
                    assertThat(error.getExpected()).isEqualTo("单行完整 JSON 对象");
                });
        verify(ingestionService, never()).ingest(any());
    }

    @Test
    @DisplayName("importJsonl should reject behavioral cards that bind technical domain")
    void importJsonl_shouldRejectBehavioralCardsThatBindTechnicalDomain() {
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        PositionSkillDomainMapper domainMapper = mock(PositionSkillDomainMapper.class);
        when(domainMapper.selectList(any())).thenReturn(List.of(domain("redis")));
        KnowledgeJsonlImportService service = new KnowledgeJsonlImportService(
                new ObjectMapper(),
                ingestionService,
                domainMapper
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cards.jsonl",
                "application/json",
                """
                {"id":"behavior-001","questionText":"讲一次你和产品意见不一致的经历","intentConcept":"考察沟通推进","referenceContext":"关注冲突处理、推进和复盘。","questionType":"BEHAVIORAL","difficulty":"L2","domainCode":"redis","source":"manual","version":"v1"}
                """.getBytes(StandardCharsets.UTF_8)
        );

        KnowledgeJsonlImportResult result = service.importJsonl(file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.getLineNo()).isEqualTo(1);
                    assertThat(error.getField()).isEqualTo("domainCode");
                    assertThat(error.getReason()).isEqualTo("行为题不允许绑定技术知识域");
                    assertThat(error.getExpected()).isEqualTo("domainCode 为空字符串或缺省");
                });
        verify(ingestionService, never()).ingest(any());
    }

    @Test
    @DisplayName("importJsonl should allow intro cards with intro domain and ingest after full validation")
    void importJsonl_shouldAllowIntroCardsWithIntroDomainAndIngestAfterFullValidation() {
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        PositionSkillDomainMapper domainMapper = mock(PositionSkillDomainMapper.class);
        when(domainMapper.selectList(any())).thenReturn(List.of(domain("redis"), domain("mysql")));
        when(ingestionService.ingest(any())).thenReturn(2);
        KnowledgeJsonlImportService service = new KnowledgeJsonlImportService(
                new ObjectMapper(),
                ingestionService,
                domainMapper
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cards.jsonl",
                "application/json",
                """
                {"id":"intro-001","questionText":"请先做自我介绍","intentConcept":"考察表达与项目概述","referenceContext":"首题固定为 INTRO，要求候选人介绍背景和项目。","questionType":"INTRO","difficulty":"L1","domainCode":"intro","source":"manual","version":"v1","active":true}
                {"id":"redis-001","questionText":"讲一下 Redis 缓存穿透","intentConcept":"考察空对象缓存和布隆过滤器","referenceContext":"高并发查询不存在数据时需要兜底。","questionType":"PRINCIPLE","difficulty":"L2","domainCode":"redis","scoringKeyPoints":["空对象缓存","布隆过滤器"],"scoringPitfalls":["混淆击穿"],"followUpIds":["redis-002"],"keywords":["Redis","缓存穿透"],"source":"manual","version":"v1"}
                """.getBytes(StandardCharsets.UTF_8)
        );

        KnowledgeJsonlImportResult result = service.importJsonl(file);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalLines()).isEqualTo(2);
        assertThat(result.getValidLines()).isEqualTo(2);
        assertThat(result.getIngestedCount()).isEqualTo(2);
        assertThat(result.getErrors()).isEmpty();
        verify(ingestionService).ingest(any());
    }

    @Test
    @DisplayName("importJsonl should return file scoped ingestion failure when storage write throws")
    void importJsonl_shouldReturnFileScopedIngestionFailureWhenStorageWriteThrows() {
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        PositionSkillDomainMapper domainMapper = mock(PositionSkillDomainMapper.class);
        when(domainMapper.selectList(any())).thenReturn(List.of(domain("redis")));
        when(ingestionService.ingest(any())).thenThrow(new IllegalStateException("qdrant down"));
        KnowledgeJsonlImportService service = new KnowledgeJsonlImportService(
                new ObjectMapper(),
                ingestionService,
                domainMapper
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cards.jsonl",
                "application/json",
                """
                {"id":"redis-001","questionText":"讲一下 Redis 缓存穿透","intentConcept":"考察空对象缓存和布隆过滤器","referenceContext":"高并发查询不存在数据时需要兜底。","questionType":"PRINCIPLE","difficulty":"L2","domainCode":"redis","source":"manual","version":"v1"}
                """.getBytes(StandardCharsets.UTF_8)
        );

        KnowledgeJsonlImportResult result = service.importJsonl(file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.getScope()).isEqualTo("file");
                    assertThat(error.getReason()).isEqualTo("知识入库失败");
                    assertThat(error.getField()).isNull();
                    assertThat(error.getLineNo()).isNull();
                });
        verify(ingestionService).ingest(any());
    }

    @Test
    @DisplayName("importJsonl should reject unreadable file")
    void importJsonl_shouldRejectUnreadableFile() {
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        PositionSkillDomainMapper domainMapper = mock(PositionSkillDomainMapper.class);
        KnowledgeJsonlImportService service = new KnowledgeJsonlImportService(
                new ObjectMapper(),
                ingestionService,
                domainMapper
        );

        MultipartFile file = new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return "cards.jsonl";
            }

            @Override
            public String getContentType() {
                return "application/json";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return 10;
            }

            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("broken");
            }

            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("broken");
            }

            @Override
            public void transferTo(java.io.File dest) {
                throw new UnsupportedOperationException();
            }
        };

        KnowledgeJsonlImportResult result = service.importJsonl(file);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.getScope()).isEqualTo("file");
                    assertThat(error.getReason()).isEqualTo("文件读取失败");
                    assertThat(error.getExpected()).isEqualTo("UTF-8 编码的文本文件");
                });
        verify(ingestionService, never()).ingest(any());
    }

    private PositionSkillDomain domain(String code) {
        PositionSkillDomain domain = new PositionSkillDomain();
        domain.setDomainCode(code);
        return domain;
    }
}
