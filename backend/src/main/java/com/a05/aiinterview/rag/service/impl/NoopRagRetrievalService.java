package com.a05.aiinterview.rag.service.impl;

import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * RAG 检索服务的空实现（No-Op）。
 *
 * <p>当 {@code rag.enabled=false}（默认值）时激活，直接返回空上下文，
 * 确保与接入 RAG 前的出题行为完全一致（向后兼容）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rag.enabled", havingValue = "false", matchIfMissing = true)
public class NoopRagRetrievalService implements RagRetrievalService {

    @Override
    public RagContext retrieve(RagRetrievalRequest request) {
        log.debug("RAG 未启用（rag.enabled=false），跳过检索, domainCode={}", request.getDomainCode());
        return RagContext.empty();
    }
}
