package com.a05.aiinterview.interview.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 面试题目实体，对应 interview_questions 表。
 * 每条记录代表 AI 动态生成的一道题目，在会话内按 question_no 编号。
 */
@Data
@TableName(value = "interview_questions", autoResultMap = true)
public class InterviewQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属面试会话 ID */
    private Long sessionId;

    /** 题号（在本场面试中唯一，从 1 开始） */
    private Integer questionNo;

    /** 题目类型：INTRO / PROJECT_DEEP_DIVE / SCENARIO / PRINCIPLE / BEHAVIORAL */
    private String questionType;

    /** 主知识域 code */
    private String domainCode;

    /** 副知识域 code 列表（JSON 数组），可为空 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> secondaryDomainCodes;

    /** 题目正文 */
    private String stem;

    /** 当前题目焦点，如 "缓存击穿" */
    private String focusPoint;

    /** 理想回答要点列表（JSON 数组），如 ["布隆过滤器原理", "误判率控制"] */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> expectedPoints;

    /** 题目状态：pending / asked / answered / skipped */
    private String status;

    /** 面试官提示文本（用户点击"获取提示"后由 AI 生成并写入） */
    private String hintText;

    /** 生成本题时的决策上下文快照（用于调试和复盘） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> generationContextJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
