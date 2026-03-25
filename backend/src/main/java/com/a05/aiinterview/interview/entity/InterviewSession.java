package com.a05.aiinterview.interview.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.a05.aiinterview.common.enums.ExperienceLevel;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 面试会话实体，对应 interview_sessions 表。
 * 每条记录代表用户发起的一场完整面试，贯穿从创建到报告生成的全生命周期。
 */
@Data
@TableName(value = "interview_sessions", autoResultMap = true)
public class InterviewSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起本场面试的用户 ID */
    private Long userId;

    /** 本次面试选用的简历 ID，可为空（未传简历时） */
    private Long resumeId;

    /** 本场标题，如 "Java 后端开发模拟面试 - 2026-03-09" */
    private String title;

    /** 目标岗位枚举值，如 JAVA_BACKEND */
    private String targetRole;

    /** 本场采用的岗位知识域版本，用于兼容历史报告 */
    private Integer positionDomainVersion;

    /** 工作年限分层枚举值，如 SENIOR */
    private String experienceLevel;

    /** 面试模式：practice / professional */
    private String mode;

    /** JD 文本，可为空 */
    private String jobDescription;

    /** 用户指定侧重点（自由文本） */
    private String focusTopics;

    /** 当前题号，从 1 开始，0 表示尚未出题 */
    private Integer currentQuestionNo;

    /** 上下文窗口大小：最近 x 题读取完整 Q/A，默认 5 */
    private Integer contextWindowSize;

    /** 专业模式思考时间限制（秒） */
    private Integer thinkTimeLimitSeconds;

    /** 专业模式回答时间限制（秒） */
    private Integer answerTimeLimitSeconds;

    /**
     * 第一题快照（JSON），status=in_progress 时由 GET /interviews/{id} 内嵌返回。
     * 结构与 interview_questions 的关键字段一致。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> firstQuestionJson;

    /**
     * Planner 生成的主考纲（JSON），包含规划推理、知识域与项目/实习条目。
     * 格式参见当前 planner 新契约。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> syllabusJson;

    /**
     * 状态账本（JSON），记录知识域覆盖、当前条目焦点与最近决策轨迹。
     * 是后端唯一可信的过程状态。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> stateLedgerJson;

    /** 会话状态：planning / in_progress / report_generating / completed / aborted */
    private String status;

    /** 本场调用的模型供应商，如 openai / mock */
    private String modelProvider;

    /** 本场调用的模型名称，如 gpt-4o-mini */
    private String modelName;

    /** 面试正式开始时间（首题就绪时更新） */
    private LocalDateTime startedAt;

    /** 面试结束时间 */
    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = ExperienceLevel.normalizeStoredValue(experienceLevel);
    }
}
