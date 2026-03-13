package com.a05.aiinterview.profile.service;

import com.a05.aiinterview.auth.entity.User;
import com.a05.aiinterview.auth.mapper.UserMapper;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.profile.config.ProfileAvatarStorageConfig;
import com.a05.aiinterview.profile.dto.ProfileDto;
import com.a05.aiinterview.profile.dto.ProfileStatisticsDto;
import com.a05.aiinterview.profile.dto.ScoreTrendPointDto;
import com.a05.aiinterview.profile.dto.SkillDomainItemDto;
import com.a05.aiinterview.profile.dto.SkillOverviewDto;
import com.a05.aiinterview.profile.dto.UpdateProfileRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserMapper userMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final ProfileAvatarStorageConfig avatarStorageConfig;

    public ProfileDto getProfile(Long userId) {
        User user = requireUser(userId);
        return toProfileDto(user);
    }

    public ProfileDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);

        if (request.getEmail() != null) {
            String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!email.isEmpty() && !email.equalsIgnoreCase(user.getEmail())) {
                long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email)
                        .ne(User::getId, userId));
                if (count > 0) {
                    throw new IllegalArgumentException("邮箱已被其他用户使用");
                }
                user.setEmail(email);
            }
        }

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname().trim());
        }

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toProfileDto(user);
    }

    public ProfileDto updateAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像文件");
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String ext = resolveImageExt(originalName);
        if (ext == null) {
            throw new IllegalArgumentException("头像仅支持 png/jpg/jpeg/webp 格式");
        }

        Path userDir = avatarStorageConfig.getUploadDirPath().resolve(String.valueOf(userId));
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            throw new IllegalStateException("头像目录创建失败", e);
        }

        String fileName = UUID.randomUUID() + ext;
        Path target = userDir.resolve(fileName);
        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("头像保存失败", e);
        }

        User user = requireUser(userId);
        user.setAvatarUrl(String.format("/api/v1/profile/avatar/%d/%s", userId, fileName));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toProfileDto(user);
    }

    public Resource loadAvatar(Long userId, String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("非法文件名");
        }
        Path path = avatarStorageConfig.getUploadDirPath().resolve(String.valueOf(userId)).resolve(fileName);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("头像文件不存在");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("头像地址非法", e);
        }
    }

    public ProfileStatisticsDto getStatistics(Long userId, String positionCode) {
        List<InterviewSession> sessions = loadSessions(userId, positionCode);

        ProfileStatisticsDto dto = new ProfileStatisticsDto();
        dto.setTotalSessions(sessions.size());
        dto.setTotalMinutes(sumMinutes(sessions));

        List<InterviewReport> reports = loadReportsBySessions(sessions);
        dto.setAverageScore(avgScore(reports));
        dto.setScoreTrend(buildScoreTrend(sessions, reports));
        return dto;
    }

    public SkillOverviewDto getSkillOverview(Long userId, String positionCode) {
        List<InterviewSession> sessions = loadSessions(userId, positionCode);
        List<InterviewReport> reports = loadReportsBySessions(sessions);

        Map<Long, InterviewSession> sessionMap = new HashMap<>();
        for (InterviewSession session : sessions) {
            sessionMap.put(session.getId(), session);
        }

        class Aggregate {
            BigDecimal sum = BigDecimal.ZERO;
            long count = 0;
            LocalDateTime lastTestedAt;
            String domainName;
        }

        Map<String, Aggregate> aggMap = new LinkedHashMap<>();

        for (InterviewReport report : reports) {
            if (report.getSkillDomainScores() == null) {
                continue;
            }
            InterviewSession session = sessionMap.get(report.getSessionId());
            LocalDateTime testedAt = session != null ? session.getCreatedAt() : report.getCreatedAt();

            for (Map<String, Object> item : report.getSkillDomainScores()) {
                String domainCode = toStr(item.get("domainCode"));
                if (!StringUtils.hasText(domainCode)) {
                    continue;
                }
                BigDecimal score = toDecimal(item.get("score"));
                if (score == null) {
                    continue;
                }

                Aggregate aggregate = aggMap.computeIfAbsent(domainCode, k -> new Aggregate());
                aggregate.sum = aggregate.sum.add(score);
                aggregate.count += 1;
                if (StringUtils.hasText(toStr(item.get("domainName")))) {
                    aggregate.domainName = toStr(item.get("domainName"));
                }
                if (testedAt != null && (aggregate.lastTestedAt == null || testedAt.isAfter(aggregate.lastTestedAt))) {
                    aggregate.lastTestedAt = testedAt;
                }
            }
        }

        List<SkillDomainItemDto> domains = aggMap.entrySet().stream()
                .map(e -> {
                    SkillDomainItemDto item = new SkillDomainItemDto();
                    item.setDomainCode(e.getKey());
                    item.setDomainName(StringUtils.hasText(e.getValue().domainName) ? e.getValue().domainName : e.getKey());
                    if (e.getValue().count > 0) {
                        item.setAverageScore(e.getValue().sum.divide(BigDecimal.valueOf(e.getValue().count), 1, RoundingMode.HALF_UP));
                    } else {
                        item.setAverageScore(null);
                    }
                    item.setSampleCount(e.getValue().count);
                    item.setLastTestedAt(e.getValue().lastTestedAt != null ? e.getValue().lastTestedAt.toString() : null);
                    return item;
                })
                .sorted(Comparator.comparing(SkillDomainItemDto::getAverageScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        SkillOverviewDto dto = new SkillOverviewDto();
        dto.setPositionCode(normalizeTargetRole(positionCode));
        dto.setDomains(domains);
        return dto;
    }

    private List<InterviewSession> loadSessions(Long userId, String positionCode) {
        LambdaQueryWrapper<InterviewSession> wrapper = new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, userId)
                .orderByAsc(InterviewSession::getCreatedAt);

        String role = normalizeTargetRole(positionCode);
        if (StringUtils.hasText(role)) {
            wrapper.eq(InterviewSession::getTargetRole, role);
        }
        return interviewSessionMapper.selectList(wrapper);
    }

    private List<InterviewReport> loadReportsBySessions(List<InterviewSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
        return interviewReportMapper.selectList(new LambdaQueryWrapper<InterviewReport>()
                .in(InterviewReport::getSessionId, sessionIds));
    }

    private long sumMinutes(List<InterviewSession> sessions) {
        long total = 0;
        for (InterviewSession session : sessions) {
            LocalDateTime start = session.getStartedAt();
            LocalDateTime end = session.getFinishedAt();
            if (start == null || end == null || end.isBefore(start)) {
                continue;
            }
            total += Math.max(0, Duration.between(start, end).toMinutes());
        }
        return total;
    }

    private BigDecimal avgScore(List<InterviewReport> reports) {
        List<BigDecimal> values = reports.stream()
                .map(InterviewReport::getOverallScore)
                .filter(v -> v != null)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    private List<ScoreTrendPointDto> buildScoreTrend(List<InterviewSession> sessions, List<InterviewReport> reports) {
        if (sessions.isEmpty() || reports.isEmpty()) {
            return List.of();
        }

        Map<Long, InterviewReport> reportMap = new HashMap<>();
        for (InterviewReport report : reports) {
            reportMap.put(report.getSessionId(), report);
        }

        List<ScoreTrendPointDto> trend = new ArrayList<>();
        for (InterviewSession session : sessions) {
            InterviewReport report = reportMap.get(session.getId());
            if (report == null || report.getOverallScore() == null || session.getCreatedAt() == null) {
                continue;
            }
            LocalDate day = session.getCreatedAt().toLocalDate();
            trend.add(new ScoreTrendPointDto(day.format(DATE_FMT), report.getOverallScore()));
        }
        return trend;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private ProfileDto toProfileDto(User user) {
        ProfileDto dto = new ProfileDto();
        dto.setId(user.getId());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setAvatarUrl(user.getAvatarUrl());
        return dto;
    }

    private String resolveImageExt(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        if (fileName.endsWith(".png")) {
            return ".png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (fileName.endsWith(".webp")) {
            return ".webp";
        }
        return null;
    }

    private BigDecimal toDecimal(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof BigDecimal decimal) {
            return decimal;
        }
        if (val instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toStr(Object val) {
        return val == null ? null : String.valueOf(val);
    }

    private String normalizeTargetRole(String positionCode) {
        if (!StringUtils.hasText(positionCode)) {
            return null;
        }
        String raw = positionCode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (raw) {
            case "JAVA_BACKEND", "GO_BACKEND", "FRONTEND", "DATA_ENGINEER", "QA", "DEVOPS" -> raw;
            case "JAVA" -> "JAVA_BACKEND";
            case "GO" -> "GO_BACKEND";
            default -> raw;
        };
    }
}
