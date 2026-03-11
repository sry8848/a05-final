package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.*;
import com.a05.aiinterview.ai.entity.AiInvocationLog;
import com.a05.aiinterview.ai.service.AiInvocationLogService;
import com.a05.aiinterview.common.enums.DomainStatus;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 闂佹悶鍎抽崑鐔兼偤閻旂厧绠甸柟閭︿簼閸炲鈽夐幘鍓佸笡婵犫偓閸ヮ剙绀夐柍閿亾闁逞屽墯閺岋繝鍩€椤掍焦顥堥柍褜鍓氶崝娆撱€傞挊澶樺殫妞ゅ繐妫楃粻姘舵煛瀹ュ浂鐒剧紒娲畺瀹曟濡烽妷銉х▔闂? *
 * <p>闂佸湱顭堥ˇ閬嶆偤閵夆晜鍋╅柕澶涢檮閻庮喗淇婂Δ瀣閻庨潧鐭傞弻褔骞戦幇顔惧敶婵犮垼娉涚€氼噣骞冩繝鍌傛帟绠涙惔锝庝紘濠电偛妫岄埀顒佹灱閸嬫挸鈻庨幘顖氫壕濠㈣泛顦伴惇浠嬫煙缂佹ê濮冩慨姗堢畵閺佸秹宕? 闂佸憡顨呭ú銊︻殽閸ヮ剙瑙﹂幖绮光偓鍐茬稇 7 濠殿喗绺块崕鑽ゆ濮樿埖鏅? * <ol>
 *   <li>濡ょ姷鍋涢崐濠氭偤閹达箑鍐€闁跨喓濮峰畷?attempt_id闂佹寧绋戦悧鍡涘箖?ID 闂佺儵鏅涢悺銊ф暜鐎涙ɑ浜ら柡鍌涘缁€鈧梺鍛娒Λ妤勩亹閸撲胶纾奸柟鎯ь嚟娴滎垶鏌?/li>
 *   <li>闁荤姴娲╅褑銇愰崶銊ヮ嚤婵ê纾Ο鍫ユ煏閸℃洘顦烽悗闈涚焸閹虫捇宕橀鍡楃墯闂侀潧妫斿ù鍥ㄥ緞閸曨垰瀚夋い鎴︽暜閸嬫挻鎷呯粙鍨綉闂佸憡鐟﹂悡锟犮€傛禒瀣剮?/li>
 *   <li>缂傚倷绀佺€氼垶藟婵犲啰鈻斿┑鐘辫兌閻熸捇鏌￠崒姘カ缂佽鲸鐟﹀濠氬箣閿旂粯姣岄梺绋跨箞閸旀垿宕?Q/A闂佹寧绋戦張顒€煤閸ф绫嶉柍琛″亾缂侇喖瀛╅敍鎰熼崗鑲╃崶闂?/li>
 *   <li>闁荤姴顑呴崯浼村极閵堝洦瀚氶柛鏇ㄤ簻瀵兘鏌涢幇顒傦紞闁?AI</li>
 *   <li>闁圭厧鐡ㄥ濠氬极閵堝洦瀚婚柨鏃囧Г閹?Patch闂佹寧绋戦悧鍡涘箖閹炬潙顕辨慨妯虹－濡牓鎮跺☉妯肩劯闁衡偓閿濆鏅悘鐐跺亹缁犱粙鎮归崶锔筋樂閻熸洖妫涢幃鏉跨暆鐎ｎ剛顦?/li>
 *   <li>闂?signal=END 闂佸憡甯楅悷銊杺闂佸憡鐟﹂崹鑸垫叏閵堝宸濆┑鐘插閺呮悂鏌熺€涙ê濮х紒閬嶄憾瀹曘儵鏁冮埀顒勫垂椤栨稓鈹嶆繝闈涙閹界娀鎮楅悷鐗堟拱闁哄棴绲剧粙澶屸偓锝傛櫇椤忓崬螞閻楀牜鐒介柣掳鍎甸幃楣冨Ψ閵娿儲顔?evaluationJson</li>
 *   <li>闁哄鏅滈弻銊ッ?streamAttemptId闂佹寧绋戦懟顖涙櫠閻樼數鍗氭い鏍ㄨ壘濞堣埖鎱ㄥ┑鍕偓娑㈡儍閻斿吋鍋?SSE 缂備焦妫忛崹鎶藉磻閿濆鍤旂€瑰嫭婢樼徊鍧楁倵閸︻厼浠︽俊鐐插€归敍鎰熼崗鑲╃崶濠?/li>
 * </ol>
 *
 * <p>M2 闂佸憡鐟﹁摫婵炶尪娉曢幏鐘碘偓娑櫳戦～鏍煥濞戞瑧鐓紒妤€鎳忕粙澶愬焵椤掍緤绱ｆ俊顖氭贡閻熸繈鏌涢幇顒傂㈡繝鈧鍕垫桨闁靛牆鎳忛崐杈ㄦ叏濠靛嫬鍔ら柡浣规崌楠炲骞囬崜浣侯槷闂佸吋婢橀張顒€危閹间焦鍋?{@code QuestionStreamService} 闂?SSE 濠电偟绻濆鎺楁嚈閹寸姭鍋撻崷顓炰沪婵＄偛鍊块幃浠嬫偄缁嬭法浜ｉ梺瑙ｅ亾闁芥ê顦卞銊╂煏? */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerSubmitService {

    private final AiClient aiClient;
    private final PromptProperties promptProperties;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final StateLedgerPatchService stateLedgerPatchService;
    private final AiInvocationLogService aiInvocationLogService;
    private final ReportGenerationService reportGenerationService;

    /**
     * 闂佸湱绮崝鎺戭潩閿曞倸纾规繛鍡樻尨閸嬫挻寰勬径瀣簞闂佹悶鍎抽崑鐔兼偤閻斿吋鏅悘鐐垫櫕閺嗘岸鏌℃担鍝ユ憼濠⒀呮櫕閹?8 濠殿喗绺块崕鎵偓闈涚焸閺屟囧箲閹邦喚鍞撮梺?     *
     * @param sessionId 闂傚倸鐗勯崹濂告儊椤栨稑顕辨慨妯虹－濡?ID
     * @param userId    閻熸粎澧楅幐鍛婃櫠閻樼粯鍎岄悹鍥皺缁夊潡鏌ｉ～顒€濡介柛?ID闂佹寧绋戦悧蹇涘极閵堝棛顩查幖杈剧磿缁夋椽鎮橀悙闈涗沪闁绘瀛╅〃銉ョ暆鐎ｎ剛顦?
     * @param request   闂佸湱绮崝鎺戭潩閿旂偓瀚氶梺鍨儑濠€鎾煥濞戞ɑ濞噓estionId闂侀潧妫斿顧簍emptId闂侀潧妫斿顧磗werText闂?     * @return 婵炴垶鎸搁鍕博鐎涙﹫绱ｆ俊顖炩偓娑氱畾闂佽鍙庨崹浼村箯閹峰被浜归柟鎯у暱椤ゅ懎霉閸忛棿浜㈤柣锔跨矙閹晠鎳滅喊妯轰壕?     */
    public SubmitAttemptResponse submitAnswer(Long sessionId, Long userId, SubmitAttemptRequest request) {
        log.info("闂佸湱绮崝鎺戭潩閿曞倸鐐婇柣鎰暩閹芥洖鈽夐幘鎰佺吋闁瑰€熷亹閹瑰嫰顢涘杈╋紱婵? sessionId={}, questionId={}, attemptId={}",
                sessionId, request.getQuestionId(), request.getAttemptId());

        // 闂佸啿鍘滈崑鎾绘煃閸忓浜?Step 1闂佹寧绋掗懝鍓х不閹惧墎椹冲璺侯儑婢р€澄?闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜?
        InterviewAttempt existing = interviewAttemptMapper.selectByAttemptId(request.getAttemptId());
        if (existing != null) {
            log.info("濡ょ姷鍋涢崐濠氭偤閹达箑宸濋柟瀛樺笩閸橆剟鏌ㄥ☉妯肩伇婵炴彃娼￠獮鎺楀Ψ閵娧咁唹闂佹悶鍎抽崑娑橆啅婵犳艾鐭楅柤纰卞墰濞夈垽鏌? attemptId={}", request.getAttemptId());
            return buildIdempotentResponse(existing);
        }

        // 闂佸啿鍘滈崑鎾绘煃閸忓浜?Step 2闂佹寧绋掓穱娲敋娴兼潙鐭楅柡宥忓缁愭鎮归崶銊х細闁逞屽厸濡炴帞鈧潧鐭傞幊鎾诲礃椤忓棗鐗氶梺闈涙濞村洦寰勯崟顖氬珘妞ゆ垿鏁崑鎾存媴缁嬪灝娼戦梺鍛婄懄閻擄繝銆傛禒瀣剮?闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        validateSession(session, userId, sessionId);

        InterviewQuestion currentQuestion = interviewQuestionMapper.selectById(request.getQuestionId());
        validateQuestion(currentQuestion, sessionId, request.getQuestionId());

        List<InterviewQuestion> allQuestions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getQuestionNo)
        );

        // 闂佸憡姊绘慨鎯归崶顒€鍌ㄩ柛鈩冾殔閽戝鏌涢妷褍浠ч柣锛勫枛閺佸秹宕奸姀鈩冩婵炲瓨绮屽ù椋庡垝瀹ュ洦鍟?Q/A 婵炴垶鎸搁敃锝囩箔閸涙潙妫橀柛銉╊棑缁€?        List<InterviewAttempt> allAttempts = interviewAttemptMapper.selectList(
                new LambdaQueryWrapper<InterviewAttempt>()
                        .eq(InterviewAttempt::getSessionId, sessionId)
        );

        // 闂佸啿鍘滈崑鎾绘煃閸忓浜?Step 3闂佹寧绋掑銊у垝瀹ュ洦鍟戦柛娑卞墰閻熸劕鈽夐幘鎰佸剱闁?闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕
        boolean forceEndByMaxQuestions = shouldForceEndByMaxQuestions(session);
        EvaluationDecisionOutput evalOutput;

        if (forceEndByMaxQuestions) {
            evalOutput = buildForcedEndDecision(currentQuestion, session);
            log.info("???????????????, sessionId={}, currentQuestionNo={}, maxQuestions={}",
                    sessionId, session.getCurrentQuestionNo(), extractMaxQuestions(session.getStateLedgerJson()));
        } else {
            List<EvaluationDecisionInput.QaContext> recentContext =
                    buildRecentContext(allQuestions, allAttempts, session.getContextWindowSize());

            EvaluationDecisionInput evalInput = EvaluationDecisionInput.builder()
                    .positionCode(session.getTargetRole())
                    .experienceLevel(session.getExperienceLevel())
                    .mode(session.getMode())
                    .currentQuestionId(currentQuestion.getId())
                    .currentQuestionType(currentQuestion.getQuestionType())
                    .currentDomainCode(resolveDomainCode(currentQuestion, session))
                    .currentDomainName(resolveDomainName(currentQuestion, session))
                    .currentDomainId(currentQuestion.getDomainId())
                    .currentTargetDepth(currentQuestion.getTargetDepth())
                    .currentQuestionStem(currentQuestion.getStem())
                    .expectedPoints(currentQuestion.getExpectedPoints())
                    .answerText(request.getAnswerText())
                    .pauseStats(request.getPauseStats())
                    .stateLedger(session.getStateLedgerJson())
                    .syllabusJson(session.getSyllabusJson())
                    .recentContext(recentContext)
                    .build();

            boolean evalSuccess = true;
            String evalError = null;
            AiCallResult<EvaluationDecisionOutput> evalResult = null;

            try {
                evalResult = aiClient.callEvaluationDecision(evalInput);
                evalOutput = evalResult.getOutput();
            } catch (Exception e) {
                evalSuccess = false;
                evalError = e.getMessage();
                log.error("???? AI ????, sessionId={}, attemptId={}", sessionId, request.getAttemptId(), e);
                throw new RuntimeException("????????????", e);
            } finally {
                recordEvalLog(session, currentQuestion, evalSuccess, evalError, evalResult);
            }
        }

        InterviewAttempt attempt = saveAttempt(sessionId, currentQuestion.getId(), request, evalOutput);

        // 闂佸啿鍘滈崑鎾绘煃閸忓浜?Step 5b闂佹寧绋掗懝鍓ц姳閺屻儲鍋ㄩ柕濞垮妼椤︹晠鏌?Patch闂佹寧绋戦悧鎰般€侀幋锔界叆濞达絿鏌夐々顐︽偠濞戞鐒跨紒?闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑?        if (evalOutput.getPatch() != null) {
            evalOutput.getPatch().setEvidenceQuestionId(currentQuestion.getId());
        }
        stateLedgerPatchService.applyPatch(sessionId, evalOutput.getPatch(), request.getAttemptId(), attempt.getId());

        // 闂佸搫娲ら悺銊╁蓟婵犲懌浜归柟鎯у暱椤ゅ懎螞閻楀牜鐒芥繛韫嵆閹晠鎳滅喊妯轰壕濞达絿鏌夌粈?answered
        markQuestionAnswered(currentQuestion);

        // 闂佸啿鍘滈崑鎾绘煃閸忓浜?Step 6闂佹寧绋掗鐠眊nal=END 闂佸憡甯楅悷褏鍒掗妸鈺佺骇闁绘柨鍚嬪銊╂偣閸ャ劍绌跨紒閬嶄憾瀹曘儵鏁冮埀顒勫垂椤栨稓顩烽柛娑欏缁犳煡鏌涢妷褍浠︾紒渚婄畱椤曪綀绠涢弮鍌氱伇闁荤姴娲ゅΛ娑㈩敄娓氣偓閺佸秶浠﹂懖鈺冩啴婵炴垶鎸撮崑鎾澄涢悧鍫劷闁?SSE 闁诲骸婀遍崑鐐差渻閸岀偞鍋ㄩ柣鏃傤焾閻?闂佸啿鍘滈崑鎾绘煃閸忓浜?
        if ("END".equals(evalOutput.getSignal())) {
            log.info("闁荤姴娲ょ€氼亪宕ｆ繝鍥х鐎规洖娲ㄩ幗婊兦庨崶锝傚亾閸愭彃鈻?END闂佹寧绋戦惌浣筋杺闂佸憡鐟﹂崹鍦垝閵娾晛绾ч柣鏂垮悑濡椼劑鎮归崶銊︾闁艰崵鍠庨锝夊磼濞戝崬濡遍梺姹囧灮閸犳劙宕瑰璺虹闁靛鍎查崯? sessionId={}", sessionId);
            markSessionFinishing(sessionId);
            reportGenerationService.generateAsync(sessionId);
            return SubmitAttemptResponse.builder()
                    .attemptId(request.getAttemptId())
                    .evaluationSignal("END")
                    .streamAttemptId(null)
                    .sessionStatus("report_generating")
                    .build();
        }

        // 闂佸啿鍘滈崑鎾绘煃閸忓浜?Step 7闂佹寧绋掓穱铏规崲閹达箑鐐婇柣鎰嚟閵堬妇鈧鍠栫换鎰板吹椤撶噦绱ｆ俊顖濆吹閸ㄥジ鎮归崶褎顥犳い鎴滅窔閺佸秹宕煎鍛啴婵炴垶鎸撮崑鎾澄涢悧鍫€块柍褜鍓氭穱铏规崲?SSE QuestionStreamService 闁诲骸婀遍崑鐐差渻閸岀偞鍋ㄩ柣鏃傤焾閻忓洭鏌?闂佸啿鍘滈崑鎾绘煃閸忓浜?
        log.info("闂佸湱绮崝鎺戭潩閿曞倸鐐婇柣鎰暩閹芥洖鈽夐幘鎰佺吋闁瑰€熷亹閹瑰嫰顢涘杈ㄦ闂? sessionId={}, signal={}, 婵炴垶鎸搁鍕博鐎涙﹫绱ｆ俊顖濇濞堟椽姊洪锝勪孩缂?SSE 濠电偟绻濈粈浣烘閿熺姵鍋ㄩ柣鏃傤焾閻? attemptId={}",
                sessionId, evalOutput.getSignal(), request.getAttemptId());

        return SubmitAttemptResponse.builder()
                .attemptId(request.getAttemptId())
                .evaluationSignal(evalOutput.getSignal())
                .streamAttemptId(request.getAttemptId())
                .sessionStatus("in_progress")
                .build();
    }

    // 闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕
    // 缂備礁顦悞锕€锕㈡笟鈧顒傛喆閸曨厹鈧?    // 闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕闂佸啿鍘滈崑鎾绘煃閸忓浜鹃梺鍐插帨閸嬫捇鏌嶉崗澶婁壕



    private boolean shouldForceEndByMaxQuestions(InterviewSession session) {
        int maxQuestions = extractMaxQuestions(session.getStateLedgerJson());
        if (maxQuestions <= 0) {
            return false;
        }
        int currentNo = session.getCurrentQuestionNo() != null ? session.getCurrentQuestionNo() : 0;
        return currentNo >= maxQuestions;
    }

    private int extractMaxQuestions(Map<String, Object> ledger) {
        if (ledger == null) {
            return 0;
        }
        Object maxObj = ledger.get("max_questions");
        if (maxObj instanceof Number n) {
            return n.intValue();
        }
        if (maxObj != null) {
            try {
                return Integer.parseInt(String.valueOf(maxObj));
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private EvaluationDecisionOutput buildForcedEndDecision(InterviewQuestion question, InterviewSession session) {
        String domainCode = resolveDomainCode(question, session);
        EvaluationDecisionOutput.LedgerPatch patch = EvaluationDecisionOutput.LedgerPatch.builder()
                .domainCode(domainCode)
                .domainId(question.getDomainId())
                .currentDepth(question.getTargetDepth())
                .domainStatus(DomainStatus.COVERED)
                .saturated(true)
                .questionType(question.getQuestionType())
                .build();

        return EvaluationDecisionOutput.builder()
                .domainCode(domainCode)
                .depthReached(question.getTargetDepth())
                .saturated(true)
                .signal("END")
                .patch(patch)
                .nextStrategy(null)
                .reasoning("Reached max_questions limit")
                .build();
    }

    private void validateSession(InterviewSession session, Long userId, Long sessionId) {
        if (session == null) {
            throw new IllegalArgumentException("闂傚倸鐗勯崹濂告儊椤栨稑顕辨慨妯虹－濡牆鈽夐幘宕囆㈤柣掳鍔戝畷? sessionId=" + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("No permission to access this interview session");
        }
        if (!"in_progress".equals(session.getStatus())) {
            throw new IllegalArgumentException("闂傚倸鐗勯崹濂告儊椤栨稑顕辨慨妯虹－濡牓鏌ｅΟ鍨厫闁逞屽厸缁€浣烘閹捐埖鏆滈梻鍫氭櫇缁€澶屾喐閻楀牊灏褏濞€閹晠鎳滅喊妯轰壕? " + session.getStatus());
        }
    }

    private void validateQuestion(InterviewQuestion question, Long sessionId, Long questionId) {
        if (question == null) {
            throw new IllegalArgumentException("婵☆偆澧楅…鍥洪悧鍫⑩枖鐎广儱鎳愰幗鐘绘煕? questionId=" + questionId);
        }
        if (!question.getSessionId().equals(sessionId)) {
            throw new IllegalArgumentException("婵☆偆澧楅…鍥洪悧鍫⑩枖鐎广儱鎳愬锝吤瑰鍐╁攭妞ゆ洘纰嶇€电厧顫濆畷鍥ㄢ枔");
        }
    }

    /**
     * 缂傚倷绀佺€氼垶藟婵犲洤瀚夐柍褜鍓氬?N 婵☆偆澧楅…鍥р枔?Q/A 婵炴垶鎸搁敃锝囩箔閸涙潙妫橀柛銉ｅ劗閸?     * 闁?windowSize 婵☆偆澧楄摫闁诡垽绱曢弫顕€鏁冮埀顒勬偩椤掑嫬鏋侀柡澶嬪灦缁€鈧紓浣圭⊕濡垹妲愬┑瀣嵆閻庢稒蓱閿涘秹鏌ｉ妸銉ヮ伃妞わ絼绮欓幆鍕敊閻撳孩閿柣銏╁灲缁犳捇銆傛禒瀣祦閻犲泧鍛槱answer 婵?null闂佹寧绋戦ˇ顓㈠焵?     * 闁哄鏅滈悷锕€危閸濄儳椹抽柡宥庡亝濞堬綁鏌￠崒姘闁?闂佹悶鍎遍幖顐︽偩閻愵剛鈻斿┑鐘辫兌閻熸捇鏌￠崒姘窛闁绘稏鍎靛畷?闂佹眹鍔岀€氼厼銆掗崜浣轰笉闁逞屽墰閳ь剙婀遍崑鐔肩嵁閸ヮ剚鏅€光偓閸曨儷鈺傛叏?token 闂佺粯鐗曞Λ娑㈠磻閵忋倖鍎嶉柛鏇ㄥ亝閸婇亶鏌￠崘鈺傛瀯缂佺粯宀搁幃鎯р枎閹烘梻顔呴梺鎼炲劤婵敻寮查妷锔藉劅闁哄倻鍎甸崑?     */
    private List<EvaluationDecisionInput.QaContext> buildRecentContext(
            List<InterviewQuestion> questions,
            List<InterviewAttempt> attempts,
            Integer windowSize) {

        if (questions.isEmpty()) return List.of();

        int window = windowSize != null ? windowSize : 5;

        Map<Long, String> answerMap = attempts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsFinal()) && a.getAnswerText() != null)
                .collect(Collectors.toMap(
                        InterviewAttempt::getQuestionId,
                        InterviewAttempt::getAnswerText,
                        (a, b) -> b));

        int total = questions.size();
        int cutoff = total - window;
        List<EvaluationDecisionInput.QaContext> ctx = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            InterviewQuestion q = questions.get(i);
                String answer = (i >= cutoff) ? answerMap.get(q.getId()) : null;
            ctx.add(EvaluationDecisionInput.QaContext.builder()
                    .stem(q.getStem())
                    .answer(answer)
                    .questionType(q.getQuestionType())
                    .domainCode(resolveDomainCodeFromGenCtx(q))
                    .build());
        }
        return ctx;
    }

    /**
     * 婵烇絽娲︾换鍌炴偤?attempt 闁荤姳鐒﹀妯肩礊瀹ュ鏅柛顐ｇ箖閸庢捇鎮归崶褍顏ч柛娆忥功缁辨帡骞樼€甸晲鍑介棅顐㈡祩閸嬪棝宕ｆ惔銊︽櫖濠㈣泛鐗冮崑?     */
    private InterviewAttempt saveAttempt(Long sessionId, Long questionId,
                                         SubmitAttemptRequest request,
                                         EvaluationDecisionOutput evalOutput) {
        Map<String, Object> evalSnapshot = new LinkedHashMap<>();
        evalSnapshot.put("signal", evalOutput.getSignal());
        evalSnapshot.put("depthReached", evalOutput.getDepthReached());
        evalSnapshot.put("saturated", evalOutput.isSaturated());
        evalSnapshot.put("reasoning", evalOutput.getReasoning());
        if (evalOutput.getNextStrategy() != null) {
            EvaluationDecisionOutput.NextQuestionStrategy strat = evalOutput.getNextStrategy();
            Map<String, Object> ns = new LinkedHashMap<>();
            ns.put("nextDomainId", strat.getNextDomainId());
            ns.put("nextDomainCode", strat.getNextDomainCode());
            ns.put("nextDomainName", strat.getNextDomainName());
            ns.put("questionType", strat.getQuestionType());
            ns.put("targetDepth", strat.getTargetDepth());
            ns.put("focusPoint", strat.getFocusPoint());
            evalSnapshot.put("nextStrategy", ns);
        }

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setSessionId(sessionId);
        attempt.setQuestionId(questionId);
        attempt.setAttemptId(request.getAttemptId());
        attempt.setAnswerText(request.getAnswerText());
        attempt.setIsFinal(Boolean.TRUE.equals(request.getIsFinal()));
        attempt.setEvaluationJson(evalSnapshot);
        attempt.setCreatedAt(LocalDateTime.now());
        interviewAttemptMapper.insert(attempt);

        log.info("attempt 闂佽В鍋撻柦妯侯槺濮樸劑鎮楅悷鐗堟拱闁? attemptId={}, attemptPK={}", request.getAttemptId(), attempt.getId());
        return attempt;
    }

    /**
     * 闁诲繐绻愬Λ妤呭礄閿涘嫧鍋撳☉娅亜锕㈤鍫熷剭?attempt 闁哄鍎愰崜娆戔偓闈涘级缁嬪宕崟顓犳殼缂備焦绋戦ˇ宕囨崲閹达箑鐐婇柣鎴濇川缁€鍕?evaluationJson 闂婎偄娴傞崑鍡涘矗鎼淬垻鈻旀い鎿勭磿缁犵兘鏌涘Ο鐓庢瀻闁瑰ジ鏀遍幆鏃堝籍閹惧墎顦梺?     * M2 闂佸憡顨呭ú銊︻殽閸ヮ剙瑙﹂幖鎼灣缁愭鈽夐幘宕囆㈤柛鐔插亾闂佸搫琚崕鍙夌珶?nextQuestion闂佹寧绋戦惌渚€鍩€椤掆偓閺堫剙危閸涘﹥浜ら柡鍌涘缁€鈧?streamAttemptId闂?     * 闂佸憡鎸哥粔鍫曨敂椤掍焦濯撮悹鎭掑妽閺嗗繘鎮?ID 闂備焦褰冪粔鐢稿蓟婵犲洤绠ラ柟鎯х－绾?SSE闂佹寧绋戦顪籩stionStreamService 婵炴潙鍚嬮惌顔惧垝?Redis 缂傚倸鍊归幐鎼佹偤閵婏妇鈻旀い鎾卞灩濞呫垽鏌￠埀顒勫箚瑜嶉崵鎺楁煟閵忋垹鏋戦柛銊﹀哺瀹曟﹢宕ㄩ褍鏅ｉ梺?     */
    private SubmitAttemptResponse buildIdempotentResponse(InterviewAttempt existing) {
        String signal = "NEXT_DOMAIN";
        if (existing.getEvaluationJson() != null) {
            Object s = existing.getEvaluationJson().get("signal");
            if (s instanceof String str) signal = str;
        }

        boolean isEnd = "END".equals(signal);
        return SubmitAttemptResponse.builder()
                .attemptId(existing.getAttemptId())
                .evaluationSignal(signal)
                .streamAttemptId(isEnd ? null : existing.getAttemptId())
                .sessionStatus(isEnd ? "report_generating" : "in_progress")
                .build();
    }

    private void markQuestionAnswered(InterviewQuestion question) {
        InterviewQuestion update = new InterviewQuestion();
        update.setId(question.getId());
        update.setStatus("answered");
        update.setUpdatedAt(LocalDateTime.now());
        interviewQuestionMapper.updateById(update);
    }

    private void markSessionFinishing(Long sessionId) {
        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStatus("report_generating");
        update.setFinishedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);
    }

    /**
     * 闁荤姳鐒﹀妯肩礊瀹ュ洦瀚氶柛鏇ㄤ簻瀵兘鏌涢幇顒傦紞闁?AI 闁荤姴顑呴崯浼村极閵堝洠鍋撶€涖們鍊ら崥鈧梺鍝勫暔閸庤京鎹㈤弮鍫熸櫖闁割偅绻冮崕?Token 闁荤姳璁查崜婵嬪汲閻斿吋鏅璺虹墐閸?     */
    private void recordEvalLog(InterviewSession session, InterviewQuestion question,
                                boolean success, String errorMsg,
                                AiCallResult<EvaluationDecisionOutput> result) {
        AiInvocationLog logEntry = AiInvocationLog.builder()
                .sessionId(session.getId())
                .questionId(question.getId())
                .userId(session.getUserId())
                .promptCode("evaluation_decision")
                .promptVersion(promptProperties.resolveVersion("evaluation_decision"))
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

    /**
     * 婵?generationContextJson 婵炴垶鎼╅崢鍊熴亹娓氣偓瀹?domainCode闂佹寧绋戦悧鍡涘储鐟欏嫭鍎熼柡鍥╁Л閺佸嫰鎮楀☉娆樻畼妞ゆ垳鐒︾粙澶屾嫚瑜忕粈鍡涙煏?     */
    private String resolveDomainCodeFromGenCtx(InterviewQuestion q) {
        if (q.getGenerationContextJson() == null) return "";
        Object code = q.getGenerationContextJson().get("domainCode");
        return code instanceof String s ? s : "";
    }

    /**
     * 婵炲濮撮柊锝夈€傛禒瀣剮妞ゆ棁鍋愰弶钘壝归敐鍡樺磩鐞氭瑩鏌＄€ｎ偄濮夐柣鎿勭磿閹风娀宕卞Δ鍐ㄥ箥缂傚倸鍊归悧婊堟偉濠婂牊鏅悘鐐跺亹閸犳﹢鏌涜箛鎾跺缂?generationContextJson 婵炴垶鎼╅崢鑹般亹閸ヮ剚鏅悘鐐舵楠炴垿骞?"intro"闂?     */
    private String resolveDomainCode(InterviewQuestion q, InterviewSession session) {
        String fromCtx = resolveDomainCodeFromGenCtx(q);
        if (!fromCtx.isBlank()) return fromCtx;
        return "intro";
    }

    /**
     * 婵炲濮村锔藉緞閸曨垰瀚?domain_states 闂佸搫琚崕鍙夌珶濡￥浜归柟鎯у暱椤ゅ懎螞閻楀牜鐒芥繛韫嵆楠炲秹鍩€椤掑倷娌柣鎰皺閸樼敻鏌ｉ妸銉ヮ仹闁煎灚鍨垮顒勫炊瑜庨崐鎶芥煏?     */
    private String resolveDomainName(InterviewQuestion q, InterviewSession session) {
        String domainCode = resolveDomainCode(q, session);
        if (session.getSyllabusJson() == null) return domainCode;

        Object domainsObj = session.getSyllabusJson().get("domains");
        if (!(domainsObj instanceof List<?> domains)) return domainCode;

        for (Object d : domains) {
            if (!(d instanceof Map<?, ?> dm)) continue;
            if (domainCode.equals(dm.get("domainCode"))) {
                Object name = dm.get("domainName");
                if (name instanceof String s) return s;
            }
        }
        return domainCode;
    }
}
