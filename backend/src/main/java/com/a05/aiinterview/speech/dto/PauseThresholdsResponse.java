package com.a05.aiinterview.speech.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 各题型停顿判定阈值响应。
 *
 * <p>前端启动时拉取并本地缓存，根据当前题目的 {@code questionType} 取对应阈值，
 * 超过阈值的静音段将在 answerText 中插入 {@code [停顿 Xs]} 标签。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "各题型停顿判定阈值（毫秒）")
public class PauseThresholdsResponse {

    @Schema(description = "questionType → 阈值（毫秒）映射",
            example = "{\"INTRO\":2000,\"PRINCIPLE\":2500,\"SCENARIO\":3000,\"PROJECT_DEEP_DIVE\":2000,\"BEHAVIORAL\":2500}")
    private Map<String, Integer> thresholds;
}
