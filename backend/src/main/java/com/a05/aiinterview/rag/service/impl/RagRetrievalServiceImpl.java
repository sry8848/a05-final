package com.a05.aiinterview.rag.service.impl;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagRerankService;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 面试题卡检索服务真实实现。
 *
 * <p>固定链路：lexical 预过滤 -> dense recall -> 业务重排 -> 结构化结果。
 * lexical 仅负责候选收缩，不承担独立排序；不再保留旧的伪 sparse + RRF 链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class RagRetrievalServiceImpl implements RagRetrievalService {

    /**
     * 词法预过滤的放大因子。
     *
     * <p>预过滤候选数 = 结果限制 × 放大因子
     * <p>设计目的：先快速收缩候选集，再进行昂贵的向量检索
     */
    private static final int LEXICAL_PREFILTER_MULTIPLIER = 4;

    private final VectorStore vectorStore;
    private final QdrantClient qdrantClient;
    private final RagRerankService ragRerankService;
    private final RagProperties ragProperties;

    /**
     * 执行完整的RAG检索流程。
     *
     * <p>固定链路：lexical预过滤 → dense召回 → rerank重排 → 结构化结果
     *
     * <p>处理流程：
     * <ol>
     *   <li>检查是否需要检索（shouldRetrieve=false则返回空）</li>
     *   <li>lexical预过滤：快速收缩候选集</li>
     *   <li>dense召回：使用向量相似度检索</li>
     *   <li>rerank重排：优先商业重排，失败回退到本地排序</li>
     *   <li>硬性护栏：验证题型和知识域匹配</li>
     *   <li>构建审计信息</li>
     *   <li>构建结构化的RagContext</li>
     * </ol>
     *
     * @param request RAG检索请求
     * @return 检索结果上下文
     */
    @Override
    public RagContext retrieve(RagRetrievalRequest request) {
        // ========== 步骤0：参数校验 ==========
        // 如果请求为空或不需要检索，直接返回空上下文（不影响主流程）
        if (request == null || !request.isShouldRetrieve()) {
            log.debug("RAG 跳过检索, shouldRetrieve=false");
            return RagContext.empty();
        }

        log.info("RAG 检索开始, questionType={}, domainCode={}, difficultyHint={}, queryText={}",
                request.getQuestionType(), request.getDomainCode(),
                request.getDifficultyHint(), request.getQueryText());
        try {
            // ========== 步骤1：Lexical 预过滤 ==========
            // 使用 Qdrant Scroll API 进行词法检索，快速收缩候选集
            // 候选数 = resultLimit × 4 = 20 条
            List<Candidate> lexicalCandidates = lexicalPrefilter(request);

            // ========== 步骤2：Dense 召回 ==========
            // 使用向量相似度检索，从 Lexical 候选集中精准召回
            // 只保留 Lexical 候选集中且向量检索命中的文档
            List<Candidate> denseCandidates = denseRecall(request, lexicalCandidates);

            // 保存 Dense 召回结果用于审计（rerank 前）
            List<Candidate> rerankInputCandidates = List.copyOf(denseCandidates);

            // ========== 步骤3：Rerank 重排 ==========
            // 优先使用商业 Rerank（阿里云百炼），失败回退到本地排序
            List<Candidate> reranked = rerank(rerankInputCandidates, request);

            // 保存 Rerank 后的 Top N ID 用于审计
            List<String> rerankPostTopQuestionIds = topQuestionIds(reranked, resolveResultLimit());

            // ========== 步骤4：Hard Guardrails 硬性护栏 ==========
            // 验证题型和知识域匹配，过滤不符合的候选
            reranked = applyHardGuardrails(reranked, request);

            // ========== 步骤5：构建审计信息（第一部分）==========
            // 记录各阶段候选数量和 ID，用于调试和分析
            RagContext.RetrievalAudit retrievalAudit = RagContext.RetrievalAudit.builder()
                    .retrievalTriggered(true)
                    .lexicalCandidateCount(lexicalCandidates.size())                    // Lexical 候选数
                    .denseCandidateCount(denseCandidates.size())                        // Dense 召回数
                    .rerankPreTopQuestionIds(topQuestionIds(rerankInputCandidates, resolveResultLimit()))  // Rerank 前 Top ID
                    .rerankPostTopQuestionIds(rerankPostTopQuestionIds)                // Rerank 后 Top ID
                    .injectedQuestionIds(List.of())                                    // 注入的题目 ID（后续填充）
                    .build();

            // ========== 步骤6：检查是否无命中 ==========
            if (reranked.isEmpty()) {
                log.info("RAG 检索无命中, questionType={}, domainCode={}",
                        request.getQuestionType(), request.getDomainCode());
                // 返回空触发器（检索已执行但无结果）
                return RagContext.emptyTriggered(retrievalAudit);
            }

            // ========== 步骤7：取 Top K 结果 ==========
            int resultLimit = resolveResultLimit();
            List<Candidate> topCandidates = reranked.stream()
                    .limit(resultLimit)
                    .toList();

            // ========== 步骤8：转换为检索材料 ==========
            // 将 Candidate 转换为 RetrievedMaterial 用于返回
            List<RagContext.RetrievedMaterial> retrievedMaterials = topCandidates.stream()
                    .map(this::toRetrievedMaterial)
                    .toList();

            // ========== 步骤9：收集追问候选 ==========
            // 用于后续追问功能
            List<String> followUpCandidates = collectFollowUpCandidates(topCandidates, resultLimit);

            // ========== 步骤10：构建上下文文本 ==========
            // 用于注入到 AI Prompt 中
            String summary = buildSummary(retrievedMaterials);
            String contextText = buildContextText(retrievedMaterials);

            // ========== 步骤11：返回完整结果 ==========
            return RagContext.builder()
                    .summary(summary)                                          // 检索摘要
                    .contextText(contextText)                                  // 上下文文本（注入 Prompt）
                    .retrievedMaterials(retrievedMaterials)                     // 检索材料列表
                    .followUpCandidates(followUpCandidates)                   // 追问候选
                    .retrievalAudit(RagContext.RetrievalAudit.builder()        // 完整审计信息
                            .retrievalTriggered(true)
                            .lexicalCandidateCount(retrievalAudit.getLexicalCandidateCount())
                            .denseCandidateCount(retrievalAudit.getDenseCandidateCount())
                            .rerankPreTopQuestionIds(retrievalAudit.getRerankPreTopQuestionIds())
                            .rerankPostTopQuestionIds(retrievalAudit.getRerankPostTopQuestionIds())
                            .injectedQuestionIds(topQuestionIds(topCandidates, resultLimit))  // 最终注入的题目 ID
                            .build())
                    .hitCount(retrievedMaterials.size())                       // 命中数量
                    .empty(false)                                              // 非空
                    .build();

        } catch (Exception e) {
            // ========== 异常处理：RAG 失败不影响主流程 ==========
            log.error("RAG 检索异常, questionType={}, domainCode={}",
                    request.getQuestionType(), request.getDomainCode(), e);
            // 返回空触发器（异常状态）
            return RagContext.emptyTriggered(RagContext.RetrievalAudit.empty(true));
        }
    }

    /**
     * 执行向量召回（Dense Recall）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>解析查询文本（queryText 或 focusPoint 或 keywords）</li>
     *   <li>构建 SearchRequest（包含 topK、minScore、过滤条件）</li>
     *   <li>调用 vectorStore.similaritySearch() 执行向量检索</li>
     *   <li>过滤结果（只保留active=true且在lexical候选集中的ID）</li>
     *   <li>设置denseRank（稠密排序）</li>
     * </ol>
     *
     * @param request RAG检索请求
     * @param lexicalCandidates 词法预过滤的候选集
     * @return 向量召回后的候选列表
     */
    private List<Candidate> denseRecall(RagRetrievalRequest request, List<Candidate> lexicalCandidates) {
        String queryText = resolveDenseQueryText(request);
        if (!hasText(queryText)) {
            return List.of();
        }

        SearchRequest searchRequest = buildDenseSearchRequest(queryText, request, hasLexicalPrefilter(lexicalCandidates));
        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> allowedQuestionIds = allowedQuestionIds(lexicalCandidates);
        List<Candidate> candidates = new ArrayList<>(docs.size());
        for (int i = 0; i < docs.size(); i++) {
            Candidate candidate = candidateFromDocument(docs.get(i));
            if (!candidate.active) {
                continue;
            }
            if (!allowedQuestionIds.isEmpty() && !allowedQuestionIds.contains(candidate.questionId)) {
                continue;
            }
            candidate.denseRank = candidates.size() + 1;
            candidates.add(candidate);
        }
        return candidates;
    }

    /**
     * 执行词法预过滤（Lexical Prefilter）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>解析词法检索词（keywordQueries）</li>
     *   <li>构建Qdrant scroll请求</li>
     *   <li>执行scroll API快速检索候选集</li>
     *   <li>过滤结果（只保留active=true）</li>
     * </ol>
     *
     * <p>过滤条件：
     * <ul>
     *   <li>匹配question_text、intent_concept、keywords字段</li>
     *   <li>放大因子：resultLimit × 4</li>
     * </ul>
     *
     * @param request RAG检索请求
     * @return 词法预过滤后的候选列表
     * @throws Exception Qdrant API调用异常
     */
    private List<Candidate> lexicalPrefilter(RagRetrievalRequest request) throws Exception {
        List<String> lexicalTerms = resolveLexicalTerms(request);
        if (lexicalTerms.isEmpty()) {
            return List.of();
        }

        Points.ScrollPoints scrollRequest = buildLexicalPrefilterScrollRequest(request, lexicalTerms);
        Points.ScrollResponse response = qdrantClient.scrollAsync(scrollRequest).get(3, TimeUnit.SECONDS);
        if (response == null || response.getResultList().isEmpty()) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Points.RetrievedPoint point : response.getResultList()) {
            Candidate candidate = candidateFromPoint(point);
            if (candidate.active) {
                candidates.add(candidate);
            }
        }
        return dedupeByQuestionId(candidates);
    }

    /**
     * 构建 Dense 召回的 SearchRequest。
     *
     * <p>配置参数：
     * <ul>
     *   <li>queryText：查询文本</li>
     *   <li>topK：检索数量（有预过滤时放大4倍）</li>
     *   <li>similarityThreshold：最小相似度分数</li>
     *   <li>filterExpression：题型/知识域过滤条件</li>
     * </ul>
     *
     * @param queryText 查询文本
     * @param request RAG检索请求
     * @param widenForPrefilter 是否有预过滤（放大topK）
     * @return Spring AI SearchRequest
     */
    private SearchRequest buildDenseSearchRequest(String queryText, RagRetrievalRequest request, boolean widenForPrefilter) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(queryText)
                .topK(resolveDenseFetchLimit(widenForPrefilter))
                .similarityThreshold(ragProperties.getMinScore());

        String filterExpression = buildDenseFilterExpression(request);
        if (hasText(filterExpression)) {
            builder.filterExpression(filterExpression);
        }
        return builder.build();
    }

    /**
     * 构建 Dense 召回的过滤表达式。
     *
     * <p>过滤条件：
     * <ul>
     *   <li>question_type：题型匹配（PRINCIPLE/SCENARIO/PROJECT/BEHAVIORAL）</li>
     *   <li>domain_code：知识域编码匹配</li>
     * </ul>
     *
     * <p>多个条件使用 AND 连接
     *
     * @param request RAG检索请求
     * @return 过滤表达式字符串
     */
    private String buildDenseFilterExpression(RagRetrievalRequest request) {
        List<String> filters = new ArrayList<>();
        String questionType = normalizeQuestionTypeForCorpus(request.getQuestionType());
        if (hasText(questionType)) {
            filters.add("question_type == '" + questionType + "'");
        }
        if (hasText(request.getDomainCode())) {
            filters.add("domain_code == '" + request.getDomainCode().trim() + "'");
        }
        return String.join(" && ", filters);
    }

    /**
     * 构建 Lexical 预过滤的 Qdrant Scroll 请求。
     *
     * <p>配置参数：
     * <ul>
     *   <li>collectionName：集合名称</li>
     *   <li>filter：过滤条件（MUST + MIN_SHOULD）</li>
     *   <li>limit：检索数量（resultLimit × 4）</li>
     *   <li>withPayload：返回所有 payload 字段</li>
     *   <li>withVectors：不返回向量（只用于预过滤，不需要向量）</li>
     * </ul>
     *
     * @param request RAG检索请求
     * @param lexicalTerms 词法检索词列表
     * @return Qdrant ScrollPoints 请求
     */
    private Points.ScrollPoints buildLexicalPrefilterScrollRequest(RagRetrievalRequest request, List<String> lexicalTerms) {
        return Points.ScrollPoints.newBuilder()
                .setCollectionName(ragProperties.getCollectionName())
                .setFilter(buildLexicalPrefilterFilter(request, lexicalTerms))
                .setLimit(resolveLexicalPrefilterLimit())
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(false).build())
                .build();
    }

    /**
     * 构建 Lexical 预过滤的过滤条件。
     *
     * <p>过滤结构：
     * <ul>
     *   <li>MUST（必须满足）：
     *     <ul>
     *       <li>active = true（必须激活）</li>
     *       <li>question_type = 题型（如果有）</li>
     *       <li>domain_code = 知识域（如果有）</li>
     *     </ul>
     *   </li>
     *   <li>MIN_SHOULD（至少满足一个）：
     *     <ul>
     *       <li>matchText(question_text, term) - 题目文本包含词</li>
     *       <li>matchText(intent_concept, term) - 意图/概念包含词</li>
     *       <li>matchKeyword(keywords, term) - 关键词匹配</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param request RAG检索请求
     * @param lexicalTerms 词法检索词列表
     * @return Qdrant Filter
     */
    private Points.Filter buildLexicalPrefilterFilter(RagRetrievalRequest request, List<String> lexicalTerms) {
        List<Points.Condition> must = new ArrayList<>();
        must.add(ConditionFactory.match("active", true));

        String questionType = normalizeQuestionTypeForCorpus(request.getQuestionType());
        if (hasText(questionType)) {
            must.add(ConditionFactory.matchKeyword("question_type", questionType));
        }
        if (hasText(request.getDomainCode())) {
            must.add(ConditionFactory.matchKeyword("domain_code", request.getDomainCode().trim()));
        }

        List<Points.Condition> should = new ArrayList<>();
        for (String term : lexicalTerms) {
            should.add(ConditionFactory.matchText("question_text", term));
            should.add(ConditionFactory.matchText("intent_concept", term));
            should.add(ConditionFactory.matchKeyword("keywords", term));
        }
        return Points.Filter.newBuilder()
                .addAllMust(must)
                .setMinShould(Points.MinShould.newBuilder()
                        .addAllConditions(should)
                        .setMinCount(1)
                        .build())
                .build();
    }

    /**
     * 执行重排（Rerank）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>优先调用商业rerank服务（阿里云百炼gte-rerank-v2）</li>
     *   <li>如果商业rerank失败，回退到本地排序</li>
     *   <li>多级排序规则：finalScore降序 → denseRank升序 → questionId升序</li>
     * </ol>
     *
     * <p>本地排序分数计算公式：
     * <ul>
     *   <li>denseRankScore(denseRank) = 1.0 / denseRank</li>
     *   <li>denseRank 越小，分数越高</li>
     * </ul>
     *
     * <p>多级排序说明：
     * <ul>
     *   <li>第一级：finalScore（最终分数）降序，保证 rerank 效果好的排前面</li>
     *   <li>第二级：denseRank（向量召回排名）升序，保证向量召回效果好的排前面</li>
     *   <li>第三级：questionId 升序，保证结果稳定性</li>
     * </ul>
     *
     * @param denseCandidates 向量召回后的候选列表
     * @param request RAG检索请求
     * @return 重排后的候选列表
     */
    private List<Candidate> rerank(List<Candidate> denseCandidates, RagRetrievalRequest request) {
        List<Candidate> reranked = new ArrayList<>(denseCandidates);
        if (reranked.isEmpty()) {
            return reranked;
        }

        try {
            List<RagRerankService.RerankCandidate> rerankCandidates = reranked.stream()
                    .map(this::toRerankCandidate)
                    .toList();
            Map<String, Double> rerankScores = ragRerankService.rerank(request, rerankCandidates).stream()
                    .collect(Collectors.toMap(
                            RagRerankService.RerankResult::questionId,
                            RagRerankService.RerankResult::relevanceScore,
                            Math::max,
                            LinkedHashMap::new
                    ));
            for (Candidate candidate : reranked) {
                candidate.finalScore = rerankScores.getOrDefault(candidate.questionId, 0.0d);
            }
        } catch (Exception e) {
            log.warn("RAG 商业 rerank 失败，回退到本地排序, queryText={}, reason={}",
                    request.getQueryText(), e.getMessage());
            for (Candidate candidate : reranked) {
                candidate.finalScore = denseRankScore(candidate.denseRank);
            }
        }

        reranked.sort((left, right) -> {
            int scoreCompare = Double.compare(right.finalScore, left.finalScore);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int denseRankCompare = Integer.compare(nullSafeRank(left.denseRank), nullSafeRank(right.denseRank));
            if (denseRankCompare != 0) {
                return denseRankCompare;
            }
            return left.questionId.compareTo(right.questionId);
        });
        return reranked;
    }

    /**
     * 应用硬性护栏（Hard Guardrails）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>标准化预期题型和知识域</li>
     *   <li>逐个检查候选是否匹配硬性护栏</li>
     *   <li>只保留符合要求的候选</li>
     * </ol>
     *
     * <p>硬性护栏规则：
     * <ul>
     *   <li>BEHAVIORAL 题型：必须匹配 BEHAVIORAL 且知识域为空</li>
     *   <li>PROJECT_DEEP_DIVE 题型：知识域匹配或为空即可</li>
     *   <li>其他题型：必须精确匹配题型和知识域</li>
     * </ul>
     *
     * @param candidates 重排后的候选列表
     * @param request RAG检索请求
     * @return 符合硬性护栏的候选列表
     */
    private List<Candidate> applyHardGuardrails(List<Candidate> candidates, RagRetrievalRequest request) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String expectedQuestionType = normalizeQuestionTypeForCorpus(request.getQuestionType());
        String expectedDomainCode = normalizeText(request.getDomainCode());

        return candidates.stream()
                .filter(candidate -> matchesHardGuardrails(candidate, expectedQuestionType, expectedDomainCode))
                .toList();
    }

    private boolean matchesHardGuardrails(Candidate candidate, String expectedQuestionType, String expectedDomainCode) {
        String actualQuestionType = normalizeQuestionTypeForCorpus(candidate.questionType);
        String actualDomainCode = normalizeText(candidate.domainCode);

        if ("BEHAVIORAL".equals(expectedQuestionType)) {
            return "BEHAVIORAL".equals(actualQuestionType) && actualDomainCode.isBlank();
        }

        if ("PROJECT".equals(expectedQuestionType)) {
            if (!"PROJECT".equals(actualQuestionType)) {
                return false;
            }
            if (expectedDomainCode.isBlank()) {
                return true;
            }
            return expectedDomainCode.equals(actualDomainCode);
        }

        if (!expectedQuestionType.isBlank() && !expectedQuestionType.equals(actualQuestionType)) {
            return false;
        }
        if (expectedDomainCode.isBlank()) {
            return true;
        }
        return expectedDomainCode.equals(actualDomainCode);
    }

    private RagRerankService.RerankCandidate toRerankCandidate(Candidate candidate) {
        return new RagRerankService.RerankCandidate(
                candidate.questionId,
                candidate.questionText,
                candidate.intentConcept,
                candidate.referenceContext,
                candidate.scoringKeyPoints,
                candidate.scoringPitfalls
        );
    }

    private double denseRankScore(Integer denseRank) {
        if (denseRank == null || denseRank <= 0) {
            return 0.0d;
        }
        return 1.0d / denseRank;
    }

    private List<String> collectFollowUpCandidates(List<Candidate> candidates, int resultLimit) {
        LinkedHashSet<String> followUps = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            for (String followUpId : safeList(candidate.followUpIds)) {
                followUps.add(followUpId);
                if (followUps.size() >= resultLimit * 2) {
                    return List.copyOf(followUps);
                }
            }
        }
        return List.copyOf(followUps);
    }

    private List<String> topQuestionIds(List<Candidate> candidates, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> candidate.questionId)
                .filter(this::hasText)
                .limit(Math.max(0, limit))
                .toList();
    }

    private String buildSummary(List<RagContext.RetrievedMaterial> materials) {
        return materials.stream()
                .map(material -> material.getQuestionText() + "：" + material.getIntentConcept())
                .collect(Collectors.joining("\n"));
    }

    private String buildContextText(List<RagContext.RetrievedMaterial> materials) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < materials.size(); i++) {
            RagContext.RetrievedMaterial material = materials.get(i);
            sb.append("--- 题卡 ").append(i + 1).append(" ---\n");
            sb.append("题目：").append(material.getQuestionText()).append("\n");
            sb.append("考点：").append(material.getIntentConcept()).append("\n");
            sb.append("语境：").append(material.getReferenceContext()).append("\n");
            if (!safeList(material.getScoringKeyPoints()).isEmpty()) {
                sb.append("关键点：").append(String.join("；", material.getScoringKeyPoints())).append("\n");
            }
            if (!safeList(material.getScoringPitfalls()).isEmpty()) {
                sb.append("误区：").append(String.join("；", material.getScoringPitfalls())).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private RagContext.RetrievedMaterial toRetrievedMaterial(Candidate candidate) {
        return RagContext.RetrievedMaterial.builder()
                .questionId(candidate.questionId)
                .questionText(candidate.questionText)
                .intentConcept(candidate.intentConcept)
                .referenceContext(candidate.referenceContext)
                .scoringKeyPoints(candidate.scoringKeyPoints)
                .scoringPitfalls(candidate.scoringPitfalls)
                .followUpIds(candidate.followUpIds)
                .domainCode(candidate.domainCode)
                .questionType(candidate.questionType)
                .difficulty(candidate.difficulty)
                .keywords(candidate.keywords)
                .build();
    }

    private Candidate candidateFromDocument(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        return Candidate.builder()
                .questionId(asString(metadata.get("question_id")))
                .questionText(firstNonBlank(asString(metadata.get("question_text")), firstLine(doc.getText())))
                .intentConcept(asString(metadata.get("intent_concept")))
                .referenceContext(firstNonBlank(asString(metadata.get("reference_context")), doc.getText()))
                .scoringKeyPoints(asStringList(metadata.get("scoring_key_points")))
                .scoringPitfalls(asStringList(metadata.get("scoring_pitfalls")))
                .followUpIds(asStringList(metadata.get("follow_up_ids")))
                .domainCode(asString(metadata.get("domain_code")))
                .questionType(asString(metadata.get("question_type")))
                .difficulty(asString(metadata.get("difficulty")))
                .keywords(asStringList(metadata.get("keywords")))
                .docContent(doc.getText())
                .active(asBoolean(metadata.get("active")))
                .build();
    }

    private Candidate candidateFromPoint(Points.RetrievedPoint point) {
        Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
        return Candidate.builder()
                .questionId(payloadString(payload, "question_id"))
                .questionText(payloadString(payload, "question_text"))
                .intentConcept(payloadString(payload, "intent_concept"))
                .referenceContext(payloadString(payload, "reference_context"))
                .scoringKeyPoints(payloadList(payload, "scoring_key_points"))
                .scoringPitfalls(payloadList(payload, "scoring_pitfalls"))
                .followUpIds(payloadList(payload, "follow_up_ids"))
                .domainCode(payloadString(payload, "domain_code"))
                .questionType(payloadString(payload, "question_type"))
                .difficulty(payloadString(payload, "difficulty"))
                .keywords(payloadList(payload, "keywords"))
                .docContent(payloadString(payload, "doc_content"))
                .active(payloadBoolean(payload, "active"))
                .build();
    }

    private String payloadString(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null) {
            return "";
        }
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case INTEGER_VALUE -> String.valueOf(value.getIntegerValue());
            case BOOL_VALUE -> String.valueOf(value.getBoolValue());
            default -> "";
        };
    }

    private List<String> payloadList(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null || !value.hasListValue()) {
            return List.of();
        }
        return value.getListValue().getValuesList().stream()
                .map(this::payloadScalar)
                .filter(this::hasText)
                .toList();
    }

    private String payloadScalar(JsonWithInt.Value value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case INTEGER_VALUE -> String.valueOf(value.getIntegerValue());
            case BOOL_VALUE -> String.valueOf(value.getBoolValue());
            default -> "";
        };
    }

    private boolean payloadBoolean(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        return value != null && value.hasBoolValue() && value.getBoolValue();
    }

    /**
     * 解析 Dense 召回的查询文本。
     *
     * <p>查询文本优先级（按顺序尝试）：
     * <ol>
     *   <li>queryText：显式查询文本（最优先）</li>
     *   <li>focusPoint：焦点（如"Redis缓存击穿"）</li>
     * </ol>
     *
     * @param request RAG检索请求
     * @return 查询文本
     */
    private String resolveDenseQueryText(RagRetrievalRequest request) {
        if (hasText(request.getQueryText())) {
            return request.getQueryText().trim();
        }
        return hasText(request.getFocusPoint()) ? request.getFocusPoint().trim() : "";
    }

    /**
     * 解析最终结果限制数量。
     *
     * <p>从配置中获取 rag.topK 作为最终返回数量
     *
     * @return 结果限制数量
     */
    private int resolveResultLimit() {
        return Math.max(1, ragProperties.getTopK());
    }

    /**
     * 解析 Dense 召回的获取数量限制。
     *
     * <p>放大因子说明：
     * <ul>
     *   <li>有预过滤时：topK × 4（放大4倍，保证召回质量）</li>
     *   <li>无预过滤时：topK（直接使用配置值）</li>
     * </ul>
     *
     * @param widenForPrefilter 是否有预过滤
     * @return Dense 召回数量限制
     */
    private int resolveDenseFetchLimit(boolean widenForPrefilter) {
        if (!widenForPrefilter) {
            return resolveResultLimit();
        }
        return Math.max(resolveResultLimit() * LEXICAL_PREFILTER_MULTIPLIER, resolveResultLimit());
    }

    /**
     * 解析 Lexical 预过滤的数量限制。
     *
     * <p>计算公式：resultLimit × LEXICAL_PREFILTER_MULTIPLIER
     * <p>例如：topK=5 × 4 = 20 条候选
     *
     * @return Lexical 预过滤数量限制
     */
    private int resolveLexicalPrefilterLimit() {
        return Math.max(resolveResultLimit() * LEXICAL_PREFILTER_MULTIPLIER, resolveResultLimit());
    }

    private String normalizeQuestionTypeForCorpus(String questionType) {
        if (!hasText(questionType)) {
            return "";
        }
        String normalized = questionType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PROJECT_DEEP_DIVE" -> "PROJECT";
            default -> normalized;
        };
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::asString)
                    .filter(this::hasText)
                    .toList();
        }
        return List.of();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean bool ? bool : "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String firstNonBlank(String primary, String fallback) {
        return hasText(primary) ? primary.trim() : asString(fallback).trim();
    }

    private String firstLine(String text) {
        if (!hasText(text)) {
            return "";
        }
        int index = text.indexOf('\n');
        return index >= 0 ? text.substring(0, index).trim() : text.trim();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream().filter(this::hasText).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasLexicalPrefilter(List<Candidate> lexicalCandidates) {
        return lexicalCandidates != null && !lexicalCandidates.isEmpty();
    }

    private LinkedHashSet<String> allowedQuestionIds(List<Candidate> lexicalCandidates) {
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        if (lexicalCandidates == null) {
            return allowed;
        }
        for (Candidate candidate : lexicalCandidates) {
            if (hasText(candidate.questionId)) {
                allowed.add(candidate.questionId);
            }
        }
        return allowed;
    }

    private List<Candidate> dedupeByQuestionId(List<Candidate> candidates) {
        Map<String, Candidate> byQuestionId = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if (hasText(candidate.questionId)) {
                byQuestionId.putIfAbsent(candidate.questionId, candidate);
            }
        }
        return List.copyOf(byQuestionId.values());
    }

    /**
     * 解析 Lexical 预过滤的检索词。
     *
     * <p>检索词来源：
     * <ol>
     *   <li>keywordQueries：关键词查询列表（唯一来源）</li>
     * </ol>
     *
     * <p>去重说明：
     * <ul>
     *   <li>使用 LinkedHashSet 保证顺序并去重</li>
     *   <li>保留第一个出现的词</li>
     * </ul>
     *
     * @param request RAG检索请求
     * @return 去重后的检索词列表
     */
    private List<String> resolveLexicalTerms(RagRetrievalRequest request) {
        LinkedHashSet<String> lexicalTerms = new LinkedHashSet<>();
        addLexicalTerms(lexicalTerms, request.getKeywordQueries());
        return List.copyOf(lexicalTerms);
    }

    private void addLexicalTerms(LinkedHashSet<String> collector, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addLexicalTerm(collector, value);
        }
    }

    private void addLexicalTerm(LinkedHashSet<String> collector, String value) {
        if (!hasText(value)) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isBlank()) {
            collector.add(normalized);
        }
    }

    private int nullSafeRank(Integer rank) {
        return rank == null ? Integer.MAX_VALUE : rank;
    }

    @lombok.Builder
    private static class Candidate {
        private String questionId;
        private String questionText;
        private String intentConcept;
        private String referenceContext;
        @lombok.Builder.Default
        private List<String> scoringKeyPoints = List.of();
        @lombok.Builder.Default
        private List<String> scoringPitfalls = List.of();
        @lombok.Builder.Default
        private List<String> followUpIds = List.of();
        private String domainCode;
        private String questionType;
        private String difficulty;
        @lombok.Builder.Default
        private List<String> keywords = List.of();
        private String docContent;
        private boolean active;
        private Integer denseRank;
        private double finalScore;
    }
}
