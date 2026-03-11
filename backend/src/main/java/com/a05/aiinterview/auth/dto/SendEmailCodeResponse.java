package com.a05.aiinterview.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送邮箱验证码接口的响应。
 * 开发环境（未配置 SEND_EMAIL）时返回 devCode，便于前端直接展示，无需查收邮件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendEmailCodeResponse {

    /** 开发环境返回的验证码，生产环境为 null */
    private String devCode;
}
