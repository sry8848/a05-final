package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首题 INTRO 变体策略服务。
 * 负责底稿池轮换与历史去重策略，不涉及 AI 调用。
 */
@Service
public class IntroQuestionStrategyService {

    static final int DEFAULT_RECENT_WINDOW = 5;

    private static final List<IntroVariant> DEFAULT_VARIANTS = List.of(
            new IntroVariant("INTRO_V1", "请先做一个1-2分钟的自我介绍，重点说说你的技术方向和最近一年最有代表性的项目。"),
            new IntroVariant("INTRO_V2", "先请你做个简短自我介绍，特别想听你目前主攻的技术栈，以及最近做过最有挑战的项目。"),
            new IntroVariant("INTRO_V3", "我们先从自我介绍开始吧，请围绕你的技术背景、职责边界和近一年代表性项目展开。"),
            new IntroVariant("INTRO_V4", "开场先请你介绍一下自己，重点讲讲你擅长的后端方向和一个你主导或深度参与的项目。"),
            new IntroVariant("INTRO_V5", "先来个简短自我介绍：你的工作年限、核心技术栈，以及最近最能体现能力的项目是什么？"),
            new IntroVariant("INTRO_V6", "开始前请先做自我介绍，建议重点说明你的技术成长路径和最近一段项目经历。"),
            new IntroVariant("INTRO_V7", "我们先热个身，请你做1-2分钟自我介绍，聚焦技术专长和近期代表项目的关键贡献。"),
            new IntroVariant("INTRO_V8", "先请你介绍一下自己，重点分享你当前技术定位，以及最近一个项目里你解决的核心问题。")
    );

    private final InterviewQuestionMapper interviewQuestionMapper;
    private final List<IntroVariant> introVariants;
    private final int recentWindow;

    @Autowired
    public IntroQuestionStrategyService(InterviewQuestionMapper interviewQuestionMapper) {
        this(interviewQuestionMapper, DEFAULT_VARIANTS, DEFAULT_RECENT_WINDOW);
    }

    IntroQuestionStrategyService(InterviewQuestionMapper interviewQuestionMapper,
                                 List<IntroVariant> introVariants,
                                 int recentWindow) {
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.introVariants = List.copyOf(introVariants);
        this.recentWindow = recentWindow;
    }

    /**
     * 按用户历史选择本场首题 INTRO 底稿。
     * 规则：优先避开最近 N 场已用变体；若无可选则回退到全量中“最久未使用”。
     */
    public IntroQuestionSelection selectIntroForUser(Long userId) {
        List<InterviewQuestion> recent = selectHistory(userId, recentWindow);
        List<InterviewQuestion> all = selectHistory(userId, null);

        Set<String> recentVariantDenySet = new LinkedHashSet<>();
        for (InterviewQuestion q : recent) {
            String variantId = resolveVariantId(q);
            if (variantId != null && !variantId.isBlank()) {
                recentVariantDenySet.add(variantId);
            }
        }

        Map<String, LocalDateTime> variantLastUsed = new LinkedHashMap<>();
        for (InterviewQuestion q : all) {
            String variantId = resolveVariantId(q);
            if (variantId == null || variantId.isBlank()) {
                continue;
            }
            variantLastUsed.putIfAbsent(variantId, q.getCreatedAt());
        }

        List<IntroVariant> candidates = introVariants.stream()
                .filter(v -> !recentVariantDenySet.contains(v.variantId()))
                .toList();
        if (candidates.isEmpty()) {
            candidates = introVariants;
        }

        IntroVariant selected = selectLeastRecentlyUsed(candidates, variantLastUsed);
        List<String> recentPrompts = buildRecentPrompts(recent);

        return IntroQuestionSelection.builder()
                .variantId(selected.variantId())
                .basePrompt(selected.basePrompt())
                .recentPrompts(recentPrompts)
                .avoidPhrases(recentPrompts)
                .historyAvoidCount(recentPrompts.size())
                .build();
    }

    private List<InterviewQuestion> selectHistory(Long userId, Integer limit) {
        if (userId == null) {
            return List.of();
        }
        List<InterviewQuestion> history = interviewQuestionMapper.selectUserFirstIntroQuestions(userId, limit);
        return history != null ? history : List.of();
    }

    private IntroVariant selectLeastRecentlyUsed(List<IntroVariant> candidates,
                                                 Map<String, LocalDateTime> variantLastUsed) {
        IntroVariant best = null;
        LocalDateTime bestTime = null;

        for (IntroVariant candidate : candidates) {
            LocalDateTime usedAt = variantLastUsed.get(candidate.variantId());
            if (best == null) {
                best = candidate;
                bestTime = usedAt;
                continue;
            }

            if (isBetterCandidate(candidate.variantId(), usedAt, best.variantId(), bestTime)) {
                best = candidate;
                bestTime = usedAt;
            }
        }
        return best != null ? best : introVariants.get(0);
    }

    private boolean isBetterCandidate(String candidateVariantId,
                                      LocalDateTime candidateUsedAt,
                                      String currentBestVariantId,
                                      LocalDateTime currentBestUsedAt) {
        if (candidateUsedAt == null && currentBestUsedAt != null) {
            return true;
        }
        if (candidateUsedAt != null && currentBestUsedAt == null) {
            return false;
        }
        if (candidateUsedAt == null) {
            return candidateVariantId.compareTo(currentBestVariantId) < 0;
        }
        int timeCompare = candidateUsedAt.compareTo(currentBestUsedAt);
        if (timeCompare != 0) {
            return timeCompare < 0;
        }
        return candidateVariantId.compareTo(currentBestVariantId) < 0;
    }

    private List<String> buildRecentPrompts(List<InterviewQuestion> recent) {
        List<String> prompts = new ArrayList<>();
        for (InterviewQuestion q : recent) {
            String prompt = resolveBasePromptOrStem(q);
            if (prompt != null && !prompt.isBlank()) {
                prompts.add(prompt);
            }
        }
        return List.copyOf(prompts);
    }

    private String resolveVariantId(InterviewQuestion question) {
        if (question == null || question.getGenerationContextJson() == null) {
            return null;
        }
        Object value = question.getGenerationContextJson().get("variantId");
        return value instanceof String s ? s : null;
    }

    private String resolveBasePromptOrStem(InterviewQuestion question) {
        if (question == null) {
            return null;
        }
        if (question.getGenerationContextJson() != null) {
            Object value = question.getGenerationContextJson().get("basePromptText");
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return question.getStem();
    }

    @Data
    @Builder
    public static class IntroQuestionSelection {
        private String variantId;
        private String basePrompt;
        private List<String> recentPrompts;
        private List<String> avoidPhrases;
        private int historyAvoidCount;
    }

    public record IntroVariant(String variantId, String basePrompt) {
    }
}
