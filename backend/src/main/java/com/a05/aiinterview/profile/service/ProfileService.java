package com.a05.aiinterview.profile.service;

import com.a05.aiinterview.auth.entity.User;
import com.a05.aiinterview.auth.mapper.UserMapper;
import com.a05.aiinterview.common.dto.RadarDimensionScoreDto;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.service.InterviewSessionStatusService;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.a05.aiinterview.position.service.PositionService;
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
    private static final BigDecimal BASELINE_SCORE = BigDecimal.valueOf(50);
    private static final List<String> NO_EVIDENCE_MARKERS = List.of(
            "未被提问", "无作答证据", "未作答", "未回答", "未考察", "未覆盖该知识域");
    private static final List<String> SECONDARY_NO_EVIDENCE_MARKERS = List.of(
            "无法评估", "不可评估", "深度不可评估");
    private static final List<String> NEGATIVE_MARKERS = List.of(
            "缺", "不足", "问题", "偏", "不清", "不够", "未", "遗漏", "薄弱", "混乱", "跳跃",
            "模糊", "欠缺", "忽略", "没有", "不稳", "风险", "欠佳");

    private final UserMapper userMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final PositionService positionService;
    private final ProfileAvatarStorageConfig avatarStorageConfig;
    private final InterviewSessionStatusService interviewSessionStatusService;

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
        List<InterviewSession> sessions = loadFinishedSessions(userId, positionCode);

        ProfileStatisticsDto dto = new ProfileStatisticsDto();
        dto.setTotalSessions(sessions.size());
        dto.setTotalMinutes(sumMinutes(sessions));

        List<InterviewReport> reports = loadReportsBySessions(sessions);
        dto.setAverageScore(avgScore(reports));
        dto.setScoreTrend(buildScoreTrend(sessions, reports));
        RadarAggregate radarAggregate = buildProfessionalRadarAggregate(sessions, reports);
        dto.setProfessionalRadarScores(radarAggregate.scores());
        dto.setProfessionalSampleCount(radarAggregate.sampleCount());
        return dto;
    }

    public SkillOverviewDto getSkillOverview(Long userId, String positionCode) {
        List<InterviewSession> sessions = loadFinishedSessions(userId, positionCode);
        List<InterviewReport> reports = loadReportsBySessions(sessions);
        List<ReportedSession> recentReportedSessions = collectRecentReportedSessions(sessions, reports, 8, false);
        Map<String, PositionSkillDomain> allowedDomains = loadAllowedDomains(positionCode);
        Map<String, DomainAggregate> aggregateMap = buildDomainAggregates(recentReportedSessions, allowedDomains);
        List<SkillDomainItemDto> domains = buildSkillDomainItems(aggregateMap, allowedDomains);

        SkillOverviewDto dto = new SkillOverviewDto();
        dto.setPositionCode(normalizeTargetRole(positionCode));
        dto.setDomains(domains);
        dto.setTopStrengths(buildTopStrengths(domains));
        dto.setTopWeaknesses(buildTopWeaknesses(domains));
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

    private List<InterviewSession> loadFinishedSessions(Long userId, String positionCode) {
        return loadSessions(userId, positionCode).stream()
                .filter(this::shouldCountForGrowthStatistics)
                .toList();
    }

    private boolean shouldCountForGrowthStatistics(InterviewSession session) {
        if (session == null) {
            return false;
        }
        String effectiveStatus = resolveEffectiveStatus(session);
        if (session.getFinishedAt() != null) {
            return true;
        }
        return "completed".equalsIgnoreCase(effectiveStatus)
                || "report_generating".equalsIgnoreCase(effectiveStatus);
    }

    private String resolveEffectiveStatus(InterviewSession session) {
        String resolved = interviewSessionStatusService.resolveAndSync(session);
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        return session.getStatus();
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

    private RadarAggregate buildProfessionalRadarAggregate(
            List<InterviewSession> sessions, List<InterviewReport> reports) {
        List<ReportedSession> professionalSessions = collectRecentReportedSessions(sessions, reports, 8, true);
        if (professionalSessions.isEmpty()) {
            return new RadarAggregate(List.of(), 0);
        }

        class Aggregate {
            BigDecimal sum = BigDecimal.ZERO;
            long count = 0;
            String name;
        }

        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (ReportedSession reported : professionalSessions) {
            List<RadarDimensionScoreDto> dimensions = extractRadarDimensions(reported.report());
            if (dimensions.isEmpty()) {
                continue;
            }
            for (RadarDimensionScoreDto dimension : dimensions) {
                if (dimension.getScore() == null || !StringUtils.hasText(dimension.getDimensionKey())) {
                    continue;
                }
                Aggregate aggregate = aggregates.computeIfAbsent(dimension.getDimensionKey(), key -> new Aggregate());
                aggregate.sum = aggregate.sum.add(dimension.getScore());
                aggregate.count += 1;
                aggregate.name = dimension.getDimensionName();
            }
        }

        List<RadarDimensionScoreDto> scores = aggregates.entrySet().stream()
                .map(entry -> RadarDimensionScoreDto.builder()
                        .dimensionKey(entry.getKey())
                        .dimensionName(entry.getValue().name)
                        .score(entry.getValue().sum.divide(
                                BigDecimal.valueOf(entry.getValue().count), 1, RoundingMode.HALF_UP))
                        .build())
                .toList();
        return new RadarAggregate(scores, professionalSessions.size());
    }

    private List<ReportedSession> collectRecentReportedSessions(
            List<InterviewSession> sessions,
            List<InterviewReport> reports,
            int limit,
            boolean professionalOnly) {
        if (sessions.isEmpty() || reports.isEmpty()) {
            return List.of();
        }

        Map<Long, InterviewReport> reportMap = new HashMap<>();
        for (InterviewReport report : reports) {
            reportMap.put(report.getSessionId(), report);
        }

        return sessions.stream()
                .sorted(Comparator.comparing(InterviewSession::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(session -> {
                    InterviewReport report = reportMap.get(session.getId());
                    if (report == null) {
                        return null;
                    }
                    if (professionalOnly && !"professional".equalsIgnoreCase(session.getMode())) {
                        return null;
                    }
                    return new ReportedSession(session, report);
                })
                .filter(java.util.Objects::nonNull)
                .limit(limit)
                .sorted(Comparator.comparing(reported ->
                        reported.session().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Map<String, PositionSkillDomain> loadAllowedDomains(String positionCode) {
        String role = normalizeTargetRole(positionCode);
        if (!StringUtils.hasText(role)) {
            return Map.of();
        }
        List<PositionSkillDomain> entities = positionService.listSkillDomainEntities(role);
        if (entities == null || entities.isEmpty()) {
            return Map.of();
        }
        Map<String, PositionSkillDomain> map = new LinkedHashMap<>();
        for (PositionSkillDomain entity : entities) {
            if (entity == null || !StringUtils.hasText(entity.getDomainCode())) {
                continue;
            }
            map.put(entity.getDomainCode(), entity);
        }
        return map;
    }

    private Map<String, DomainAggregate> buildDomainAggregates(
            List<ReportedSession> recentReportedSessions,
            Map<String, PositionSkillDomain> allowedDomains) {
        Map<String, DomainAggregate> aggregates = new LinkedHashMap<>();
        for (PositionSkillDomain allowedDomain : allowedDomains.values()) {
            DomainAggregate aggregate = new DomainAggregate();
            aggregate.domainCode = allowedDomain.getDomainCode();
            aggregate.domainName = allowedDomain.getDomainName();
            aggregates.put(allowedDomain.getDomainCode(), aggregate);
        }

        for (ReportedSession reported : recentReportedSessions) {
            InterviewSession session = reported.session();
            InterviewReport report = reported.report();
            if (report.getSkillDomainScores() == null) {
                continue;
            }

            for (Map<String, Object> item : report.getSkillDomainScores()) {
                String domainCode = toStr(item.get("domainCode"));
                if (!StringUtils.hasText(domainCode) || !allowedDomains.containsKey(domainCode)) {
                    continue;
                }
                BigDecimal rawScore = toDecimal(item.get("score"));
                if (rawScore == null) {
                    continue;
                }
                String commentary = toStr(item.get("commentary"));
                if (!hasAssessmentEvidence(rawScore, commentary)) {
                    continue;
                }
                DomainAggregate aggregate = aggregates.computeIfAbsent(domainCode, key -> {
                    DomainAggregate created = new DomainAggregate();
                    created.domainCode = key;
                    created.domainName = key;
                    return created;
                });
                if (StringUtils.hasText(toStr(item.get("domainName")))) {
                    aggregate.domainName = toStr(item.get("domainName"));
                }

                aggregate.sum = aggregate.sum.add(rawScore);
                aggregate.appearanceCount += 1;
                aggregate.lastTestedAt = session.getCreatedAt();
                aggregate.recentScores.add(new ScoreTrendPointDto(
                        session.getCreatedAt() != null ? session.getCreatedAt().toLocalDate().format(DATE_FMT) : "",
                        rawScore));
                if (aggregate.previousScore != null) {
                    aggregate.deltaSum = aggregate.deltaSum.add(rawScore.subtract(aggregate.previousScore));
                }
                aggregate.previousScore = rawScore;

                if (StringUtils.hasText(commentary)) {
                    aggregate.commentaries.add(commentary.trim());
                }
            }
        }
        return aggregates;
    }

    private List<SkillDomainItemDto> buildSkillDomainItems(
            Map<String, DomainAggregate> aggregateMap,
            Map<String, PositionSkillDomain> allowedDomains) {
        List<SkillDomainItemDto> items = new ArrayList<>();
        for (Map.Entry<String, DomainAggregate> entry : aggregateMap.entrySet()) {
            DomainAggregate aggregate = entry.getValue();
            SkillDomainItemDto item = new SkillDomainItemDto();
            item.setDomainCode(entry.getKey());
            item.setDomainName(StringUtils.hasText(aggregate.domainName)
                    ? aggregate.domainName
                    : allowedDomains.getOrDefault(entry.getKey(), new PositionSkillDomain()).getDomainName());
            item.setAppearanceCount(aggregate.appearanceCount);
            item.setSampleCount(aggregate.appearanceCount);
            item.setRecentScores(List.copyOf(aggregate.recentScores));
            item.setLastTestedAt(aggregate.lastTestedAt != null ? aggregate.lastTestedAt.toString() : null);
            item.setRankingEligible(aggregate.appearanceCount >= 3 && allowedDomains.containsKey(entry.getKey()));
            if (aggregate.appearanceCount > 0) {
                item.setAverageScore(aggregate.sum.divide(BigDecimal.valueOf(aggregate.appearanceCount), 1, RoundingMode.HALF_UP));
                item.setScoreDelta(aggregate.deltaSum.setScale(1, RoundingMode.HALF_UP));
                item.setScore(clampToScoreRange(BASELINE_SCORE.add(aggregate.deltaSum)));
                List<String> weaknessPoints = buildWeaknessPoints(aggregate.commentaries);
                item.setWeaknessPoints(weaknessPoints);
                item.setWeaknessSummary(weaknessPoints.isEmpty() ? null : String.join(" · ", weaknessPoints));
            } else {
                item.setAverageScore(null);
                item.setScoreDelta(null);
                item.setScore(null);
                item.setWeaknessPoints(List.of());
                item.setWeaknessSummary(null);
            }
            items.add(item);
        }

        items.sort(Comparator
                .comparing((SkillDomainItemDto item) -> distanceFromBaseline(item.getScore()), Comparator.reverseOrder())
                .thenComparing(SkillDomainItemDto::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SkillDomainItemDto::getDomainCode));
        return items;
    }

    private List<SkillDomainItemDto> buildTopStrengths(List<SkillDomainItemDto> domains) {
        return domains.stream()
                .filter(SkillDomainItemDto::isRankingEligible)
                .filter(item -> item.getScore() != null && item.getScore().compareTo(BASELINE_SCORE) > 0)
                .sorted(Comparator
                        .comparing((SkillDomainItemDto item) -> distanceFromBaseline(item.getScore()), Comparator.reverseOrder())
                        .thenComparing(SkillDomainItemDto::getScore, Comparator.reverseOrder()))
                .limit(3)
                .toList();
    }

    private List<SkillDomainItemDto> buildTopWeaknesses(List<SkillDomainItemDto> domains) {
        return domains.stream()
                .filter(SkillDomainItemDto::isRankingEligible)
                .filter(item -> item.getScore() != null && item.getScore().compareTo(BASELINE_SCORE) < 0)
                .sorted(Comparator
                        .comparing((SkillDomainItemDto item) -> distanceFromBaseline(item.getScore()), Comparator.reverseOrder())
                        .thenComparing(SkillDomainItemDto::getScore))
                .limit(3)
                .toList();
    }

    private List<RadarDimensionScoreDto> extractRadarDimensions(InterviewReport report) {
        if (report.getComprehensiveRadarScores() == null) {
            return List.of();
        }
        Object dimensionsObj = report.getComprehensiveRadarScores().get("dimensions");
        if (!(dimensionsObj instanceof List<?> dimensions)) {
            return List.of();
        }
        List<RadarDimensionScoreDto> scores = new ArrayList<>();
        for (Object item : dimensions) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            BigDecimal score = toDecimal(map.get("score"));
            if (score == null) {
                continue;
            }
            scores.add(RadarDimensionScoreDto.builder()
                    .dimensionKey(toStr(map.get("dimensionKey")))
                    .dimensionName(toStr(map.get("dimensionName")))
                    .score(score)
                    .build());
        }
        return scores;
    }

    private boolean hasAssessmentEvidence(BigDecimal rawScore, String commentary) {
        if (!StringUtils.hasText(commentary)) {
            return true;
        }
        if (containsAny(commentary, NO_EVIDENCE_MARKERS)) {
            return false;
        }
        return !(rawScore != null
                && rawScore.compareTo(BASELINE_SCORE) == 0
                && containsAny(commentary, SECONDARY_NO_EVIDENCE_MARKERS));
    }

    private List<String> buildWeaknessPoints(List<String> commentaries) {
        if (commentaries.isEmpty()) {
            return List.of();
        }
        Map<String, WeaknessPointAggregate> pointMap = new LinkedHashMap<>();
        for (int i = 0; i < commentaries.size(); i++) {
            for (String point : extractWeaknessPoints(commentaries.get(i))) {
                WeaknessPointAggregate aggregate = pointMap.computeIfAbsent(
                        point, key -> new WeaknessPointAggregate(point));
                aggregate.count += 1;
                aggregate.latestIndex = i;
            }
        }
        return pointMap.values().stream()
                .sorted(Comparator
                        .comparingInt((WeaknessPointAggregate item) -> item.count).reversed()
                        .thenComparing(Comparator.comparingInt((WeaknessPointAggregate item) -> item.latestIndex).reversed())
                        .thenComparing(item -> item.text))
                .limit(3)
                .map(item -> item.text)
                .toList();
    }

    private List<String> extractWeaknessPoints(String commentary) {
        if (!StringUtils.hasText(commentary)) {
            return List.of();
        }
        String normalized = commentary.replace('\n', ' ')
                .replace('；', '，')
                .replace('。', '，')
                .replace('、', '，')
                .replace(';', ',');
        String[] rawClauses = normalized.split("[，,]");
        List<String> points = new ArrayList<>();
        for (String rawClause : rawClauses) {
            String point = normalizeWeaknessPoint(rawClause);
            if (!StringUtils.hasText(point) || points.contains(point)) {
                continue;
            }
            points.add(point);
            if (points.size() >= 3) {
                break;
            }
        }
        return points;
    }

    private String normalizeWeaknessPoint(String rawClause) {
        if (!StringUtils.hasText(rawClause)) {
            return null;
        }
        String point = rawClause.trim();
        point = point.replaceAll("^但", "");
        point = point.replaceAll("^仍需", "");
        point = point.replaceAll("^还需", "");
        point = point.replaceAll("^仍然", "仍");
        point = point.replace("仍缺少", "缺少");
        point = point.replace("仍缺", "缺");
        point = point.replaceAll("^整体", "");
        point = point.replaceAll("^在", "");
        point = point.replaceAll("\\s+", "");
        if (!StringUtils.hasText(point)) {
            return null;
        }
        if (containsAny(point, NO_EVIDENCE_MARKERS) || containsAny(point, SECONDARY_NO_EVIDENCE_MARKERS)) {
            return null;
        }
        if (!containsAny(point, NEGATIVE_MARKERS)) {
            return null;
        }
        if (point.length() > 24) {
            point = point.substring(0, 24);
        }
        return point;
    }

    private boolean containsAny(String text, List<String> markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal clampToScoreRange(BigDecimal score) {
        if (score == null) {
            return null;
        }
        return score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal distanceFromBaseline(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        return score.subtract(BASELINE_SCORE).abs();
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

    private record RadarAggregate(List<RadarDimensionScoreDto> scores, long sampleCount) {
    }

    private record ReportedSession(InterviewSession session, InterviewReport report) {
    }

    private static class DomainAggregate {
        private String domainCode;
        private String domainName;
        private BigDecimal sum = BigDecimal.ZERO;
        private BigDecimal deltaSum = BigDecimal.ZERO;
        private BigDecimal previousScore;
        private long appearanceCount;
        private LocalDateTime lastTestedAt;
        private final List<String> commentaries = new ArrayList<>();
        private final List<ScoreTrendPointDto> recentScores = new ArrayList<>();
    }

    private static class WeaknessPointAggregate {
        private final String text;
        private int count;
        private int latestIndex;

        private WeaknessPointAggregate(String text) {
            this.text = text;
        }
    }
}
