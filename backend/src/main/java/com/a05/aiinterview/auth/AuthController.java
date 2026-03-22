package com.a05.aiinterview.auth;

import com.a05.aiinterview.admin.security.AdminAuthService;
import com.a05.aiinterview.admin.security.AdminPrincipal;
import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.auth.dto.*;
import com.a05.aiinterview.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AdminAuthService adminAuthService;

    public AuthController(AuthService authService, AdminAuthService adminAuthService) {
        this.authService = authService;
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/login/password")
    public ApiResponse<LoginData> loginByPassword(@Valid @RequestBody LoginPasswordRequest req) {
        LoginData data = authService.loginByPassword(req);
        return ApiResponse.ok(data);
    }

    @PostMapping("/login/email-code")
    public ApiResponse<LoginData> loginByEmailCode(@Valid @RequestBody LoginEmailCodeRequest req) {
        LoginData data = authService.loginByEmailCode(req);
        return ApiResponse.ok(data);
    }

    @PostMapping("/admin/login")
    public ApiResponse<AdminLoginData> loginAdmin(@Valid @RequestBody AdminLoginRequest req) {
        AdminLoginData data = adminAuthService.login(req.getUsername(), req.getPassword());
        return ApiResponse.ok(data);
    }

    @PostMapping("/email-code/send")
    public ApiResponse<SendEmailCodeResponse> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest req) {
        SendEmailCodeResponse data = authService.sendEmailCode(req);
        return ApiResponse.ok(data);
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoDto> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long)) {
            return ApiResponse.fail(401, "未登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        UserInfoDto user = authService.getMe(userId);
        if (user == null) {
            return ApiResponse.fail(404, "用户不存在");
        }
        return ApiResponse.ok(user);
    }

    @GetMapping("/admin/me")
    public ApiResponse<AdminInfoDto> adminMe(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal adminPrincipal)) {
            return ApiResponse.fail(401, "未登录");
        }
        return ApiResponse.ok(adminAuthService.getCurrentAdmin(adminPrincipal));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        authService.logout(token);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/logout")
    public ApiResponse<Void> adminLogout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        adminAuthService.logout(token);
        return ApiResponse.ok(null);
    }
}
