package com.a05.aiinterview.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.Getter;

/**
 * 面试题目深度等级枚举，也是 difficulty 的唯一真源。
 * 用于描述题卡/题目本身的认知深度，不用于表示候选人资历层级。
 */
@Getter
public enum DepthLevel implements IEnum<String> {

    L1("定义和基础概念"),
    L2("原理和常见用途"),
    L3("结合场景分析或实现"),
    L4("取舍、排障、优化、深度原理"),
    L5("复杂系统设计或架构判断");

    private final String desc;

    DepthLevel(String desc) {
        this.desc = desc;
    }

    @Override
    public String getValue() {
        return this.name();
    }
}
