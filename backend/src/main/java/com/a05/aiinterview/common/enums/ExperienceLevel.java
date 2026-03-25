package com.a05.aiinterview.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.Getter;

import java.util.Locale;

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
    SENIOR("高级（5 年以上）");

    private final String desc;

    ExperienceLevel(String desc) {
        this.desc = desc;
    }

    @Override
    public String getValue() {
        return this.name();
    }

    /**
     * 历史库里可能残留 STAFF；读取持久化实体时统一折叠到 SENIOR。
     * 新请求仍然必须通过 valueOf 校验，不能再写入 STAFF。
     */
    public static String normalizeStoredValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "STAFF".equals(normalized) ? SENIOR.name() : normalized;
    }
}
