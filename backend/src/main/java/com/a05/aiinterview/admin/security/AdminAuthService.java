package com.a05.aiinterview.admin.security;

import com.a05.aiinterview.auth.dto.AdminInfoDto;
import com.a05.aiinterview.auth.dto.AdminLoginData;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminSecurityProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final com.a05.aiinterview.auth.config.JwtUtil jwtUtil;

    public AdminLoginData login(String username, String password) {
        AdminSecurityProperties.Account account = resolveAccount(username);
        if (account == null || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new IllegalArgumentException("管理员账号或密码错误");
        }

        String displayName = StringUtils.hasText(account.getDisplayName()) ? account.getDisplayName().trim() : account.getUsername().trim();
        String token = jwtUtil.generateAdminToken(account.getUsername().trim(), displayName);
        return new AdminLoginData(token, account.getUsername().trim(), displayName);
    }

    public AdminInfoDto getCurrentAdmin(AdminPrincipal principal) {
        return new AdminInfoDto(principal.username(), principal.displayName());
    }

    public void logout(String token) {
        // 无状态 JWT：客户端丢弃 token 即可
    }

    private AdminSecurityProperties.Account resolveAccount(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        String normalizedUsername = username.trim();
        return properties.getAccounts().stream()
                .filter(account -> StringUtils.hasText(account.getUsername()))
                .filter(account -> normalizedUsername.equals(account.getUsername().trim()))
                .findFirst()
                .orElse(null);
    }
}
