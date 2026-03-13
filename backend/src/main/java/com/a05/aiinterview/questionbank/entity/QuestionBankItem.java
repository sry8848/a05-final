package com.a05.aiinterview.questionbank.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "question_bank_items", autoResultMap = true)
public class QuestionBankItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long questionId;

    private Long sessionId;

    private Long domainId;

    private BigDecimal score;

    private String tag;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sourceSnapshotJson;

    private LocalDateTime createdAt;
}
