package com.a05.aiinterview.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.Getter;

/**
 * 面试模式枚举。
 * practice：练习模式，支持文字或语音作答，返回技术分。
 * professional：专业模式，仅语音作答，额外返回综合能力维度评分。
 */
@Getter
public enum InterviewMode implements IEnum<String> {

    practice("练习模式"),
    professional("专业模式");

    private final String desc;

    InterviewMode(String desc) {
        this.desc = desc;
    }

    @Override
    public String getValue() {
        return this.name();
    }
}
