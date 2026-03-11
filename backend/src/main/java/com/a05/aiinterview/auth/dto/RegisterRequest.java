package com.a05.aiinterview.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 邮箱验证码（发送注册验证码后填写） */
    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 8, message = "验证码为4-8位")
    private String code;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64)
    private String nickname;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码至少6位")
    private String password;
}
