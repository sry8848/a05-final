package com.a05.aiinterview.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 题目生成 AI 调用出参。
 * 格式对齐 prompt-strategy.md §4.4 的输出规范。
 */
@Data
public class QuestionGenerationOutput {

    /** 题目正文 */
    private String stem;

    /** 核心考察点，如 "缓存击穿" */
    private String targetSkill;

    /** 理想回答要点列表 */
    private List<String> expectedPoints;

    /** 难度描述：easy / medium / hard（供日志参考，不直接存库） */
    private String difficulty;

    /** 目标深度（AI 实际分配，可能与请求略有偏差） */
    private String targetDepth;
}
