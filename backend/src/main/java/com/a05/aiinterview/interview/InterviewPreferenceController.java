package com.a05.aiinterview.interview;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.interview.dto.InterviewPreferenceDto;
import com.a05.aiinterview.interview.entity.InterviewPreference;
import com.a05.aiinterview.interview.service.InterviewPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 面试偏好接口。
 * 供准备页读取和保存用户上次的填写习惯，实现表单自动回填。
 */
@Tag(name = "面试偏好")
@RestController
@RequestMapping("/interview-preferences")
@RequiredArgsConstructor
public class InterviewPreferenceController {

    private final InterviewPreferenceService preferenceService;

    /**
     * 获取当前用户最近一次面试偏好。
     * 准备页加载时调用，若无历史偏好则返回 data=null。
     *
     * @param userId 当前登录用户 ID
     * @return 偏好信息，或 null（首次使用）
     */
    @Operation(summary = "读取最近一次面试准备偏好")
    @GetMapping("/latest")
    public ApiResponse<InterviewPreferenceDto> getLatest(@AuthenticationPrincipal Long userId) {
        return preferenceService.getLatest(userId)
                .map(pref -> ApiResponse.ok(toDto(pref)))
                .orElse(ApiResponse.ok(null));
    }

    /**
     * 保存或更新当前用户的面试偏好。
     * 每用户只保留一条最新记录（upsert 语义）。
     *
     * @param userId 当前登录用户 ID
     * @param dto    待保存的偏好数据
     * @return 成功响应
     */
    @Operation(summary = "保存面试准备偏好")
    @PutMapping("/latest")
    public ApiResponse<Void> saveLatest(
            @AuthenticationPrincipal Long userId,
            @RequestBody InterviewPreferenceDto dto) {
        preferenceService.saveOrUpdate(userId, toEntity(dto));
        return ApiResponse.ok(null);
    }

    private InterviewPreferenceDto toDto(InterviewPreference pref) {
        InterviewPreferenceDto dto = new InterviewPreferenceDto();
        dto.setPositionCode(pref.getPositionCode());
        dto.setExperienceLevel(pref.getExperienceLevel());
        dto.setMode(pref.getMode());
        dto.setFocusTopics(pref.getFocusTopics());
        dto.setThinkTimeLimitSeconds(pref.getThinkTimeLimitSeconds());
        dto.setAnswerTimeLimitSeconds(pref.getAnswerTimeLimitSeconds());
        return dto;
    }

    private InterviewPreference toEntity(InterviewPreferenceDto dto) {
        InterviewPreference pref = new InterviewPreference();
        pref.setPositionCode(dto.getPositionCode());
        pref.setExperienceLevel(dto.getExperienceLevel());
        pref.setMode(dto.getMode());
        pref.setFocusTopics(dto.getFocusTopics());
        pref.setThinkTimeLimitSeconds(dto.getThinkTimeLimitSeconds());
        pref.setAnswerTimeLimitSeconds(dto.getAnswerTimeLimitSeconds());
        return pref;
    }
}
