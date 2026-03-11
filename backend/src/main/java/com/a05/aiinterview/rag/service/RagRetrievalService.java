package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;

/**
 * RAG 检索服务接口。
 *
 * <p>根据下一题策略（知识域、题型、焦点）从向量知识库中检索相关知识片段，
 * 并聚合为可直接注入出题 Prompt 的字符串上下文。
 *
 * <p>有两个实现：
 * <ul>
 *   <li>{@code RagRetrievalServiceImpl}：{@code rag.enabled=true} 时激活，执行真实向量检索</li>
 *   <li>{@code NoopRagRetrievalService}：{@code rag.enabled=false}（默认）时激活，直接返回空上下文</li>
 * </ul>
 */
public interface RagRetrievalService {

    /**
     * 执行 RAG 检索，返回可注入 Prompt 的知识上下文。
     *
     * <p>实现类必须保证：检索异常时降级返回 {@link RagContext#empty()}，不抛出异常阻断主链路。
     *
     * @param request 检索请求（包含 domainCode、questionType、focusPoint 等维度）
     * @return RAG 上下文结果；无命中或被禁用时返回 {@link RagContext#empty()}
     */
    RagContext retrieve(RagRetrievalRequest request);
}
