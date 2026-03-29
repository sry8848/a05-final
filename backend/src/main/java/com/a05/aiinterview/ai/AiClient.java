package com.a05.aiinterview.ai;

import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.ai.dto.QuestionConsultInput;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationInput;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.ai.dto.ReportGenerationInput;
import com.a05.aiinterview.ai.dto.ReportGenerationOutput;
import reactor.core.publisher.Flux;

/**
 * AI 客户端接口。
 * 定义后端所有大模型调用的统一入口，通过接口隔离便于在 Mock / OpenAI 实现之间切换。
 *
 * <p>调用规范：
 * <ul>
 *   <li>所有调用返回 {@link AiCallResult}，内含结构化输出、Token 计数和延迟（供审计日志填充）</li>
 *   <li>调用失败时抛出 {@link RuntimeException}，由上层 engine 负责 catch 并更新 session 状态</li>
 * </ul>
 */
public interface AiClient {

    /**
     * 调用 Planner，根据岗位、简历、JD 等信息生成本场面试主考纲。
     *
     * @param input Planner 入参
     * @return 包含主考纲和 Token 消耗的结果包装
     */
    AiCallResult<PlannerOutput> callPlanner(PlannerInput input);

    /**
     * 调用题目生成服务（流式版本），返回 Token 字符流，供 SSE 推送给前端。
     * 适合前端"打字机"效果；不包含 Token 计数。
     *
     * @param input 出题入参（含目标知识域、题型、已问题目列表）
     * @return 字符 Token 的响应式流
     */
    Flux<String> callQuestionGenerationStream(QuestionGenerationInput input);

    /**
     * 调用 INTRO 改写服务，将底稿改写为更自然的首题提问话术。
     *
     * @param input 包含候选人上下文、底稿和历史禁用语句
     * @return 改写后的纯文本题干及 Prompt 元数据
     */
    AiCallResult<String> callIntroRewrite(IntroRewriteInput input);

    /**
     * 调用评估决策服务，对候选人回答评估并决策下一题策略。
     *
     * @param input 包含当前题目信息、候选人回答、历史 Q/A 上下文和状态账本
     * @return 包含评估结果、账本 Patch、下一题策略和 Token 消耗的结果包装
     */
    AiCallResult<EvaluationDecisionOutput>  callEvaluationDecision(EvaluationDecisionInput input);

    /**
     * 调用报告生成服务，根据全场 Q/A 记录和状态账本生成结构化评估报告。
     *
     * @param input 包含全部 Q/A 配对、考纲和最终状态账本
     * @return 包含结构化报告输出和 Token 消耗的结果包装
     */
    AiCallResult<ReportGenerationOutput> callReportGeneration(ReportGenerationInput input);

    /**
     * 调用单题详细评估服务，生成题后复盘结构化结果。
     *
     * @param input 单题详细评估入参
     * @return 结构化详细评估结果
     */
    AiCallResult<QuestionDetailEvaluationOutput> callQuestionDetailEvaluation(QuestionDetailEvaluationInput input);

    /**
     * 调用单题追问服务，围绕当前题目生成流式复盘回复。
     *
     * @param input 单题追问上下文
     * @return 文本增量流
     */
    Flux<String> callQuestionConsultStream(QuestionConsultInput input);
}
