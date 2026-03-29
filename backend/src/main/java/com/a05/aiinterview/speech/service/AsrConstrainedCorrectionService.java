package com.a05.aiinterview.speech.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AsrConstrainedCorrectionService {

    private static final Pattern QUOTED_TERM_PATTERN = Pattern.compile("[“\"'「『](.+?)[”\"'」』]");
    private static final Pattern LATIN_TERM_PATTERN = Pattern.compile("\\b[A-Za-z][A-Za-z0-9+.#\\- ]{1,30}\\b");
    private static final Pattern CHINESE_TERM_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,12}");

    private static final Map<String, List<ReplacementRule>> ROLE_RULES = Map.of(
            "backend", backendRules(),
            "java_backend", backendRules(),
            "frontend", frontendRules(),
            "fullstack", fullstackRules()
    );

    public CorrectionResult correct(String rawText, CorrectionContext context) {
        String source = rawText == null ? "" : rawText.trim();
        if (source.isEmpty()) {
            return new CorrectionResult(source, source, List.of(), 0, false);
        }

        List<ReplacementRule> rules = buildRules(context);
        String corrected = source;
        List<CorrectionChange> changes = new ArrayList<>();

        for (ReplacementRule rule : rules) {
            String next = corrected.replace(rule.from(), rule.to());
            if (!Objects.equals(next, corrected)) {
                changes.add(new CorrectionChange(rule.from(), rule.to(), rule.reason()));
                corrected = next;
            }
        }

        if (!isCorrectionSafe(source, corrected, changes)) {
            return new CorrectionResult(source, source, List.of(), 0, false);
        }

        return new CorrectionResult(
                corrected,
                source,
                List.copyOf(changes),
                changes.size(),
                !changes.isEmpty()
        );
    }

    private List<ReplacementRule> buildRules(CorrectionContext context) {
        List<ReplacementRule> rules = new ArrayList<>();
        rules.addAll(commonRules());

        String roleKey = normalizeRole(context.roleHint());
        if (ROLE_RULES.containsKey(roleKey)) {
            rules.addAll(ROLE_RULES.get(roleKey));
        }

        for (String term : extractDynamicTerms(context)) {
            String normalized = term.trim();
            if (normalized.length() < 2) {
                continue;
            }
            rules.add(new ReplacementRule(normalized.toLowerCase(Locale.ROOT), normalized, "dynamic_term"));
        }

        return rules.stream()
                .sorted(Comparator.comparingInt((ReplacementRule rule) -> rule.from().length()).reversed())
                .toList();
    }

    private static List<ReplacementRule> commonRules() {
        List<ReplacementRule> rules = new ArrayList<>();
        rules.add(new ReplacementRule("ja法", "Java", "technical_term"));
        rules.add(new ReplacementRule("java后端", "Java后端", "technical_term"));
        rules.add(new ReplacementRule("ja va", "Java", "technical_term"));
        rules.add(new ReplacementRule("ruedis", "Redis", "technical_term"));
        rules.add(new ReplacementRule("redis缓存", "Redis缓存", "technical_term"));
        rules.add(new ReplacementRule("vew三", "Vue 3", "technical_term"));
        rules.add(new ReplacementRule("vue三", "Vue 3", "technical_term"));
        rules.add(new ReplacementRule("后端结果", "后端接口", "technical_term"));
        rules.add(new ReplacementRule("街口", "接口", "technical_term"));
        rules.add(new ReplacementRule("首屏假宰", "首屏加载", "technical_term"));
        rules.add(new ReplacementRule("pin ya", "Pinia", "technical_term"));
        rules.add(new ReplacementRule("reactjs", "React", "technical_term"));
        rules.add(new ReplacementRule("typescript", "TypeScript", "technical_term"));
        rules.add(new ReplacementRule("javascript", "JavaScript", "technical_term"));
        return rules;
    }

    private static List<ReplacementRule> backendRules() {
        return List.of(
                new ReplacementRule("springboot", "Spring Boot", "technical_term"),
                new ReplacementRule("springcloud", "Spring Cloud", "technical_term"),
                new ReplacementRule("rabbitmq", "RabbitMQ", "technical_term"),
                new ReplacementRule("mybatis", "MyBatis", "technical_term"),
                new ReplacementRule("mysql", "MySQL", "technical_term"),
                new ReplacementRule("seata", "Seata", "technical_term"),
                new ReplacementRule("nginx", "Nginx", "technical_term"),
                new ReplacementRule("docker", "Docker", "technical_term"),
                new ReplacementRule("lua脚本", "Lua脚本", "technical_term"),
                new ReplacementRule("消息对列", "消息队列", "technical_term"),
                new ReplacementRule("分布式所", "分布式锁", "technical_term")
        );
    }

    private static List<ReplacementRule> frontendRules() {
        return List.of(
                new ReplacementRule("typescript", "TypeScript", "technical_term"),
                new ReplacementRule("javascript", "JavaScript", "technical_term"),
                new ReplacementRule("vue 3", "Vue 3", "technical_term"),
                new ReplacementRule("vue3", "Vue 3", "technical_term"),
                new ReplacementRule("react", "React", "technical_term"),
                new ReplacementRule("vite", "Vite", "technical_term"),
                new ReplacementRule("webpack", "Webpack", "technical_term"),
                new ReplacementRule("pinia", "Pinia", "technical_term"),
                new ReplacementRule("redux", "Redux", "technical_term"),
                new ReplacementRule("nodejs", "Node.js", "technical_term"),
                new ReplacementRule("tailwindcss", "Tailwind CSS", "technical_term"),
                new ReplacementRule("sass", "Sass", "technical_term"),
                new ReplacementRule("跨玉", "跨域", "technical_term"),
                new ReplacementRule("懒家在", "懒加载", "technical_term")
        );
    }

    private static List<ReplacementRule> fullstackRules() {
        List<ReplacementRule> rules = new ArrayList<>(backendRules());
        rules.addAll(frontendRules());
        return List.copyOf(rules);
    }

    private boolean isCorrectionSafe(String rawText, String correctedText, List<CorrectionChange> changes) {
        if (changes.isEmpty()) {
            return true;
        }
        if (changes.size() > 8) {
            return false;
        }
        int rawLength = rawText.length();
        int correctedLength = correctedText.length();
        return correctedLength >= rawLength / 2 && correctedLength <= Math.max(rawLength * 2, rawLength + 20);
    }

    private Set<String> extractDynamicTerms(CorrectionContext context) {
        Set<String> terms = new LinkedHashSet<>();
        collectTerms(context.questionText(), terms);
        collectTerms(context.jobDescription(), terms);
        return terms;
    }

    private void collectTerms(String text, Set<String> terms) {
        if (text == null || text.isBlank()) {
            return;
        }

        Matcher quotedMatcher = QUOTED_TERM_PATTERN.matcher(text);
        while (quotedMatcher.find()) {
            terms.add(quotedMatcher.group(1).trim());
        }

        Matcher latinMatcher = LATIN_TERM_PATTERN.matcher(text);
        while (latinMatcher.find()) {
            terms.add(latinMatcher.group().trim());
        }

        Matcher chineseMatcher = CHINESE_TERM_PATTERN.matcher(text);
        while (chineseMatcher.find()) {
            String term = chineseMatcher.group().trim();
            if (term.length() >= 4) {
                terms.add(term);
            }
        }
    }

    private String normalizeRole(String roleHint) {
        if (roleHint == null) {
            return "";
        }
        return roleHint.trim().toLowerCase(Locale.ROOT);
    }

    public record CorrectionContext(String roleHint, String questionText, String jobDescription) {
    }

    public record CorrectionChange(String from, String to, String reason) {
    }

    public record CorrectionResult(
            String correctedText,
            String rawText,
            List<CorrectionChange> changeList,
            int appliedRuleCount,
            boolean correctionApplied
    ) {
    }

    private record ReplacementRule(String from, String to, String reason) {
    }
}
