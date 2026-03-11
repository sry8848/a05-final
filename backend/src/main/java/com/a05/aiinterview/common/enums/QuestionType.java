package com.a05.aiinterview.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.Getter;

/**
 * 题目类型枚举。
 * 对应考纲中的 question_mix_plan 配额字段。
 */
@Getter
public enum QuestionType implements IEnum<String> {

    INTRO("自我介绍"),
    PROJECT_DEEP_DIVE("项目深挖"),
    SCENARIO("场景题"),
    PRINCIPLE("原理题"),
    BEHAVIORAL("行为题");

    private final String desc;

    QuestionType(String desc) {
        this.desc = desc;
    }

    @Override
    public String getValue() {
        return this.name();
    }
}
