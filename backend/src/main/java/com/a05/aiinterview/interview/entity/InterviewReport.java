package com.a05.aiinterview.interview.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试报告实体，对应 interview_reports 表。
 * 每场面试结束后由 ReportGenerationService 异步生成并持久化，
 * 与 interview_sessions 一对一关联（session_id 唯一键）。
 *
 * <p>报告分两层：
 * <ul>
 *   <li>总体层：overallScore、summary、strengths、weaknesses、improvementSuggestions</li>
 *   <li>知识域明细层：skillDomainScores（逐域得分、AI 点评）</li>
 * </ul>
 */
@Data
@TableName(value = "interview_reports", autoResultMap = true)
public class InterviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应的面试会话 ID（全局唯一） */
    private Long sessionId;

    /** 综合得分，0~100，保留 1 位小数 */
    private BigDecimal overallScore;

    /**
     * 逐知识域评分明细列表（JSON）。
     * 每项包含 domainCode、domainName、score、commentary。
     * 前端用于渲染雷达图和各域折叠卡片。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> skillDomainScores;

    /**
     * 专业模式综合能力雷达数据（JSON）。
     * 练习模式可为 null；专业模式由 AI 填充（沟通、逻辑、表达等维度）。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> comprehensiveRadarScores;

    /** 总结评语（2~3 段，不超过 300 字） */
    private String summary;

    /** 优势列表（JSON 数组，3~5 条） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> strengths;

    /** 薄弱点列表（JSON 数组，3~5 条） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> weaknesses;

    /** 提升建议列表（JSON 数组，3~5 条） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> improvementSuggestions;

    /** 推荐练习知识点列表（JSON 数组，可为 null） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> recommendedTopics;

    /**
     * 互动式逐字稿（JSON，可为 null）。
     * 用于反馈页逐字稿展示，包含题目序列和候选人回答的结构化文本。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> interactiveTranscriptJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
