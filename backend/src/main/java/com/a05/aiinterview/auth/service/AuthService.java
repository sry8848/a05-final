package com.a05.aiinterview.auth.service;

import com.a05.aiinterview.auth.dto.*;
import com.a05.aiinterview.auth.entity.User;
import com.a05.aiinterview.auth.mapper.UserMapper;
import com.a05.aiinterview.auth.config.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRE_SECONDS = 300;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final VerificationCodeStore verificationCodeStore;

    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        String code = req.getCode().trim();

        // 1. 校验注册验证码
        boolean codeOk = verificationCodeStore.verifyAndInvalidate("register", email, code);
        if (!codeOk) {
            log.warn("注册验证码错误或已过期, email={}", email);
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        // 2. 唯一性校验（防止重复提交）
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
        q.eq(User::getEmail, email);
        if (userMapper.selectCount(q) > 0) {
            log.warn("注册时邮箱已存在, email={}", email);
            throw new IllegalArgumentException("该邮箱已注册");
        }

        // 3. 创建用户
        User user = new User();
        user.setEmail(email);
        user.setNickname(req.getNickname().trim());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("用户注册成功, email={}, userId={}", email, user.getId());
    }

    public LoginData loginByPassword(LoginPasswordRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = getByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        if (!"active".equals(user.getStatus())) {
            throw new IllegalArgumentException("账户状态异常");
        }
        return buildLoginData(user);
    }

    public SendEmailCodeResponse sendEmailCode(SendEmailCodeRequest req) {
        String scene = req.getScene().trim().toLowerCase();
        if (!"login".equals(scene) && !"register".equals(scene)) {
            throw new IllegalArgumentException("场景参数无效");
        }
        String email = req.getEmail().trim().toLowerCase();

        // 注册场景：唯一性校验，仅未注册邮箱可收注册验证码
        if ("register".equals(scene)) {
            LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
            q.eq(User::getEmail, email);
            if (userMapper.selectCount(q) > 0) {
                log.warn("发送注册验证码时邮箱已存在, email={}", email);
                throw new IllegalArgumentException("该邮箱已注册");
            }
        }

        String code = generateNumericCode(CODE_LENGTH);
        verificationCodeStore.save(scene, email, code, CODE_EXPIRE_SECONDS);
        // 开发环境：不发真实邮件，将验证码放入响应供前端展示；生产配置 SEND_EMAIL 后不发 devCode
        if (System.getenv("SEND_EMAIL") == null) {
            log.info("[DEV] 邮箱验证码 email={}, code={} ({}分钟内有效)", email, code, CODE_EXPIRE_SECONDS / 60);
            return new SendEmailCodeResponse(code);
        }
        return new SendEmailCodeResponse(null);
    }

    public LoginData loginByEmailCode(LoginEmailCodeRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        boolean ok = verificationCodeStore.verifyAndInvalidate("login", email, req.getCode().trim());
        if (!ok) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        User user = getByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("该邮箱尚未注册，请先注册");
        }
        if (!"active".equals(user.getStatus())) {
            throw new IllegalArgumentException("账户状态异常");
        }
        return buildLoginData(user);
    }

    public UserInfoDto getMe(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return new UserInfoDto(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname()
        );
    }

    public void logout(String token) {
        // 无状态 JWT：客户端丢弃 token 即可；若需服务端黑名单可在此将 token 加入 Redis
    }

    private User getByEmail(String email) {
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
        q.eq(User::getEmail, email);
        return userMapper.selectOne(q);
    }

    private LoginData buildLoginData(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new LoginData(token, String.valueOf(user.getId()), user.getNickname());
    }

    private static String generateNumericCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < len; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.toString();
    }
}
