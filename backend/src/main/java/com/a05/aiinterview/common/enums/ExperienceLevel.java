package com.a05.aiinterview.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.Getter;

/**
 * 工作年限分层枚举。
 * 决定 AI 出题的难度基准和知识域目标深度。
 */
@Getter
public enum ExperienceLevel implements IEnum<String> {

    INTERN("实习生"),
    FRESH_GRAD("应届生"),
    JUNIOR("初级（1~3 年）"),
    MIDDLE("中级（3~5 年）"),
    SENIOR("高级（5~8 年）"),
    STAFF("资深/专家（8 年以上）");

    private final String desc;

    ExperienceLevel(String desc) {
        this.desc = desc;
    }

    @Override
    public String getValue() {
        return this.name();
    }
}
