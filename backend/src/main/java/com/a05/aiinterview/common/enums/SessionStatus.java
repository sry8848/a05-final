package com.a05.aiinterview.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.Getter;

/**
 * 面试会话状态枚举。
 * <pre>
 * created           → 会话已创建（预留，当前直接跳 planning）
 * planning          → AI Planner 正在生成考纲
 * in_progress       → 考纲与首题就绪，面试进行中
 * report_generating → 用户结束面试，报告生成中
 * completed         → 报告生成完毕，面试已结束
 * aborted           → 异常终止（AI 调用失败等）
 * </pre>
 */
@Getter
public enum SessionStatus implements IEnum<String> {

    created("已创建"),
    planning("考纲生成中"),
    in_progress("进行中"),
    report_generating("报告生成中"),
    completed("已完成"),
    aborted("已中止");

    private final String desc;

    SessionStatus(String desc) {
        this.desc = desc;
    }

    @Override
    public String getValue() {
        return this.name();
    }
}
