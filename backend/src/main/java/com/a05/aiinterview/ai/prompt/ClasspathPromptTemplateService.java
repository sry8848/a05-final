package com.a05.aiinterview.ai.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClasspathPromptTemplateService implements PromptTemplateService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private final ObjectMapper objectMapper;

    private final ConcurrentMap<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    @Override
    public RenderedPrompt render(String promptCode, String promptVersion, Map<String, Object> variables) {
        if (promptCode == null || promptCode.isBlank()) {
            throw new IllegalArgumentException("promptCode 不能为空");
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalArgumentException("promptVersion 不能为空");
        }

        PromptTemplate template = loadTemplate(promptCode);
        if (!promptVersion.equals(template.promptVersion())) {
            throw new IllegalArgumentException("Prompt 模板版本不匹配: promptCode=" + promptCode
                    + ", requested=" + promptVersion + ", actual=" + template.promptVersion());
        }

        Map<String, Object> safeVariables = variables != null ? variables : Map.of();

        RenderResult systemRendered = renderText(template.systemPrompt(), safeVariables);
        RenderResult userRendered = renderText(template.userPrompt(), safeVariables);

        Set<String> usedVariables = new LinkedHashSet<>(systemRendered.usedVariables());
        usedVariables.addAll(userRendered.usedVariables());

        warnUnusedVariables(promptCode, safeVariables.keySet(), usedVariables);

        return new RenderedPrompt(
                template.promptCode(),
                template.promptVersion(),
                systemRendered.text(),
                userRendered.text(),
                Collections.unmodifiableSet(usedVariables)
        );
    }

    @Override
    public PromptTemplateMetadata loadMetadata(String promptCode) {
        PromptTemplate template = loadTemplate(promptCode);
        return new PromptTemplateMetadata(template.promptCode(), template.promptVersion(), template.sourcePath());
    }

    private PromptTemplate loadTemplate(String promptCode) {
        return cache.computeIfAbsent(promptCode, this::readTemplateFromClasspath);
    }

    private PromptTemplate readTemplateFromClasspath(String promptCode) {
        List<String> fileCandidates = new ArrayList<>();
        fileCandidates.add(promptCode + ".md");
        String hyphenName = promptCode.replace('_', '-');
        if (!hyphenName.equals(promptCode)) {
            fileCandidates.add(hyphenName + ".md");
        }

        for (String filename : fileCandidates) {
            ClassPathResource resource = new ClassPathResource("prompts/" + filename);
            if (!resource.exists()) {
                continue;
            }
            try {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                return parseTemplate(content, resource.getPath());
            } catch (IOException e) {
                throw new PromptTemplateParseException("读取 Prompt 模板失败: " + filename, e);
            }
        }

        throw new IllegalArgumentException("未找到 Prompt 模板文件, promptCode=" + promptCode);
    }

    private PromptTemplate parseTemplate(String content, String sourcePath) {
        String promptCode = extractMeta(content, "promptCode");
        String promptVersion = extractMeta(content, "promptVersion");

        if (promptCode == null || promptCode.isBlank()) {
            throw new PromptTemplateParseException("模板缺少 promptCode: " + sourcePath);
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            throw new PromptTemplateParseException("模板缺少 promptVersion: " + sourcePath);
        }

        List<String> lines = Arrays.asList(content.split("\\R", -1));
        int systemHeading = findHeading(lines, "## System Prompt");
        int userHeading = findUserHeading(lines);

        if (systemHeading < 0 || userHeading < 0 || userHeading <= systemHeading) {
            throw new PromptTemplateParseException("模板缺少有效的 System/User 段落: " + sourcePath);
        }

        String systemPrompt = String.join("\n", lines.subList(systemHeading + 1, userHeading)).trim();
        String userPrompt = String.join("\n", lines.subList(userHeading + 1, lines.size())).trim();

        if (systemPrompt.isBlank()) {
            throw new PromptTemplateParseException("systemPrompt 为空: " + sourcePath);
        }
        if (userPrompt.isBlank()) {
            throw new PromptTemplateParseException("userPrompt 为空: " + sourcePath);
        }

        return new PromptTemplate(promptCode.trim(), promptVersion.trim(), sourcePath, systemPrompt, userPrompt);
    }

    private int findHeading(List<String> lines, String heading) {
        for (int i = 0; i < lines.size(); i++) {
            if (heading.equalsIgnoreCase(lines.get(i).trim())) {
                return i;
            }
        }
        return -1;
    }

    private int findUserHeading(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("## User Prompt")) {
                return i;
            }
        }
        return -1;
    }

    private String extractMeta(String content, String key) {
        Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:\\s*(.+?)\\s*$");
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private RenderResult renderText(String templateText, Map<String, Object> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(templateText);
        StringBuffer sb = new StringBuffer();
        Set<String> usedVariables = new LinkedHashSet<>();
        Set<String> missingVariables = new LinkedHashSet<>();

        while (matcher.find()) {
            String name = matcher.group(1);
            usedVariables.add(name);

            Object value = variables.get(name);
            if (!variables.containsKey(name) || value == null) {
                missingVariables.add(name);
            }

            String replacement = value == null ? "" : stringify(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        if (!missingVariables.isEmpty()) {
            throw new IllegalArgumentException("Prompt 渲染失败，缺少变量: " + String.join(", ", missingVariables));
        }

        return new RenderResult(sb.toString(), usedVariables);
    }

    private String stringify(Object value) {
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private void warnUnusedVariables(String promptCode, Set<String> providedVariables, Set<String> usedVariables) {
        Set<String> unused = new LinkedHashSet<>(providedVariables);
        unused.removeAll(usedVariables);
        if (!unused.isEmpty()) {
            log.warn("Prompt 渲染存在未使用变量, promptCode={}, unused={}", promptCode, unused);
        }
    }

    private record PromptTemplate(
            String promptCode,
            String promptVersion,
            String sourcePath,
            String systemPrompt,
            String userPrompt
    ) {
    }

    private record RenderResult(String text, Set<String> usedVariables) {
    }
}
