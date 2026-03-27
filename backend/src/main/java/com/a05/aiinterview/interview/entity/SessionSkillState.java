package com.a05.aiinterview.interview.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识域考察状态实体，对应 session_skill_states 表。
 * 每场面试 × 每个知识域 = 一条记录，记录该知识域在本场面试中的覆盖进度。
 * 初始化时由 StateLedgerInitService 批量创建，随答题进度动态更新。
 */
@Data
@TableName(value = "session_skill_states", autoResultMap = true)
public class SessionSkillState {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属面试会话 ID */
    private Long sessionId;

    /** 关联的知识域 code */
    private String domainCode;

    /** 考察状态：uncovered / in_progress / covered / circuit_broken */
    private String status;

    /** 该知识域已被考察的题数 */
    private Integer testedCount;

    /** 是否已"问透"（满足问透判定规则后置为 true，后续降权） */
    private Boolean saturated;

    /** 相关题目 ID 列表（JSON 数组），如 [5001, 5003] */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> evidenceRefs;

    /** AI 对该知识域的定性备注 */
    private String aiNotes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
