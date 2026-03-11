package com.a05.aiinterview.resume;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.resume.dto.*;
import com.a05.aiinterview.resume.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历管理接口：列表、上传、解析状态、详情、更新、设默认、删除。
 * 多租户：所有接口需登录，仅操作当前用户自己的简历。
 */
@Slf4j
@RestController
@RequestMapping("/resumes")
@RequiredArgsConstructor
@Tag(name = "简历管理")
public class ResumeController {

    private final ResumeService resumeService;

    private static Long requireUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            throw new IllegalStateException("未登录");
        }
        return (Long) auth.getPrincipal();
    }

    @GetMapping
    @Operation(summary = "获取简历列表")
    public ApiResponse<List<ResumeListItemDto>> list(Authentication authentication) {
        Long userId = requireUserId(authentication);
        List<ResumeListItemDto> data = resumeService.list(userId);
        return ApiResponse.ok(data);
    }

    @PostMapping("/upload")
    @Operation(summary = "上传简历并发起解析")
    public ApiResponse<ResumeUploadResponseDto> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        Long userId = requireUserId(authentication);
        ResumeUploadResponseDto data = resumeService.upload(userId, file);
        log.info("简历上传接口完成, userId={}, resumeId={}", userId, data.getResumeId());
        return ApiResponse.ok(data);
    }

    @GetMapping("/{resumeId}/parse-status")
    @Operation(summary = "查询简历解析状态")
    public ApiResponse<ResumeParseStatusDto> getParseStatus(
            Authentication authentication,
            @PathVariable("resumeId") Long resumeId) {
        Long userId = requireUserId(authentication);
        ResumeParseStatusDto data = resumeService.getParseStatus(userId, resumeId);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{resumeId}")
    @Operation(summary = "获取简历详情")
    public ApiResponse<ResumeDetailDto> getDetail(
            Authentication authentication,
            @PathVariable("resumeId") Long resumeId) {
        Long userId = requireUserId(authentication);
        ResumeDetailDto data = resumeService.getDetail(userId, resumeId);
        return ApiResponse.ok(data);
    }

    @PutMapping("/{resumeId}")
    @Operation(summary = "更新简历（名称、识别文本、是否默认）")
    public ApiResponse<Void> update(
            Authentication authentication,
            @PathVariable("resumeId") Long resumeId,
            @RequestBody ResumeUpdateRequestDto dto) {
        Long userId = requireUserId(authentication);
        resumeService.update(userId, resumeId, dto);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{resumeId}/set-default")
    @Operation(summary = "设为默认简历")
    public ApiResponse<Void> setDefault(
            Authentication authentication,
            @PathVariable("resumeId") Long resumeId) {
        Long userId = requireUserId(authentication);
        resumeService.setDefault(userId, resumeId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{resumeId}")
    @Operation(summary = "删除简历")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable("resumeId") Long resumeId) {
        Long userId = requireUserId(authentication);
        resumeService.delete(userId, resumeId);
        return ApiResponse.ok(null);
    }
}
