package com.a05.aiinterview.admin.dashboard;

import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardModelStatusDto;
import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardOverviewDto;
import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardPromptSummaryDto;
import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardTrendsDto;
import com.a05.aiinterview.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理端仪表盘")
@Validated
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "获取仪表盘概览指标")
    @GetMapping("/overview")
    public ApiResponse<AdminDashboardOverviewDto> getOverview() {
        return ApiResponse.ok(adminDashboardService.getOverview());
    }

    @Operation(summary = "获取仪表盘趋势数据")
    @GetMapping("/trends")
    public ApiResponse<AdminDashboardTrendsDto> getTrends(@RequestParam(defaultValue = "7") @Min(1) int days) {
        return ApiResponse.ok(adminDashboardService.getTrends(days));
    }

    @Operation(summary = "获取模型状态摘要")
    @GetMapping("/models")
    public ApiResponse<List<AdminDashboardModelStatusDto>> getModels(
            @RequestParam(defaultValue = "15") @Min(1) int windowMinutes
    ) {
        return ApiResponse.ok(adminDashboardService.getModels(windowMinutes));
    }

    @Operation(summary = "获取 Prompt 摘要列表")
    @GetMapping("/prompts")
    public ApiResponse<List<AdminDashboardPromptSummaryDto>> getPrompts() {
        return ApiResponse.ok(adminDashboardService.getPrompts());
    }
}
