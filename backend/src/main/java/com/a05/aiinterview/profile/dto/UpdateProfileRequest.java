package com.a05.aiinterview.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新个人资料请求")
public class UpdateProfileRequest {

    @Size(max = 64, message = "nickname 最长 64 个字符")
    @Schema(description = "昵称", example = "alice")
    private String nickname;

    @Email(message = "email 格式不正确")
    @Size(max = 128, message = "email 最长 128 个字符")
    @Schema(description = "邮箱", example = "user@example.com")
    private String email;
}
