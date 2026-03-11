package com.a05.aiinterview.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.Getter;

/**
 * 知识域考察状态枚举，统一账本 JSON 与 session_skill_states 表的状态语义。
 *
 * <pre>
 * UNASKED        → 尚未考察（初始状态，账本写 UNASKED，表写 uncovered）
 * IN_PROGRESS    → 考察中（已问过该域但未饱和，账本写 IN_PROGRESS，表写 in_progress）
 * COVERED        → 已覆盖（问透或达到目标深度，账本写 COVERED，表写 covered）
 * CIRCUIT_BROKEN → 熔断跳过（连续失败/策略跳过，账本写 CIRCUIT_BROKEN，表写 circuit_broken）
 * </pre>
 *
 * <p>账本侧使用枚举 {@code name()} 写入（大写），关系表侧使用 {@code getSkillStateValue()} 写入（小写下划线）。
 */
@Getter
public enum DomainStatus implements IEnum<String> {

    UNASKED("uncovered", "尚未考察"),
    IN_PROGRESS("in_progress", "考察中"),
    COVERED("covered", "已覆盖"),
    CIRCUIT_BROKEN("circuit_broken", "熔断跳过");

    /** 对应 session_skill_states.status 列的取值（小写下划线风格） */
    private final String skillStateValue;

    /** 中文描述，用于日志与展示 */
    private final String desc;

    DomainStatus(String skillStateValue, String desc) {
        this.skillStateValue = skillStateValue;
        this.desc = desc;
    }

    /**
     * IEnum 接口实现，返回账本侧存储值（枚举名，大写）。
     * 序列化到 JSON 时使用此值，确保账本中始终是 UNASKED/IN_PROGRESS/COVERED/CIRCUIT_BROKEN。
     */
    @Override
    public String getValue() {
        return this.name();
    }

    /**
     * 将账本字符串状态（大小写均支持）安全解析为枚举。
     * 无法识别时返回 {@code IN_PROGRESS} 作为兜底值，并可由调用方打 warning 日志。
     *
     * @param value 账本中的状态字符串，如 "UNASKED" / "covered" / null
     * @return 对应枚举，无法识别时返回 IN_PROGRESS
     */
    public static DomainStatus fromLedgerValue(String value) {
        if (value == null) {
            return IN_PROGRESS;
        }
        for (DomainStatus ds : values()) {
            if (ds.name().equalsIgnoreCase(value)) {
                return ds;
            }
        }
        return IN_PROGRESS;
    }

    /**
     * 将 session_skill_states.status 列的小写值解析为枚举。
     * 无法识别时返回 {@code IN_PROGRESS}。
     *
     * @param skillStateValue 关系表中的状态值，如 "uncovered" / "in_progress"
     * @return 对应枚举，无法识别时返回 IN_PROGRESS
     */
    public static DomainStatus fromSkillStateValue(String skillStateValue) {
        if (skillStateValue == null) {
            return IN_PROGRESS;
        }
        for (DomainStatus ds : values()) {
            if (ds.skillStateValue.equalsIgnoreCase(skillStateValue)) {
                return ds;
            }
        }
        return IN_PROGRESS;
    }
}
