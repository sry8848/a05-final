package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.LearningRecommendationDto;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds lightweight learning recommendations from report data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningRecommendationService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewReportMapper interviewReportMapper;

    @Value("${learning.recommendation.enabled:true}")
    private boolean recommendationEnabled;

    @Value("${learning.recommendation.top-k:3}")
    private int topK;

    public LearningRecommendationDto getRecommendations(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Interview session not found, sessionId=" + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("No permission to access this session");
        }

        if (!recommendationEnabled) {
            return LearningRecommendationDto.disabled(sessionId);
        }

        InterviewReport report = interviewReportMapper.selectBySessionId(sessionId);
        if (report == null) {
            return LearningRecommendationDto.generating(sessionId);
        }

        List<LearningRecommendationDto.Section> sections = buildSections(report, session);
        return LearningRecommendationDto.ready(sessionId, sections);
    }

    private List<LearningRecommendationDto.Section> buildSections(InterviewReport report, InterviewSession session) {
        int limit = Math.max(topK, 1);
        List<DomainCandidate> weakestDomains = extractWeakestDomains(report, limit);

        List<LearningRecommendationDto.Section> sections = new ArrayList<>();

        LearningRecommendationDto.Section immediate = new LearningRecommendationDto.Section();
        immediate.setSectionKey("immediate");
        immediate.setSectionTitle("立即补强");
        immediate.setItems(buildImmediateItems(weakestDomains, report, Math.min(2, limit)));
        if (!immediate.getItems().isEmpty()) {
            sections.add(immediate);
        }

        LearningRecommendationDto.Section focused = new LearningRecommendationDto.Section();
        focused.setSectionKey("focused");
        focused.setSectionTitle("重点补齐");
        focused.setItems(buildFocusedItems(weakestDomains, report, limit));
        if (!focused.getItems().isEmpty()) {
            sections.add(focused);
        }

        LearningRecommendationDto.Section longTerm = new LearningRecommendationDto.Section();
        longTerm.setSectionKey("long_term");
        longTerm.setSectionTitle("长期进阶");
        longTerm.setItems(buildLongTermItems(session, report, limit));
        if (!longTerm.getItems().isEmpty()) {
            sections.add(longTerm);
        }

        if (sections.isEmpty()) {
            LearningRecommendationDto.Section fallback = new LearningRecommendationDto.Section();
            fallback.setSectionKey("immediate");
            fallback.setSectionTitle("立即补强");
            LearningRecommendationDto.Item item = new LearningRecommendationDto.Item();
            item.setItemId("fallback-1");
            item.setTitle("补齐本场面试薄弱点");
            item.setReason("建议先从本场回答不完整的题目入手，逐题复盘并补强核心概念。");
            item.setResourceType("practice");
            item.setEstimatedMinutes(30);
            item.setLink(buildSearchLink("技术面试 复盘 模板"));
            fallback.setItems(List.of(item));
            sections.add(fallback);
        }

        return sections;
    }

    private List<LearningRecommendationDto.Item> buildImmediateItems(
            List<DomainCandidate> weakestDomains,
            InterviewReport report,
            int limit) {

        List<LearningRecommendationDto.Item> items = new ArrayList<>();

        for (int i = 0; i < weakestDomains.size() && items.size() < limit; i++) {
            DomainCandidate domain = weakestDomains.get(i);
            LearningRecommendationDto.Item item = new LearningRecommendationDto.Item();
            item.setItemId("immediate-" + (i + 1));
            item.setTitle(domain.domainName + " 快速补强");
            item.setReason("该知识域当前得分约 " + Math.round(domain.score) + " 分，建议先补齐高频概念和常见追问。");
            item.setResourceType("practice");
            item.setDomainCode(domain.domainCode);
            item.setDomainName(domain.domainName);
            item.setEstimatedMinutes(20 + (i * 10));
            item.setLink(buildSearchLink(domain.domainName + " 面试 高频题"));
            items.add(item);
        }

        if (!items.isEmpty()) {
            return items;
        }

        List<String> weaknesses = report.getWeaknesses();
        if (weaknesses == null) {
            return items;
        }

        for (int i = 0; i < weaknesses.size() && items.size() < limit; i++) {
            String weak = weaknesses.get(i);
            LearningRecommendationDto.Item item = new LearningRecommendationDto.Item();
            item.setItemId("immediate-weak-" + (i + 1));
            item.setTitle("薄弱点补强：" + trimText(weak, 28));
            item.setReason("该问题在本场面试中反复出现，建议先做针对性练习。");
            item.setResourceType("practice");
            item.setEstimatedMinutes(30);
            item.setLink(buildSearchLink(weak + " 面试 讲解"));
            items.add(item);
        }

        return items;
    }

    private List<LearningRecommendationDto.Item> buildFocusedItems(
            List<DomainCandidate> weakestDomains,
            InterviewReport report,
            int limit) {

        List<String> topics = report.getRecommendedTopics();
        if (topics == null || topics.isEmpty()) {
            topics = report.getImprovementSuggestions();
        }

        List<LearningRecommendationDto.Item> items = new ArrayList<>();
        if (topics == null) {
            return items;
        }

        for (int i = 0; i < topics.size() && items.size() < limit; i++) {
            String topic = topics.get(i);
            DomainCandidate domain = weakestDomains.isEmpty() ? null : weakestDomains.get(i % weakestDomains.size());

            LearningRecommendationDto.Item item = new LearningRecommendationDto.Item();
            item.setItemId("focused-" + (i + 1));
            item.setTitle(trimText(topic, 36));
            item.setReason("和本场薄弱点直接相关，建议本周完成一次系统复习并输出笔记。");
            item.setResourceType("article");
            item.setDomainCode(domain == null ? null : domain.domainCode);
            item.setDomainName(domain == null ? null : domain.domainName);
            item.setEstimatedMinutes(45);
            item.setLink(buildSearchLink(topic + " 学习路线"));
            items.add(item);
        }

        return items;
    }

    private List<LearningRecommendationDto.Item> buildLongTermItems(
            InterviewSession session,
            InterviewReport report,
            int limit) {

        List<LearningRecommendationDto.Item> items = new ArrayList<>();

        LearningRecommendationDto.Item roadmap = new LearningRecommendationDto.Item();
        roadmap.setItemId("long-term-1");
        roadmap.setTitle(resolveRoleName(session.getPositionCode()) + " 能力升级路线");
        roadmap.setReason("围绕目标岗位补齐系统设计、项目深挖和表达能力，形成可持续提升闭环。");
        roadmap.setResourceType("project");
        roadmap.setEstimatedMinutes(180);
        roadmap.setLink(buildSearchLink(resolveRoleName(session.getPositionCode()) + " 面试 进阶路线"));
        items.add(roadmap);

        List<String> suggestions = report.getImprovementSuggestions();
        if (suggestions != null) {
            for (int i = 0; i < suggestions.size() && items.size() < Math.max(limit, 2); i++) {
                String suggestion = suggestions.get(i);
                LearningRecommendationDto.Item item = new LearningRecommendationDto.Item();
                item.setItemId("long-term-" + (i + 2));
                item.setTitle(trimText(suggestion, 36));
                item.setReason("把这条建议拆成 1~2 周的小目标并持续打卡，效果会明显更稳定。");
                item.setResourceType("project");
                item.setEstimatedMinutes(120);
                item.setLink(buildSearchLink(suggestion + " 实战 项目"));
                items.add(item);
            }
        }

        return items;
    }

    private List<DomainCandidate> extractWeakestDomains(InterviewReport report, int limit) {
        if (report.getSkillDomainScores() == null || report.getSkillDomainScores().isEmpty()) {
            return List.of();
        }

        return report.getSkillDomainScores().stream()
                .map(this::toDomainCandidate)
                .sorted(Comparator.comparingDouble(DomainCandidate::score))
                .limit(Math.max(limit, 1))
                .toList();
    }

    private DomainCandidate toDomainCandidate(Map<String, Object> scoreMap) {
        String domainCode = asString(scoreMap.get("domainCode"), "general");
        String domainName = asString(scoreMap.get("domainName"), domainCode);
        double score = asDouble(scoreMap.get("score"), 70D);
        return new DomainCandidate(domainCode, domainName, score);
    }

    private String buildSearchLink(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return "https://www.google.com/search?q=" + encoded;
    }

    private String resolveRoleName(String positionCode) {
        if (positionCode == null) {
            return "目标岗位";
        }
        return switch (positionCode) {
            case "JAVA_BACKEND" -> "Java 后端";
            case "GO_BACKEND" -> "Go 后端";
            case "FRONTEND" -> "前端";
            case "DATA_ENGINEER" -> "数据工程";
            case "QA" -> "测试";
            case "DEVOPS" -> "DevOps";
            default -> positionCode;
        };
    }

    private String trimText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "未命名主题";
        }
        String v = text.trim();
        if (v.length() <= maxLength) {
            return v;
        }
        return v.substring(0, Math.max(maxLength - 1, 1)) + "…";
    }

    private String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? defaultValue : v;
    }

    private double asDouble(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignore) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private record DomainCandidate(String domainCode, String domainName, double score) {
    }
}
