package com.a05.aiinterview.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.Getter;

/**
 * 知识域考察深度等级枚举。
 * L1：基础概念认知；L5：架构级深度设计。
 * 用于 Planner 设定目标深度，以及状态账本记录当前深度。
 */
@Getter
public enum DepthLevel implements IEnum<String> {

    L1("基础认知"),
    L2("理解应用"),
    L3("熟练掌握"),
    L4("深度原理"),
    L5("架构设计");

    private final String desc;

    DepthLevel(String desc) {
        this.desc = desc;
    }

    @Override
    public String getValue() {
        return this.name();
    }
}
