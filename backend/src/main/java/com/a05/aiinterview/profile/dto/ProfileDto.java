package com.a05.aiinterview.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户档案")
public class ProfileDto {

    @Schema(description = "用户 ID", example = "1001")
    private Long id;

    @Schema(description = "昵称", example = "alice")
    private String nickname;

    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "头像 URL", example = "/api/v1/profile/avatar/1001/a.png")
    private String avatarUrl;
}
