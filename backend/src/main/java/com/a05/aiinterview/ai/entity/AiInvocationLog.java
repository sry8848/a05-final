package com.a05.aiinterview.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 调用审计日志实体，对应 ai_invocation_logs 表。
 * 每次调用大模型（Planner、出题、评估等）均记录一条日志，用于复盘调优和排障。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_invocation_logs", autoResultMap = true)
public class AiInvocationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long questionId;
    private Long userId;

    /** Prompt 标识，如 planner / question_generation_stream / evaluation_decision */
    private String promptCode;

    /** Prompt 版本，如 v1 */
    private String promptVersion;

    /** 模型供应商，如 openai / mock */
    private String modelProvider;

    /** 模型名称，如 gpt-4o-mini */
    private String modelName;

    private java.math.BigDecimal temperature;

    /** 输入 Token 数 */
    private Integer requestTokens;

    /** 输出 Token 数 */
    private Integer responseTokens;

    /** 端到端延迟（毫秒） */
    private Integer latencyMs;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> retrievalContextJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestPayloadJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> responsePayloadJson;

    /** 是否成功 */
    private Boolean success;

    private String errorMessage;

    private LocalDateTime createdAt;
}
