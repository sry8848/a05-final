package com.a05.aiinterview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 单题重答记录实体。
 * 与整场面试会话解耦，只围绕原题快照 + 新答案做独立评估。
 */
@Data
@TableName(value = "question_redo_attempts", autoResultMap = true)
public class QuestionRedoAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sourceSessionId;

    private Long sourceQuestionId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sourceSnapshotJson;

    private String answerText;

    /**
     * pending / generating / ready / failed
     */
    private String evaluationStatus;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> evaluationJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
