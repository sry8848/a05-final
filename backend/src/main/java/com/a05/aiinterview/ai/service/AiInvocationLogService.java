package com.a05.aiinterview.ai.service;

import com.a05.aiinterview.ai.entity.AiInvocationLog;
import com.a05.aiinterview.ai.mapper.AiInvocationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * AI 调用日志服务。
 * 负责将每次大模型调用的元数据异步写入 ai_invocation_logs 表，
 * 用于后续调试、prompt 调优和 token 成本统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInvocationLogService {

    private final AiInvocationLogMapper aiInvocationLogMapper;

    /**
     * 异步保存 AI 调用日志。
     * 采用异步写入避免日志 IO 阻塞主链路响应。
     *
     * @param logEntry 构建好的日志实体
     */
    @Async
    public void saveAsync(AiInvocationLog logEntry) {
        try {
            aiInvocationLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 日志写入失败不影响主流程，仅打印错误
            log.error("AI 调用日志落库失败, promptCode={}, sessionId={}", logEntry.getPromptCode(), logEntry.getSessionId(), e);
        }
    }

    /**
     * 同步保存 AI 调用日志（用于需要立即知道日志 ID 的场景）。
     *
     * @param logEntry 构建好的日志实体
     */
    public void save(AiInvocationLog logEntry) {
        try {
            aiInvocationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("AI 调用日志落库失败, promptCode={}, sessionId={}", logEntry.getPromptCode(), logEntry.getSessionId(), e);
        }
    }
}
