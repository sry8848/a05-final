package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.common.enums.DepthLevel;
import com.a05.aiinterview.common.enums.QuestionType;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.a05.aiinterview.position.mapper.PositionSkillDomainMapper;
import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.dto.KnowledgeJsonlImportError;
import com.a05.aiinterview.rag.dto.KnowledgeJsonlImportResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 管理端 JSONL 题卡导入服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class KnowledgeJsonlImportService {

    private static final String INTRO_DOMAIN_CODE = "intro";
    private static final Set<String> FORBIDDEN_FAKE_DOMAIN_CODES = Set.of("behavior", "behavioral", "project");

    private final ObjectMapper objectMapper;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final PositionSkillDomainMapper positionSkillDomainMapper;

    public KnowledgeJsonlImportResult importJsonl(MultipartFile file) {
        if (file == null) {
            return failure("", 0, 0, KnowledgeJsonlImportError.file("缺少上传文件", "multipart/form-data 字段 file"));
        }

        String fileName = safeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jsonl")) {
            return failure(fileName, 0, 0, KnowledgeJsonlImportError.file("文件类型不支持", ".jsonl"));
        }
        if (file.isEmpty()) {
            return failure(fileName, 0, 0, KnowledgeJsonlImportError.file("文件为空", "至少包含一行 JSON 对象"));
        }

        List<String> lines;
        try {
            lines = readLines(file);
        } catch (IOException e) {
            log.warn("读取 JSONL 文件失败, fileName={}", fileName, e);
            return failure(fileName, 0, 0, KnowledgeJsonlImportError.file("文件读取失败", "UTF-8 编码的文本文件"));
        }

        int totalLines = lines.size();
        if (totalLines == 0) {
            return failure(fileName, 0, 0, KnowledgeJsonlImportError.file("未检测到有效 JSONL 内容", "每行一个 JSON 对象"));
        }

        Set<String> allowedDomainCodes = loadAllowedDomainCodes();
        List<KnowledgeJsonlImportError> errors = new ArrayList<>();
        List<KnowledgeDocument> documents = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {
            int lineNo = index + 1;
            String line = lines.get(index);
            JsonNode node;
            try {
                node = objectMapper.readTree(line);
            } catch (JsonProcessingException e) {
                errors.add(KnowledgeJsonlImportError.line(lineNo, null, "不是合法 JSON", "单行完整 JSON 对象"));
                continue;
            }

            if (node == null || !node.isObject()) {
                errors.add(KnowledgeJsonlImportError.line(lineNo, null, "JSONL 行必须是对象", "{...}"));
                continue;
            }

            List<KnowledgeJsonlImportError> lineErrors = new ArrayList<>();
            KnowledgeDocument document = parseDocument(node, lineNo, lineErrors, allowedDomainCodes);
            if (lineErrors.isEmpty()) {
                documents.add(document);
            } else {
                errors.addAll(lineErrors);
            }
        }

        if (!errors.isEmpty()) {
            return KnowledgeJsonlImportResult.failure(fileName, totalLines, documents.size(), errors);
        }

        try {
            int ingestedCount = knowledgeIngestionService.ingest(documents);
            return KnowledgeJsonlImportResult.success(fileName, totalLines, documents.size(), ingestedCount);
        } catch (Exception e) {
            log.error("JSONL 导入入库失败, fileName={}", fileName, e);
            return failure(fileName, totalLines, documents.size(), KnowledgeJsonlImportError.file("知识入库失败", null));
        }
    }

    private List<String> readLines(MultipartFile file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private KnowledgeDocument parseDocument(JsonNode node,
                                            int lineNo,
                                            List<KnowledgeJsonlImportError> lineErrors,
                                            Set<String> allowedDomainCodes) {
        String id = requiredText(node, "id", lineNo, lineErrors);
        String questionText = requiredText(node, "questionText", lineNo, lineErrors);
        String intentConcept = requiredText(node, "intentConcept", lineNo, lineErrors);
        String referenceContext = requiredText(node, "referenceContext", lineNo, lineErrors);
        String questionType = requiredEnum(node, "questionType", QuestionType.class, lineNo, lineErrors);
        String difficulty = requiredEnum(node, "difficulty", DepthLevel.class, lineNo, lineErrors);
        String source = requiredText(node, "source", lineNo, lineErrors);
        String version = requiredText(node, "version", lineNo, lineErrors);

        List<String> scoringKeyPoints = stringArray(node, "scoringKeyPoints", lineNo, lineErrors);
        List<String> scoringPitfalls = stringArray(node, "scoringPitfalls", lineNo, lineErrors);
        List<String> followUpIds = stringArray(node, "followUpIds", lineNo, lineErrors);
        List<String> keywords = stringArray(node, "keywords", lineNo, lineErrors);
        Boolean active = optionalBoolean(node, "active", lineNo, lineErrors);

        String domainCode = optionalTrimmedText(node, "domainCode");
        validateDomainCode(questionType, domainCode, lineNo, lineErrors, allowedDomainCodes);

        return KnowledgeDocument.builder()
                .id(id)
                .questionText(questionText)
                .intentConcept(intentConcept)
                .referenceContext(referenceContext)
                .scoringKeyPoints(scoringKeyPoints)
                .scoringPitfalls(scoringPitfalls)
                .followUpIds(followUpIds)
                .domainCode(domainCode)
                .questionType(questionType)
                .difficulty(difficulty)
                .keywords(keywords)
                .source(source)
                .active(active == null || active)
                .version(version)
                .build();
    }

    private void validateDomainCode(String questionType,
                                    String domainCode,
                                    int lineNo,
                                    List<KnowledgeJsonlImportError> lineErrors,
                                    Set<String> allowedDomainCodes) {
        String normalizedType = normalizeUpper(questionType);
        String normalizedDomainCode = normalize(domainCode);
        if (normalizedType.isBlank()) {
            return;
        }
        if (FORBIDDEN_FAKE_DOMAIN_CODES.contains(normalizedDomainCode)) {
            lineErrors.add(KnowledgeJsonlImportError.line(
                    lineNo,
                    "domainCode",
                    "知识域编码非法",
                    "使用系统真实 domainCode，不使用伪域"
            ));
            return;
        }

        if (QuestionType.BEHAVIORAL.name().equals(normalizedType)) {
            if (!normalizedDomainCode.isBlank()) {
                lineErrors.add(KnowledgeJsonlImportError.line(
                        lineNo,
                        "domainCode",
                        "行为题不允许绑定技术知识域",
                        "domainCode 为空字符串或缺省"
                ));
            }
            return;
        }

        if (QuestionType.INTRO.name().equals(normalizedType)) {
            if (!INTRO_DOMAIN_CODE.equals(normalizedDomainCode)) {
                lineErrors.add(KnowledgeJsonlImportError.line(
                        lineNo,
                        "domainCode",
                        "知识域编码非法",
                        "INTRO 题必须使用 intro"
                ));
            }
            return;
        }

        if (normalizedDomainCode.isBlank() || !allowedDomainCodes.contains(normalizedDomainCode)) {
            lineErrors.add(KnowledgeJsonlImportError.line(
                    lineNo,
                    "domainCode",
                    "知识域编码非法",
                    "使用系统允许的 domainCode"
            ));
        }
    }

    private String requiredText(JsonNode node,
                                String field,
                                int lineNo,
                                List<KnowledgeJsonlImportError> lineErrors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            lineErrors.add(KnowledgeJsonlImportError.line(lineNo, field, "缺少必填字段", "非空字符串"));
            return "";
        }
        if (!value.isTextual()) {
            lineErrors.add(KnowledgeJsonlImportError.line(lineNo, field, "字段类型非法", "非空字符串"));
            return "";
        }
        String text = value.asText().trim();
        if (text.isEmpty()) {
            lineErrors.add(KnowledgeJsonlImportError.line(lineNo, field, "字段不能为空", "非空字符串"));
        }
        return text;
    }

    private <E extends Enum<E>> String requiredEnum(JsonNode node,
                                                    String field,
                                                    Class<E> enumType,
                                                    int lineNo,
                                                    List<KnowledgeJsonlImportError> lineErrors) {
        String value = requiredText(node, field, lineNo, lineErrors);
        if (value.isBlank()) {
            return value;
        }
        try {
            Enum.valueOf(enumType, value);
            return value;
        } catch (IllegalArgumentException e) {
            lineErrors.add(KnowledgeJsonlImportError.line(
                    lineNo,
                    field,
                    "字段值非法",
                    enumExpected(enumType)
            ));
            return value;
        }
    }

    private List<String> stringArray(JsonNode node,
                                     String field,
                                     int lineNo,
                                     List<KnowledgeJsonlImportError> lineErrors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            lineErrors.add(KnowledgeJsonlImportError.line(lineNo, field, "字段类型非法", "字符串数组"));
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                lineErrors.add(KnowledgeJsonlImportError.line(lineNo, field, "字段类型非法", "字符串数组"));
                return List.of();
            }
            String text = item.asText().trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    private Boolean optionalBoolean(JsonNode node,
                                    String field,
                                    int lineNo,
                                    List<KnowledgeJsonlImportError> lineErrors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            lineErrors.add(KnowledgeJsonlImportError.line(lineNo, field, "字段类型非法", "true 或 false"));
            return null;
        }
        return value.asBoolean();
    }

    private String optionalTrimmedText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            return String.valueOf(value.asText()).trim();
        }
        return value.asText().trim();
    }

    private Set<String> loadAllowedDomainCodes() {
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        List<PositionSkillDomain> domains = positionSkillDomainMapper.selectList(null);
        if (domains != null) {
            for (PositionSkillDomain domain : domains) {
                if (domain != null && domain.getDomainCode() != null && !domain.getDomainCode().isBlank()) {
                    allowed.add(domain.getDomainCode().trim());
                }
            }
        }
        return allowed;
    }

    private String enumExpected(Class<? extends Enum<?>> enumType) {
        return String.join(" / ", java.util.Arrays.stream(enumType.getEnumConstants()).map(Enum::name).toList());
    }

    private String safeFileName(String originalFilename) {
        return originalFilename == null ? "" : originalFilename.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private KnowledgeJsonlImportResult failure(String fileName,
                                               int totalLines,
                                               int validLines,
                                               KnowledgeJsonlImportError error) {
        return KnowledgeJsonlImportResult.failure(fileName, totalLines, validLines, List.of(error));
    }
}
