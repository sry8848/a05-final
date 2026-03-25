package com.a05.aiinterview.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送邮箱验证码接口的响应。
 * 为兼容前端既有解析逻辑，保留 devCode 字段，但真实邮件发送模式下固定返回 null。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendEmailCodeResponse {

    /** 兼容字段，真实邮件发送模式下固定为 null */
    private String devCode;
}
