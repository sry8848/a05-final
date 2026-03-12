package com.a05.aiinterview.ai.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * AI 调用统一结果包装类。
 * 封装结构化输出、Token 消耗及端到端延迟，供审计日志填充使用。
 *
 * @param <T> AI 输出 DTO 类型
 */
@Getter
@Builder
public class AiCallResult<T> {

    /** 解析后的结构化输出对象 */
    private final T output;

    /** 实际使用的 Prompt 标识 */
    private final String promptCode;

    /** 实际使用的 Prompt 版本 */
    private final String promptVersion;

    /** Prompt Token 数（输入） */
    private final int promptTokens;

    /** Completion Token 数（输出） */
    private final int responseTokens;

    /** 端到端调用延迟（毫秒），从发起请求到收到完整响应 */
    private final long latencyMs;
}
