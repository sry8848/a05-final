package com.a05.aiinterview.position;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.position.dto.PositionDto;
import com.a05.aiinterview.position.dto.SkillDomainDto;
import com.a05.aiinterview.position.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 岗位与知识域接口。
 * 提供岗位列表和指定岗位知识域树查询，无需登录即可访问（公开配置数据）。
 */
@Tag(name = "岗位与知识域")
@RestController
@RequestMapping("/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    /**
     * 获取所有支持的岗位列表。
     *
     * @return 岗位列表，每个岗位包含 positionCode 和 positionName
     */
    @Operation(summary = "获取岗位列表")
    @GetMapping
    public ApiResponse<List<PositionDto>> listPositions() {
        return ApiResponse.ok(positionService.listPositions());
    }

    /**
     * 获取指定岗位下的知识域树。
     * 开始面试页侧重知识点选择时调用此接口。
     *
     * @param positionCode 岗位编码，如 JAVA_BACKEND
     * @return 知识域列表，按 sort_order 升序
     */
    @Operation(summary = "获取岗位知识域树")
    @GetMapping("/{positionCode}/skill-domains")
    public ApiResponse<List<SkillDomainDto>> listSkillDomains(@PathVariable String positionCode) {
        return ApiResponse.ok(positionService.listSkillDomains(positionCode));
    }
}
