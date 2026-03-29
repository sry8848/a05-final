package com.a05.aiinterview.ai.contract;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评估决策策略编码。
 */
public enum StrategyCode {
    S_P_VERIFY("S_P_VERIFY"),
    S_P_DEEP_LINK("S_P_DEEP_LINK"),
    S_P_VARIANT("S_P_VARIANT"),
    S_P_SAME_DOMAIN_SHIFT("S_P_SAME_DOMAIN_SHIFT"),
    S_SWITCH_DOMAIN("S_SWITCH_DOMAIN"),

    S_J_RECONSTRUCT("S_J_RECONSTRUCT"),
    S_J_RESPONSIBILITY("S_J_RESPONSIBILITY"),
    S_J_PRESSURE("S_J_PRESSURE"),
    S_J_TRADEOFF("S_J_TRADEOFF"),
    S_J_GUARDRAILS("S_J_GUARDRAILS"),
    S_J_EVOLUTION("S_J_EVOLUTION"),
    S_J_SWITCH_POINT("S_J_SWITCH_POINT"),
    S_J_SWITCH_PROJECT("S_J_SWITCH_PROJECT"),

    S_S_FOLLOW_DIAGNOSE("S_S_FOLLOW_DIAGNOSE"),
    S_S_FOLLOW_RESPONSE("S_S_FOLLOW_RESPONSE"),
    S_S_FOLLOW_TRADEOFF("S_S_FOLLOW_TRADEOFF"),
    S_S_FOLLOW_GUARDRAILS("S_S_FOLLOW_GUARDRAILS"),
    S_S_NEW_DIAGNOSE("S_S_NEW_DIAGNOSE"),
    S_S_NEW_RESPONSE("S_S_NEW_RESPONSE"),
    S_S_NEW_TRADEOFF("S_S_NEW_TRADEOFF"),
    S_S_NEW_GUARDRAILS("S_S_NEW_GUARDRAILS"),

    S_B_FOLLOW_DECISION("S_B_FOLLOW_DECISION"),
    S_B_FOLLOW_REFLECTION("S_B_FOLLOW_REFLECTION"),
    S_B_FOLLOW_CONFLICT("S_B_FOLLOW_CONFLICT"),
    S_B_FOLLOW_TRANSFER("S_B_FOLLOW_TRANSFER"),
    S_B_NEW_DECISION("S_B_NEW_DECISION"),
    S_B_NEW_REFLECTION("S_B_NEW_REFLECTION"),
    S_B_NEW_CONFLICT("S_B_NEW_CONFLICT"),
    S_B_NEW_TRANSFER("S_B_NEW_TRANSFER"),

    S_ENTER_PRINCIPLE("S_ENTER_PRINCIPLE"),
    S_ENTER_PROJECT("S_ENTER_PROJECT"),
    S_ENTER_SCENARIO("S_ENTER_SCENARIO"),
    S_ENTER_BEHAVIORAL("S_ENTER_BEHAVIORAL"),

    S_WRAPUP("S_WRAPUP");

    private static final Map<String, StrategyCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(StrategyCode::code, Function.identity()));

    private final String code;

    StrategyCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static StrategyCode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return BY_CODE.get(code.trim().toUpperCase(Locale.ROOT));
    }
}
