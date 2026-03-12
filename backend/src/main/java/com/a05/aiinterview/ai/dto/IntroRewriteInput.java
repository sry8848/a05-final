package com.a05.aiinterview.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 首题 INTRO 改写入参。
 * 由首题策略服务先给出底稿，再结合候选人上下文与历史禁用语句做自然化改写。
 */
@Data
@Builder
public class IntroRewriteInput {

    private String positionCode;
    private String experienceLevel;
    private String mode;

    /** 题库底稿原文 */
    private String basePrompt;

    /** 最近若干场 INTRO 题干（用于避免重复） */
    private List<String> recentPrompts;

    /** 历史禁用措辞（用于避免过度复用同句式） */
    private List<String> avoidPhrases;
}
