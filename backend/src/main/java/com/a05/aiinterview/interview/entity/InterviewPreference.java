package com.a05.aiinterview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试准备偏好实体，对应 interview_preferences 表。
 * 每用户仅保留最近一条，记录上次在准备页的填写内容，用于下次预填表单。
 */
@Data
@TableName("interview_preferences")
public class InterviewPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（唯一键，每用户仅一条记录） */
    private Long userId;

    /** 最近选择的岗位枚举，如 JAVA_BACKEND */
    private String targetRole;

    /** 最近选择的工作年限枚举，如 SENIOR */
    private String experienceLevel;

    /** 最近选择的面试模式：practice / professional */
    private String mode;

    /** 最近填写的侧重知识点 */
    private String focusTopics;

    /** 最近设置的思考时间限制（秒） */
    private Integer thinkTimeLimitSeconds;

    /** 最近设置的回答时间限制（秒） */
    private Integer answerTimeLimitSeconds;

    private LocalDateTime updatedAt;
}
