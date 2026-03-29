package com.a05.aiinterview.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardModelStatusDto {

    private String modelProvider;
    private String modelName;
    private Long requestCount;
    private Long successCount;
    private Long errorCount;
    private Double successRate;
    private Long avgLatencyMs;
    private Long p95LatencyMs;
    private String status;
}
