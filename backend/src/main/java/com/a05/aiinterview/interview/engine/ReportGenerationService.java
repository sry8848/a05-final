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
 * 閹躲儱鎲￠悽鐔稿灇閺堝秴濮熼敍鍫濈磽濮濄儲澧界悰宀嬬礆閵? *
 * <p>閻㈠彉浜掓稉瀣╄⒈娑擃亜鍙嗛崣锝埿曢崣鎴窗
 * <ol>
 *   <li>{@code AnswerSubmitService}閿涙俺鐦庢导鏉垮枀缁涙牔淇婇崣铚傝礋 {@code END} 閺冩儼鍤滈崝銊ㄐ曢崣?/li>
 *   <li>{@code InterviewController}閿涙@code POST /interviews/{sessionId}/finish} 閹靛濮╃憴锕€褰?/li>
 * </ol>
 *
 * <p>閹笛嗩攽濞翠胶鈻奸敍? * <ol>
 *   <li>楠炲倻鐡戝Λ鈧弻銉⑩偓鏂衡偓鏃囧閹躲儱鎲″鎻掔摠閸︺劌鍨捄瀹犵箖閿涘矂浼╅崗宥夊櫢婢跺秶鏁撻幋?/li>
 *   <li>鐏忓棔绱扮拠婵堝Ц閹胶鐤嗘稉?{@code report_generating}閿涘牆鍑￠悽杈殶閻劍鏌熺拋鍓х枂閿涘本顒濇径鍕晙濞嗭紕鈥樻穱婵撶礆</li>
 *   <li>閸旂姾娴囬崗銊╁劥妫版娲伴崪灞芥礀缁涙棑绱濋弸鍕紦 Q/A 闁板秴顕崚妤勩€?/li>
 *   <li>鐠嬪啰鏁?AI 閻㈢喐鍨氶幎銉ユ啞閿涘牆缍嬮崜宥勮礋 Mock閿涘瞼绮ㄩ弸鍕暚閺佽揪绱?/li>
 *   <li>閹镐椒绠欓崠鏍ㄥГ閸涘﹤鍩?{@code interview_reports} 鐞?/li>
 *   <li>鐏忓棔绱扮拠婵堝Ц閹胶鐤嗘稉?{@code completed}</li>
 * </ol>
 *
 * <p>娴犺缍嶅銉╊€冨鍌氱埗閸?catch 閸氬氦顔囪ぐ鏇熸）韫囨鑻熼弴瀛樻煀 session 閻樿埖鈧椒璐?{@code aborted}閿? * 娑撳秴鎮滄径鏍ㄥ閸戠尨绱欐穱婵囧瘮瀵倹顒炵痪璺ㄢ柤鐎瑰鍙忛敍澶堚偓? */
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
     * 瀵倹顒為悽鐔稿灇闂堛垼鐦幎銉ユ啞閵?     * 閻?AnswerSubmitService閿涘澃ignal=END閿涘鍨ㄩ幍瀣З finish 閹恒儱褰涚憴锕€褰傞妴?     *
     * @param sessionId 闂堛垼鐦导姘崇樈 ID
     */
    @Async
    public void generateAsync(Long sessionId) {
        log.info("閹躲儱鎲￠悽鐔稿灇娴犺濮熷鈧慨? sessionId={}", sessionId);

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            log.error("閹躲儱鎲￠悽鐔稿灇婢惰精瑙﹂敍姘娑撳秴鍩屾导姘崇樈, sessionId={}", sessionId);
            return;
        }

        if (interviewReportMapper.selectBySessionId(sessionId) != null) {
            log.info("閹躲儱鎲″鎻掔摠閸︻煉绱濈捄瀹犵箖闁插秴顦查悽鐔稿灇, sessionId={}", sessionId);
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

            log.info("鐠嬪啰鏁ら幎銉ユ啞閻㈢喐鍨?AI, sessionId={}, qaCount={}", sessionId, pairs.size());

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
                log.error("閹躲儱鎲￠悽鐔稿灇 AI 鐠嬪啰鏁ゆ径杈Е, sessionId={}", sessionId, e);
                throw e;
            } finally {
                recordReportLog(session, success, errorMsg, reportResult);
            }

                InterviewReport report = buildReport(sessionId, output);
            interviewReportMapper.insert(report);

            // 閺囧瓨鏌婃导姘崇樈閻樿埖鈧椒璐?completed
            interviewSessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                    .eq(InterviewSession::getId, sessionId)
                    .set(InterviewSession::getStatus, "completed")
                    .set(InterviewSession::getUpdatedAt, LocalDateTime.now()));

            log.info("閹躲儱鎲￠悽鐔稿灇鐎瑰本鍨? sessionId={}, overallScore={}, status=completed",
                    sessionId, report.getOverallScore());

        } catch (Exception e) {
            log.error("閹躲儱鎲￠悽鐔稿灇瀵倸鐖? sessionId={}", sessionId, e);
            // 閻㈢喐鍨氭径杈Е閺冭泛娲栭柅鈧崚?report_generating閿涘奔绗夌拋鍙ヨ礋 aborted閿涘牆鍘戠拋鍛婂閸斻劑鍣哥拠鏇礆
            interviewSessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                    .eq(InterviewSession::getId, sessionId)
                    .set(InterviewSession::getStatus, "report_generating")
                    .set(InterviewSession::getUpdatedAt, LocalDateTime.now()));
        }
    }

    // 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓
    // 缁変焦婀侀弬瑙勭《
    // 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    /**
     * 閺嬪嫬缂?questionId 閳?answerText 閻ㄥ嫬娲栫粵鏃傚偍瀵洏鈧?     * 閸氬奔绔存０妯垮閺堝顦块弶?attempt閿涘苯褰?isFinal=true 閻ㄥ嫭娓堕弬棰佺閺壜扳偓?     */
    private Map<Long, String> buildAnswerMap(Long sessionId) {
        List<InterviewAttempt> attempts = interviewAttemptMapper.selectList(
                new LambdaQueryWrapper<InterviewAttempt>()
                        .eq(InterviewAttempt::getSessionId, sessionId)
                        .eq(InterviewAttempt::getIsFinal, true)
                        .orderByDesc(InterviewAttempt::getCreatedAt)
        );
        // 閸氬奔绔?questionId 娣囨繄鏆€閺堚偓閺傞绔撮弶鈽呯礄閻㈠彉绨鍙夊瘻 createdAt 閸婃帒绨敍瀹紀Map 娣囨繄鏆€ first 閸楄櫕娓堕弬甯礆
        return attempts.stream()
                .collect(Collectors.toMap(
                        InterviewAttempt::getQuestionId,
                        a -> a.getAnswerText() != null ? a.getAnswerText() : "",
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    /**
     * 鐏忓棝顣介惄顔煎灙鐞涖劌鎷伴崶鐐电摕缁便垹绱╅柊宥咁嚠閿涘苯鎮撻弮鏈电矤 syllabusJson 娑擃叀藟閸忋劎鐓＄拠鍡楃厵娑擃厽鏋冮崥宥冣偓?     */
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
     * 鐏?AI 鏉堟挸鍤潪顑胯礋 InterviewReport 鐎圭偘缍嬮妴?     * skillDomainScores 鎼村繐鍨崠鏍﹁礋 List<Map> 鐎涙ê鍋嶉敍灞肩┒娴?JSON 閻╁瓨甯撮幐浣风畽閸栨牓鈧?     */
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
     * 娴犲酣顣介惄顔炬畱 generationContextJson 娑擃厽褰侀崣?domainCode閿涘牆鍘规惔?"intro"閿涘鈧?     */
    private String extractDomainCode(InterviewQuestion q) {
        if (q.getGenerationContextJson() == null) return "intro";
        Object code = q.getGenerationContextJson().get("domainCode");
        return (code instanceof String s && !s.isBlank()) ? s : "intro";
    }

    /**
     * 鐠佹澘缍嶉幎銉ユ啞閻㈢喐鍨?AI 鐠嬪啰鏁ょ€孤ゎ吀閺冦儱绻旈敍鍫濇儓 Token 鐠佲剝鏆熼敍澶堚偓?     */
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

    /** 绾喕绻氭导姘崇樈閻樿埖鈧椒璐?completed閿涘牆绠撶粵澶婃簚閺咁垰鍘规惔鏇＄殶閻㈩煉绱氶妴?*/
    private void ensureCompleted(Long sessionId) {
        interviewSessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                .eq(InterviewSession::getId, sessionId)
                .ne(InterviewSession::getStatus, "completed")
                .set(InterviewSession::getStatus, "completed")
                .set(InterviewSession::getUpdatedAt, LocalDateTime.now()));
    }
}
