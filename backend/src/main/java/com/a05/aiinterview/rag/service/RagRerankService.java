package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.dto.RagRetrievalRequest;

import java.util.List;

/**
 * RAG 精排服务接口。
 *
 * <p>默认实现会调用商业化 rerank API，对 dense 召回候选做最终排序。
 */
public interface RagRerankService {

    List<RerankResult> rerank(RagRetrievalRequest request, List<RerankCandidate> candidates);

    record RerankCandidate(
            String questionId,
            String questionText,
            String intentConcept,
            String referenceContext,
            List<String> scoringKeyPoints,
            List<String> scoringPitfalls
    ) {
    }

    record RerankResult(
            String questionId,
            double relevanceScore
    ) {
    }
}
