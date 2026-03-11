package com.a05.aiinterview.system;

import com.a05.aiinterview.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 系统接口：Ping、环境检测扩展位。
 */
@RestController
@RequestMapping("/system")
public class SystemController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("serverTime", Instant.now().toString()));
    }
}
