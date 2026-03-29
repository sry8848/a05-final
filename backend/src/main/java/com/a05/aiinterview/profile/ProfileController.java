package com.a05.aiinterview.profile;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.profile.dto.ProfileDto;
import com.a05.aiinterview.profile.dto.ProfileStatisticsDto;
import com.a05.aiinterview.profile.dto.SkillOverviewDto;
import com.a05.aiinterview.profile.dto.UpdateProfileRequest;
import com.a05.aiinterview.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Profile")
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "获取当前用户资料")
    @GetMapping
    public ApiResponse<ProfileDto> getProfile(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(profileService.getProfile(userId));
    }

    @Operation(summary = "更新当前用户资料")
    @PutMapping
    public ApiResponse<ProfileDto> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(profileService.updateProfile(userId, request));
    }

    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    public ApiResponse<ProfileDto> uploadAvatar(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(profileService.updateAvatar(userId, file));
    }

    @Operation(summary = "获取成长统计")
    @GetMapping("/statistics")
    public ApiResponse<ProfileStatisticsDto> statistics(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "positionCode", required = false) String positionCode) {
        return ApiResponse.ok(profileService.getStatistics(userId, positionCode));
    }

    @Operation(summary = "获取知识域成长概览")
    @GetMapping("/skill-overview")
    public ApiResponse<SkillOverviewDto> skillOverview(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "positionCode", required = false) String positionCode) {
        return ApiResponse.ok(profileService.getSkillOverview(userId, positionCode));
    }

    @Operation(summary = "读取头像文件")
    @GetMapping("/avatar/{uid}/{fileName}")
    public ResponseEntity<Resource> avatarFile(
            @AuthenticationPrincipal Long userId,
            @PathVariable("uid") Long uid,
            @PathVariable("fileName") String fileName) {
        if (!uid.equals(userId)) {
            throw new IllegalArgumentException("无权访问该头像文件");
        }
        Resource resource = profileService.loadAvatar(uid, fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(resource);
    }
}
