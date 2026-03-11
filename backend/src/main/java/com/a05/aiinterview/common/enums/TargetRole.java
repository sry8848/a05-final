package com.a05.aiinterview.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.Getter;

/**
 * 目标岗位枚举。
 * 入库值为枚举 name()，展示名称用 desc 字段。
 */
@Getter
public enum TargetRole implements IEnum<String> {

    JAVA_BACKEND("Java 后端开发"),
    GO_BACKEND("Go 后端开发"),
    DATA_ENGINEER("数据工程师"),
    FRONTEND("前端开发"),
    QA("测试工程师"),
    DEVOPS("DevOps 工程师");

    private final String desc;

    TargetRole(String desc) {
        this.desc = desc;
    }

    /** 入库时使用枚举 name()，与 API 传值保持一致 */
    @Override
    public String getValue() {
        return this.name();
    }
}
