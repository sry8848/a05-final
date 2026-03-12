package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.ReportGenerationInput;
import com.a05.aiinterview.ai.dto.ReportGenerationOutput;
import com.a05.aiinterview.ai.entity.AiInvocationLog;
import com.a05.aiinterview.ai.service.AiInvocationLogService;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告生成服务——异步生成面试评估报告。
 *
 * <p>触发时机：
 * <ol>
 *   <li>{@code AnswerSubmitService}检测到评估信号为 {@code END} 时自动调用</li>
 *   <li>{@code InterviewController}的{@code POST /interviews/{sessionId}/finish} 手动触发</li>
 * </ol>
 *
 * <p>核心流程：
 * <ol>
 *   <li>检查是否已存在报告（幂等），若存在则直接更新会话状态为 completed</li>
 *   <li>更新会话状态为 {@code report_generating}，防止重复生成</li>
 *   <li>查询该会话所有题目及对应回答，组装完整 Q/A 列表</li>
 *   <li>调用 AI 生成报告（真实或 Mock）</li>
 *   <li>持久化报告到 {@code interview_reports} 表</li>
 *   <li>更新会话状态为 {@code completed}</li>
 * </ol>
 *
 * <p>任何步骤失败都会在 catch 块中记录错误日志，session 状态保持为 {@code aborted}，
 * 用户可稍后重试或联系管理员排查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final AiClient aiClient;
    private final PromptProperties promptProperties;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final AiInvocationLogService aiInvocationLogService;

    /**
     * 异步生成面试报告。
     * 由 AnswerSubmitService在 signal=END 时调用，或由 finish 接口手动触发。
     *
     * @param sessionId 面试会话 ID
     */
    @Async
    public void generateAsync(Long sessionId) {
        log.info("报告生成流程开始, sessionId={}", sessionId);

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            log.error("报告生成流程找不到会话，可能已被删除, sessionId={}", sessionId);
            return;
        }

        if (interviewReportMapper.selectBySessionId(sessionId) != null) {
            log.info("报告已存在，跳过重复生成，直接更新状态, sessionId={}", sessionId);
            ensureCompleted(sessionId);
            return;
        }

        try {
            List<InterviewQuestion> questions = interviewQuestionMapper.selectList(
                    new LambdaQueryWrapper<InterviewQuestion>()
                            .eq(InterviewQuestion::getSessionId, sessionId)
                            .orderByAsc(InterviewQuestion::getQuestionNo)
            );

            Map<Long, String> answerMap = buildAnswerMap(sessionId);

            List<ReportGenerationInput.QuestionAnswerPair> pairs = buildQaPairs(questions, answerMap, session);

            ReportGenerationInput input = ReportGenerationInput.builder()
                    .positionCode(session.getTargetRole())
                    .experienceLevel(session.getExperienceLevel())
                    .mode(session.getMode())
                    .sessionTitle(session.getTitle())
                    .syllabusJson(session.getSyllabusJson())
                    .stateLedgerJson(session.getStateLedgerJson())
                    .questionAnswerPairs(pairs)
                    .build();

            log.info("调用 AI 生成报告, sessionId={}, qaCount={}", sessionId, pairs.size());

            boolean success = true;
            String errorMsg = null;
            ReportGenerationOutput output = null;
            AiCallResult<ReportGenerationOutput> reportResult = null;

            try {
                reportResult = aiClient.callReportGeneration(input);
                output = reportResult.getOutput();
            } catch (Exception e) {
                success = false;
                errorMsg = e.getMessage();
                log.error("报告生成 AI 调用失败, sessionId={}", sessionId, e);
                throw e;
            } finally {
                recordReportLog(session, success, errorMsg, reportResult);
            }

            InterviewReport report = buildReport(sessionId, output);
            interviewReportMapper.insert(report);

            // 更新会话状态为 completed
            interviewSessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                    .eq(InterviewSession::getId, sessionId)
                    .set(InterviewSession::getStatus, "completed")
                    .set(InterviewSession::getUpdatedAt, LocalDateTime.now()));

            log.info("报告生成完成, sessionId={}, overallScore={}, status=completed",
                    sessionId, report.getOverallScore());

        } catch (Exception e) {
            log.error("报告生成异常, sessionId={}", sessionId, e);
            // 调用失败时保持状态为 report_generating，或改为 aborted 并通知用户重试
            interviewSessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                    .eq(InterviewSession::getId, sessionId)
                    .set(InterviewSession::getStatus, "report_generating")
                    .set(InterviewSession::getUpdatedAt, LocalDateTime.now()));
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 构建 questionId 到 answerText 的映射表。
     * 同一题可能有多条 attempt，取 isFinal=true 的最新一条。
     */
    private Map<Long, String> buildAnswerMap(Long sessionId) {
        List<InterviewAttempt> attempts = interviewAttemptMapper.selectList(
                new LambdaQueryWrapper<InterviewAttempt>()
                        .eq(InterviewAttempt::getSessionId, sessionId)
                        .eq(InterviewAttempt::getIsFinal, true)
                        .orderByDesc(InterviewAttempt::getCreatedAt)
        );
        // 按 questionId 分组，取每组第一条（即最新），因为已按 createdAt 倒序，所以 first 即最新
        return attempts.stream()
                .collect(Collectors.toMap(
                        InterviewAttempt::getQuestionId,
                        a -> a.getAnswerText() != null ? a.getAnswerText() : "",
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    /**
     * 组装题目与回答配对列表，并从 syllabusJson 补充知识域名称。
     */
    private List<ReportGenerationInput.QuestionAnswerPair> buildQaPairs(
            List<InterviewQuestion> questions,
            Map<Long, String> answerMap,
            InterviewSession session) {

        Map<String, String> domainNameMap = new HashMap<>();
        if (session.getSyllabusJson() != null) {
            Object domainsObj = session.getSyllabusJson().get("domains");
            if (domainsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> dm) {
                        String code = (String) dm.get("domainCode");
                        String name = (String) dm.get("domainName");
                        if (code != null && name != null) domainNameMap.put(code, name);
                    }
                }
            }
        }

        return questions.stream().map(q -> {
            String domainCode = extractDomainCode(q);
            String domainName = domainNameMap.getOrDefault(domainCode, domainCode);
            return ReportGenerationInput.QuestionAnswerPair.builder()
                    .questionId(q.getId())
                    .questionNo(q.getQuestionNo())
                    .questionType(q.getQuestionType())
                    .domainCode(domainCode)
                    .domainName(domainName)
                    .targetDepth(q.getTargetDepth())
                    .stem(q.getStem())
                    .answerText(answerMap.get(q.getId()))
                    .expectedPoints(q.getExpectedPoints())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 将 AI 返回结果转换为 InterviewReport 实体。
     * skillDomainScores 单独存储为 List<Map> 以便前端 JSON 解析。
     */
    private InterviewReport buildReport(Long sessionId, ReportGenerationOutput output) {
        List<Map<String, Object>> domainScoreMaps = null;
        if (output.getSkillDomainScores() != null) {
            domainScoreMaps = output.getSkillDomainScores().stream()
                    .map(s -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("domainCode", s.getDomainCode());
                        m.put("domainName", s.getDomainName());
                        m.put("score", s.getScore());
                        m.put("achievedDepth", s.getAchievedDepth());
                        m.put("commentary", s.getCommentary());
                        return m;
                    })
                    .collect(Collectors.toList());
        }

        InterviewReport report = new InterviewReport();
        report.setSessionId(sessionId);
        report.setOverallScore(output.getOverallScore());
        report.setSummary(output.getSummary());
        report.setStrengths(output.getStrengths());
        report.setWeaknesses(output.getWeaknesses());
        report.setImprovementSuggestions(output.getImprovementSuggestions());
        report.setSkillDomainScores(domainScoreMaps);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }

    /**
     * 从题目的 generationContextJson 中取 domainCode，兜底返回 "intro"。
     */
    private String extractDomainCode(InterviewQuestion q) {
        if (q.getGenerationContextJson() == null) return "intro";
        Object code = q.getGenerationContextJson().get("domainCode");
        return (code instanceof String s && !s.isBlank()) ? s : "intro";
    }

    /**
     * 记录报告生成 AI 调用审计日志（含 Token 计数）。
     */
    private void recordReportLog(InterviewSession session, boolean success, String errorMsg,
                                  AiCallResult<ReportGenerationOutput> result) {
        AiInvocationLog logEntry = AiInvocationLog.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .promptCode("report_generation")
                .promptVersion(promptProperties.resolveVersion("report_generation"))
                .modelProvider(session.getModelProvider() != null ? session.getModelProvider() : "unknown")
                .modelName(session.getModelName() != null ? session.getModelName() : "")
                .requestTokens(result != null ? result.getPromptTokens() : 0)
                .responseTokens(result != null ? result.getResponseTokens() : 0)
                .latencyMs(result != null ? (int) result.getLatencyMs() : 0)
                .success(success)
                .errorMessage(errorMsg)
                .createdAt(LocalDateTime.now())
                .build();
        aiInvocationLogService.saveAsync(logEntry);
    }

    /** 确保会话状态为 completed（若已存在报告则补充更新状态）。 */
    private void ensureCompleted(Long sessionId) {
        interviewSessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                .eq(InterviewSession::getId, sessionId)
                .ne(InterviewSession::getStatus, "completed")
                .set(InterviewSession::getStatus, "completed")
                .set(InterviewSession::getUpdatedAt, LocalDateTime.now()));
    }
}
